package com.storyteller_f.feiya.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyteller_f.feiya.R

private data class Destination(val route: String, val title: Int, val icon: ImageVector)
private val destinations = listOf(
    Destination("main", R.string.files_title, Icons.Default.Home),
    Destination("messages", R.string.messages, Icons.Default.Email),
    Destination("hid", R.string.keyboard_title, Icons.Default.Build),
    Destination("safe", R.string.security_title, Icons.Default.Lock),
    Destination("settings", R.string.settings, Icons.Default.Settings),
)

@Composable
fun destinationTitle(route: String) = stringResource(if (route == "main") R.string.shared_files else destinations.firstOrNull { it.route == route }?.title ?: R.string.shared_files)

/** Choose navigation from the available window, including split-screen windows. */
@Composable
fun AppShell(
    route: String,
    navigate: (String) -> Unit,
    about: () -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideNavigation = maxWidth >= 600.dp
        val expanded = maxWidth >= 1000.dp
        val generous = maxWidth >= 1200.dp
        val largeText = LocalDensity.current.fontScale > 1.3f
        Row(Modifier.fillMaxSize()) {
            if (sideNavigation) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(if (generous) 280.dp else if (expanded) 224.dp else 96.dp).fillMaxHeight().testTag("side_navigation")) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.feiya_logo), null, Modifier.size(48.dp))
                            if (expanded) Text("Feiya", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
                        }
                        destinations.forEach { destination ->
                            if (expanded) NavigationDrawerItem(
                                label = { Text(stringResource(destination.title), style = MaterialTheme.typography.titleMedium, fontSize = if (generous) 20.sp else 16.sp) },
                                selected = route == destination.route,
                                onClick = { navigate(destination.route) },
                                icon = { Icon(when (destination.route) { "main" -> ImageVector.vectorResource(R.drawable.nav_folder); "hid" -> ImageVector.vectorResource(R.drawable.nav_keyboard); else -> destination.icon }, if (largeText && !expanded) stringResource(destination.title) else null) },
                            ) else NavigationRailItem(
                                selected = route == destination.route,
                                onClick = { navigate(destination.route) },
                                icon = { Icon(when (destination.route) { "main" -> ImageVector.vectorResource(R.drawable.nav_folder); "hid" -> ImageVector.vectorResource(R.drawable.nav_keyboard); else -> destination.icon }, if (largeText && !expanded) stringResource(destination.title) else null) },
                                label = if (largeText) null else { { Text(stringResource(destination.title), maxLines = 1) } },
                            )
                        }
                        TextButton(onClick = about, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.about)) }
                    }
                }
                VerticalDivider()
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = topBar,
                bottomBar = {
                    if (!sideNavigation) NavigationBar(Modifier.testTag("bottom_navigation")) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route,
                                onClick = { navigate(destination.route) },
                                icon = { Icon(when (destination.route) { "main" -> ImageVector.vectorResource(R.drawable.nav_folder); "hid" -> ImageVector.vectorResource(R.drawable.nav_keyboard); else -> destination.icon }, if (largeText && !expanded) stringResource(destination.title) else null) },
                                label = if (largeText) null else { { Text(stringResource(destination.title), maxLines = 1) } },
                            )
                        }
                    }
                },
                content = content,
            )
        }
    }
}

/** Forms retain a comfortable reading width instead of stretching across a tablet. */
@Composable
fun FormPane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp)) { content() }
    }
}
