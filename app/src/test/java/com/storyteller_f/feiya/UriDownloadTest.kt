package com.storyteller_f.feiya

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UriDownloadTest {
    private class Provider(private val file: File, private val size: Long?, private val minimalMetadata: Boolean) : ContentProvider() {
        var opens = 0
        override fun onCreate() = true
        override fun getType(uri: Uri) = "text/plain"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            if (minimalMetadata) MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_SIZE)).apply {
                addRow(arrayOf<Any?>(size))
            } else MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)).apply {
                addRow(arrayOf<Any?>("text/plain", size, 0L))
            }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            opens++
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private fun download(size: Long?, contents: String = "0123456789", minimalMetadata: Boolean = false, check: suspend io.ktor.server.testing.ApplicationTestBuilder.(Provider) -> Unit) = testApplication {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "download.txt").apply { writeText(contents) }
        val provider = Provider(file, size, minimalMetadata).apply {
            attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "downloads.test" })
        }
        ShadowContentResolver.registerProviderInternal("downloads.test", provider)
        application {
            install(PartialContent)
            install(AutoHeadResponse)
            routing { get("/file") { call.respondUri(context, Uri.parse("content://downloads.test/file")) } }
        }
        check(provider)
    }

    @Test fun unknownSizeDoesNotAdvertiseAnEmptyDownload() = download(null) {
        val response = client.get("/file")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotEquals("0", response.headers[HttpHeaders.ContentLength])
        assertEquals("0123456789", response.bodyAsText())
    }

    @Test fun headDoesNotOpenOrLeakAFileDescriptor() = download(10) { provider ->
        val response = client.head("/file")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("10", response.headers[HttpHeaders.ContentLength])
        assertEquals("", response.bodyAsText())
        assertEquals(0, provider.opens)
    }

    @Test fun rangeAndSubsequentFullDownloadHaveCorrectBytes() = download(10) {
        val range = client.get("/file") { header(HttpHeaders.Range, "bytes=2-5") }
        assertEquals(HttpStatusCode.PartialContent, range.status)
        assertEquals("2345", range.bodyAsText())
        assertEquals("0123456789", client.get("/file").bodyAsText())
    }
    @Test fun genericProviderMayOmitDocumentColumns() = download(null, minimalMetadata = true) {
        val response = client.get("/file")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("0123456789", response.bodyAsText())
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("text/plain"))
    }

    @Test fun genuinelyEmptyFileStillHasZeroLength() = download(0, contents = "") {
        val response = client.get("/file")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("0", response.headers[HttpHeaders.ContentLength])
        assertEquals("", response.bodyAsText())
    }

    @Test fun unsatisfiableRangeDoesNotOpenAStream() = download(10) { provider ->
        val response = client.get("/file") { header(HttpHeaders.Range, "bytes=20-30") }
        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
        assertEquals(0, provider.opens)
    }

}
