package io.github.marcocapile.launchermanager.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.marcocapile.launchermanager.data.LauncherInfo
import io.github.marcocapile.launchermanager.system.LauncherActions
import io.github.marcocapile.launchermanager.system.UninstallPackageContract
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var confirmUninstall by remember { mutableStateOf(false) }

    val uninstallPrompt = rememberLauncherForActivityResult(UninstallPackageContract()) {
        viewModel.onPromptResult()
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAccessGranted()
        if (!granted) {
            scope.launch { snackbar.showSnackbar("Permission denied: some launchers may be missing from the list") }
        }
    }

    // Package changes can happen behind our back (Play Store, Settings), so reload on every resume.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.pendingPrompt, state.promptLaunched) {
        val pkg = state.pendingPrompt
        if (pkg != null && !state.promptLaunched) {
            viewModel.onPromptLaunched()
            try {
                uninstallPrompt.launch(pkg)
            } catch (e: ActivityNotFoundException) {
                viewModel.onPromptResult()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Launcher Manager") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.accessGranted) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { confirmUninstall = true },
                    icon = { Icon(Icons.Default.Delete, null) },
                    text = { Text("Uninstall ${state.selected.size}") },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.accessGranted) {
            AccessScreen(
                modifier = Modifier.padding(padding),
                onAllow = {
                    val permission = viewModel.runtimePermissionToRequest()
                    if (permission != null) permissionRequest.launch(permission) else viewModel.onAccessGranted()
                },
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DefaultLauncherCard(
                    state = state,
                    onRestore = viewModel::restoreDefault,
                    onHomeSettings = { LauncherActions.openHomeSettings(context) },
                )
            }
            item {
                SectionHeader(
                    count = state.thirdParty.size,
                    hasSelection = state.selected.isNotEmpty(),
                    onSelectAll = viewModel::selectAll,
                    onClear = viewModel::clearSelection,
                )
            }
            when {
                state.isLoading -> item {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.thirdParty.isEmpty() -> item { NoThirdPartyCard() }
                else -> items(state.thirdParty, key = { it.packageName }) { launcher ->
                    LauncherRow(
                        launcher = launcher,
                        checked = launcher.packageName in state.selected,
                        onToggle = { viewModel.toggle(launcher) },
                        onOpen = { LauncherActions.openLauncher(context, launcher) },
                        onAppInfo = { LauncherActions.openAppInfo(context, launcher.packageName) },
                    )
                }
            }
        }
    }

    if (confirmUninstall) {
        ConfirmUninstallDialog(
            state = state,
            onConfirm = {
                confirmUninstall = false
                viewModel.uninstallSelected()
            },
            onDismiss = { confirmUninstall = false },
        )
    }
    state.progress?.let { ProgressDialog(it, onStop = viewModel::cancelQueue) }
    if (state.showRestoreGuide) {
        RestoreGuideDialog(
            state = state,
            onAppInfo = { LauncherActions.openAppInfo(context, it) },
            onHomeSettings = { LauncherActions.openHomeSettings(context) },
            onDismiss = viewModel::dismissRestoreGuide,
        )
    }
}

@Composable
private fun AccessScreen(modifier: Modifier, onAllow: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.padding(24.dp).size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Allow access to installed apps",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Launcher Manager needs to see which apps are installed to find every home-screen " +
                "launcher, so you can remove the ones you don't want and go back to the stock " +
                "launcher. Nothing leaves your device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAllow) { Text("Allow access") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultLauncherCard(state: UiState, onRestore: () -> Unit, onHomeSettings: () -> Unit) {
    val current = state.defaultLauncher
    val stock = state.stockLauncher
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Current home app",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(current, size = 48)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        current?.label ?: "Not set",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        current?.packageName ?: "Android asks which launcher to use",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    stock == null -> "No stock launcher detected"
                    state.isOnStock -> "You're using the stock launcher"
                    else -> "Stock launcher: ${stock.label}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore, enabled = stock != null) {
                    Icon(Icons.Default.Home, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Restore default")
                }
                OutlinedButton(onClick = onHomeSettings) {
                    Icon(Icons.Default.Settings, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Home settings")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(count: Int, hasSelection: Boolean, onSelectAll: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Third-party launchers ($count)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) TextButton(onClick = onSelectAll) { Text("Select all") }
        if (hasSelection) TextButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun NoThirdPartyCard() {
    OutlinedCard {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text("No third-party launchers") },
            supportingContent = { Text("Only the launchers that came with the device are installed.") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LauncherRow(
    launcher: LauncherInfo,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val container = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = container)) {
        ListItem(
            modifier = Modifier.clickable(onClick = onToggle),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle() })
                    AppIcon(launcher, size = 40)
                }
            },
            headlineContent = {
                Text(launcher.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Column {
                    Text(
                        listOfNotNull(launcher.packageName, launcher.versionName?.let { "v$it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (launcher.isDefault || !launcher.isEnabled) {
                        FlowRow(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val colors = MaterialTheme.colorScheme
                            if (launcher.isDefault) Pill("Default", colors.primary, colors.onPrimary)
                            if (!launcher.isEnabled) Pill("Disabled", colors.errorContainer, colors.onErrorContainer)
                        }
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${launcher.label}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (launcher.isActive) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                            onClick = { menuOpen = false; onOpen() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("App info") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { menuOpen = false; onAppInfo() },
                    )
                }
            },
        )
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AppIcon(launcher: LauncherInfo?, size: Int) {
    val icon = launcher?.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun ConfirmUninstallDialog(state: UiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val targets = state.selectedLaunchers
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null) },
        title = { Text(if (targets.size == 1) "Uninstall 1 launcher?" else "Uninstall ${targets.size} launchers?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                targets.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android will ask you to confirm each one in turn. Just tap OK on every prompt.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (targets.any { it.isDefault }) {
                    Text(
                        "This includes the current default launcher." +
                            (state.stockLauncher?.let { " You'll then be helped back to ${it.label}." } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Uninstall") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProgressDialog(progress: Progress, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Uninstalling launchers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(progress.current, style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${minOf(progress.done + 1, progress.total)} of ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onStop) { Text("Stop") } },
    )
}

@Composable
private fun RestoreGuideDialog(
    state: UiState,
    onAppInfo: (String) -> Unit,
    onHomeSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stock = state.stockLauncher
    val current = state.defaultLauncher?.takeIf { !it.isSystem }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Home, null) },
        title = { Text("Back to ${stock?.label ?: "the stock launcher"}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Android only lets you pick the home app yourself. It takes a few taps:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (current != null) {
                    Step(
                        "${current.label} is still the default. Uninstall it from the list, or open " +
                            "App info → Open by default → Clear defaults.",
                    )
                    TextButton(onClick = { onAppInfo(current.packageName) }) { Text("App info: ${current.label}") }
                }
                if (stock != null && !stock.isInstalledForUser) {
                    Step(
                        "${stock.label} was removed for this user. Reinstall it from a computer with: " +
                            "adb shell cmd package install-existing ${stock.packageName}",
                        warning = true,
                    )
                } else if (stock != null && !stock.isEnabled) {
                    Step("${stock.label} is disabled. Tap Enable in its App info.", warning = true)
                    TextButton(onClick = { onAppInfo(stock.packageName) }) { Text("App info: ${stock.label}") }
                }
                Step("Open Home app settings and choose ${stock?.label ?: "the stock launcher"}.")
            }
        },
        confirmButton = {
            Button(onClick = { onDismiss(); onHomeSettings() }) { Text("Open Home settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Step(text: String, warning: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (warning) Icons.Default.Warning else Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
