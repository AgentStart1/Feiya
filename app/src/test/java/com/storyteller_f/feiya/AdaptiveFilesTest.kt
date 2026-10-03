package com.storyteller_f.feiya

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.storyteller_f.feiya.service.SharedFileInfo
import com.storyteller_f.feiya.service.ServerState
import com.storyteller_f.feiya.ui.components.*
import com.storyteller_f.feiya.ui.theme.AppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveFilesTest {
    @get:Rule val compose = createComposeRule()
    private val first = SharedFileInfo("content://test/one", "Project notes.pdf")
    private val second = SharedFileInfo("content://test/two", "Screenshot.png")

    @Test fun wideSelectionUsesUriAcrossReorderRemovalAndRestore() {
        val files = mutableStateOf(listOf(first, second))
        val restore = StateRestorationTester(compose)
        val removed = mutableListOf<SharedFileInfo>()
        restore.setContent { MaterialTheme { SharedFiles(files.value, deleteItem = { removed.add(it) }) } }
        compose.onNodeWithTag("file_${second.uri}").performClick()
        compose.onNodeWithTag("file_list").assertIsDisplayed()
        compose.onNodeWithTag("file_detail").assertIsDisplayed()
        compose.runOnIdle { files.value = listOf(second, first) }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Remove from sharing").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(second), removed); files.value = listOf(first) }
        compose.onNodeWithTag("file_detail").assertDoesNotExist()
        compose.onNodeWithText("Select a file to see details").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "en-w390dp-h844dp-mdpi")
    fun phoneShowsSinglePaneAndReturnsToList() {
        compose.setContent { MaterialTheme { SharedFiles(listOf(first, second)) } }
        compose.onNodeWithTag("file_${first.uri}").performClick()
        compose.onNodeWithTag("file_list").assertDoesNotExist()
        compose.onNodeWithTag("file_detail").assertIsDisplayed()
        compose.onNodeWithText("Back to files").performClick()
        compose.onNodeWithTag("file_list").assertIsDisplayed()
    }

    @Test fun selectedFileSurvivesWindowResizeInBothDirections() {
        val width = mutableStateOf(1000.dp)
        compose.setContent { MaterialTheme { Box(Modifier.width(width.value).fillMaxHeight()) { SharedFiles(listOf(first, second)) } } }
        compose.onNodeWithTag("file_${second.uri}").performClick()
        compose.runOnIdle { width.value = 390.dp }
        compose.onNodeWithTag("file_list").assertDoesNotExist()
        compose.onNodeWithTag("file_detail").assertIsDisplayed()
        compose.runOnIdle { width.value = 1000.dp }
        compose.onNodeWithTag("file_${second.uri}").assertIsSelected()
        compose.onNodeWithTag("file_detail").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-night-mdpi")
    fun captureDarkLayout() { renderAndCapture("dark") }
    @Test @Config(qualifiers = "en-w390dp-h844dp-mdpi")
    fun captureLargeFontLayout() { renderAndCapture("large-font") }

    @Test @Config(qualifiers = "zh-rCN-w1487dp-h1058dp-mdpi")
    fun captureWideLayout() { renderAndCapture("wide") }
    @Test @Config(qualifiers = "en-w390dp-h844dp-mdpi")
    fun capturePhoneLayout() { renderAndCapture("phone") }
    @Test @Config(qualifiers = "en-w720dp-h480dp-mdpi")
    fun captureShortLandscape() { renderAndCapture("landscape") }

    private fun renderAndCapture(name: String) {
        val files = if (name == "wide") listOf(
            first.copy(name = "项目说明.pdf"), second.copy(name = "界面截图.png"),
            SharedFileInfo("content://test/zip", "资料归档.zip"), SharedFileInfo("content://test/md", "使用指南.md"),
        ) else listOf(first, second, SharedFileInfo("content://test/zip", "Archive.zip"), SharedFileInfo("content://test/md", "Readme.md"))
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (name == "large-font") 1.6f else density.fontScale)) {
            AppTheme(dynamicColor = false) {
                AppShell("main", {}, {}, topBar = {
                    MainToolbar(if (name == "wide") "共享文件" else "Shared files", "8080", ServerState.Init, {}, {}, {}, {}, true, {})
                }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { SharedFiles(files) } }
            }
        }
        }
        compose.onNodeWithTag("file_${first.uri}").performClick()
        compose.onNodeWithTag("file_detail").assertIsDisplayed()
        // Robolectric has no window compositor for PixelCopy. Draw the actual
        // Android view with native Skia after Compose has settled instead.
        val image = compose.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val output = File("build/outputs/design/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (name != "wide") compose.onNodeWithText("Remove from sharing").performScrollTo().assertIsDisplayed()
    }
}
