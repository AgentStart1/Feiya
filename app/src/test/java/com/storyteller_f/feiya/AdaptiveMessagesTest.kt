package com.storyteller_f.feiya

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.storyteller_f.feiya.ui.components.MessagePage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w1280dp-h800dp-mdpi")
class AdaptiveMessagesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draftSurvivesPaneChangeAndSendUsesOriginalMarkdown() {
        val width = mutableStateOf(1000.dp)
        val sent = mutableListOf<String>()
        compose.setContent {
            MaterialTheme { Box(Modifier.width(width.value).fillMaxHeight()) { MessagePage(emptyList(), sendMessage = { sent.add(it) }) } }
        }
        compose.onNodeWithText("Preview").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("**hello**")
        compose.runOnIdle { width.value = 390.dp }
        compose.onNodeWithText("Preview").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("**hello**")
        compose.onNodeWithText("Send", ignoreCase = true).performClick()
        compose.runOnIdle { assertEquals(listOf("**hello**"), sent) }
        compose.onNodeWithText("Send", ignoreCase = true).assertIsNotEnabled()
        compose.runOnIdle { width.value = 1000.dp }
        compose.onNodeWithText("Preview").assertIsDisplayed()
    }
}
