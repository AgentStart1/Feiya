package com.storyteller_f.feiya

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.LastModifiedVersion
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID

private data class UriMetadata(val type: ContentType, val size: Long?, val modified: Long?)

suspend fun ApplicationCall.respondUri(context: Context, file: Uri, configure: OutgoingContent.() -> Unit = {}) {
    val resolver = context.contentResolver
    val metadata = withContext(Dispatchers.IO) {
        resolver.query(file, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val mime = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) cursor.getString(mimeIndex) else resolver.getType(file)
            UriMetadata(
                mime?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: ContentType.Application.OctetStream,
                cursor.optionalLong(OpenableColumns.SIZE)?.takeIf { it >= 0 },
                cursor.optionalLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0 },
            )
        }
    }
    if (metadata == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    if (request.local.method == HttpMethod.Head) {
        respond(object : OutgoingContent.NoContent() {
            override val contentType = metadata.type
            override val contentLength = metadata.size
            init {
                if (metadata.modified != null) versions = versions + LastModifiedVersion(metadata.modified)
            }
        }.apply(configure))
        return
    }
    respond(UriFileContent(
        metadata.type, metadata.size, metadata.modified, CoroutineScope(currentCoroutineContext()),
    ) { resolver.openInputStream(file) ?: throw FileNotFoundException("Shared document is unavailable") }.apply(configure))
}

private fun Cursor.optionalLong(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

/** Opens a fresh stream only when the response body is consumed, including each range. */
class UriFileContent(
    override val contentType: ContentType,
    override val contentLength: Long?,
    lastModified: Long?,
    private val scope: CoroutineScope,
    private val openStream: () -> InputStream,
) : OutgoingContent.ReadChannelContent() {
    init {
        if (lastModified != null) versions = versions + LastModifiedVersion(lastModified)
    }

    override fun readFrom(): ByteReadChannel = readStream(0, contentLength)

    override fun readFrom(range: LongRange): ByteReadChannel =
        if (range.isEmpty()) ByteReadChannel.Empty else readStream(range.first, range.last - range.first + 1)

    private fun readStream(start: Long, length: Long?): ByteReadChannel = scope.writer(Dispatchers.IO) {
        openStream().use { input ->
            // Providers may return pipes, so do not require a seekable file descriptor.
            var skip = start
            while (skip > 0) {
                currentCoroutineContext().ensureActive()
                val skipped = input.skip(skip)
                if (skipped > 0) skip -= skipped
                else if (input.read() >= 0) skip--
                else throw EOFException("Shared document ended before the requested range")
            }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = length
            while (remaining == null || remaining > 0) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer, 0, remaining?.coerceAtMost(buffer.size.toLong())?.toInt() ?: buffer.size)
                if (count < 0) {
                    if (remaining != null && remaining > 0) throw EOFException("Shared document was truncated")
                    break
                }
                channel.writeFully(buffer, 0, count)
                remaining = remaining?.minus(count)
            }
        }
    }.channel
}

suspend fun Context.saveFile(extension: String?, uri: Uri) {
    try {
        withContext(Dispatchers.IO) {
            val file = File(filesDir, "saved/file-${UUID.randomUUID()}.$extension")
            val parentFile = file.parentFile!!
            if (!parentFile.exists()) {
                parentFile.mkdirs()
            }
            if (file.createNewFile()) {
                uri.writeToFile(file, this@saveFile)
            } else {
                Log.e("Server", "create file failed ${file.absolutePath}")
            }

        }
    } catch (e: Exception) {
        Log.e("Server", "saveFile: ", e)
    }

}

fun Uri.writeToFile(file: File, context: Context) {
    file.outputStream().channel.use { oChannel ->
        context.contentResolver.openFileDescriptor(this, "r")?.use { parcelFileDescriptor ->
            FileInputStream(parcelFileDescriptor.fileDescriptor).channel.use { iChannel ->
                val byteBuffer = ByteBuffer.allocateDirect(1024)
                while (iChannel.read(byteBuffer) != -1) {
                    byteBuffer.flip()
                    oChannel.write(byteBuffer)
                    byteBuffer.clear()
                }
            }
        }
    }
}