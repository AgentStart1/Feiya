package com.storyteller_f.feiya.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import com.jamal.composeprefs3.ui.PrefsScreen
import com.jamal.composeprefs3.ui.prefs.EditTextPref
import com.storyteller_f.feiya.dataStore
import com.storyteller_f.feiya.R
import androidx.compose.ui.res.stringResource


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SafePage() {
    PrefsScreen(dataStore = LocalContext.current.dataStore) {
        prefsItem {
            EditTextPref(key = "password", title = stringResource(R.string.access_password), summary = stringResource(R.string.access_password_hint))
        }
    }
}
