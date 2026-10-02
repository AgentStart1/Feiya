package com.storyteller_f.feiya

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.storyteller_f.feiya.ui.components.BootPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en")
class BootPreferenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchReflectsSavedStateAndDisablesWhileBusy() {
        val state = mutableStateOf(BootSettingsState())
        val changes = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme { BootPreference(state.value, changes::add, {}) } }
        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
        compose.runOnIdle { state.value = BootSettingsState(loading = false) }
        compose.onNode(isToggleable()).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(true), changes)
            state.value = state.value.copy(saving = true)
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.runOnIdle { state.value = BootSettingsState(enabled = true, loading = false) }
        compose.onNode(isToggleable()).assertIsOn().assertIsEnabled()
    }

    @Test fun readErrorOffersRetryWithoutAllowingUnknownSettingToBeOverwritten() {
        var retries = 0
        compose.setContent {
            MaterialTheme { BootPreference(BootSettingsState(error = true), {}, { retries++ }) }
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
