package io.github.marcocapile.launchermanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marcocapile.launchermanager.data.LauncherInfo
import io.github.marcocapile.launchermanager.data.LauncherRepository
import io.github.marcocapile.launchermanager.system.AppListAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Progress(val current: String, val done: Int, val total: Int)

data class UiState(
    val accessGranted: Boolean = false,
    val isLoading: Boolean = true,
    val launchers: List<LauncherInfo> = emptyList(),
    val stockLauncher: LauncherInfo? = null,
    val selected: Set<String> = emptySet(),
    val progress: Progress? = null,
    /** Package waiting for the system uninstall prompt. */
    val pendingPrompt: String? = null,
    val promptLaunched: Boolean = false,
    val showRestoreGuide: Boolean = false,
) {
    val defaultLauncher: LauncherInfo? get() = launchers.find { it.isDefault }

    /** Launchers the user installed; these are the ones offered for removal. */
    val thirdParty: List<LauncherInfo> get() = launchers.filter { !it.isSystem && it.isInstalledForUser }

    val selectedLaunchers: List<LauncherInfo> get() = thirdParty.filter { it.packageName in selected }

    /** True when the stock launcher is already what the HOME button opens. */
    val isOnStock: Boolean
        get() = stockLauncher != null && defaultLauncher?.packageName == stockLauncher.packageName
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LauncherRepository(application)
    private val access = AppListAccess(application)

    private val _state = MutableStateFlow(UiState(accessGranted = access.isGranted))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val promptQueue = ArrayDeque<LauncherInfo>()
    private var queueTotal = 0
    private var queueRemoved = 0

    /** ROM-specific permission still needed before the app list is complete, if any. */
    fun runtimePermissionToRequest(): String? = access.runtimePermissionToRequest()

    fun onAccessGranted() {
        access.markGranted()
        _state.update { it.copy(accessGranted = true) }
        refresh()
    }

    fun refresh() {
        if (!_state.value.accessGranted) return
        viewModelScope.launch {
            val launchers = withContext(Dispatchers.Default) { repository.loadLaunchers() }
            val stock = repository.pickStockLauncher(launchers)
            _state.update { s ->
                val updated = s.copy(isLoading = false, launchers = launchers, stockLauncher = stock)
                updated.copy(selected = s.selected intersect updated.thirdParty.map { it.packageName }.toSet())
            }
        }
    }

    fun toggle(launcher: LauncherInfo) {
        if (launcher.isSystem) return
        _state.update {
            val pkg = launcher.packageName
            it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg)
        }
    }

    fun selectAll() = _state.update { s -> s.copy(selected = s.thirdParty.map { it.packageName }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Queues the checked launchers; Android shows its uninstall confirmation for each in turn. */
    fun uninstallSelected() {
        val s = _state.value
        val targets = s.selectedLaunchers
        if (targets.isEmpty() || s.progress != null) return
        if (s.launchers.none { it.isActive && it.packageName !in s.selected }) {
            post("Keep at least one working launcher, or the device has no home screen")
            return
        }
        queueTotal = targets.size
        queueRemoved = 0
        promptQueue.clear()
        promptQueue.addAll(targets)
        promptNext()
    }

    private fun promptNext() {
        val next = promptQueue.removeFirstOrNull()
        if (next == null) {
            finishQueue()
            return
        }
        _state.update {
            it.copy(
                progress = Progress(next.label, queueTotal - promptQueue.size - 1, queueTotal),
                pendingPrompt = next.packageName,
                promptLaunched = false,
            )
        }
    }

    fun onPromptLaunched() = _state.update { it.copy(promptLaunched = true) }

    fun onPromptResult() {
        val pkg = _state.value.pendingPrompt ?: return
        // Trust the package manager rather than the result code; some OEM installers lie.
        if (!repository.isInstalledForUser(pkg)) queueRemoved++
        _state.update { it.copy(pendingPrompt = null, promptLaunched = false) }
        promptNext()
    }

    /** Stops after the prompt currently on screen. */
    fun cancelQueue() = promptQueue.clear()

    private fun finishQueue() {
        val stock = _state.value.stockLauncher
        val defaultPackage = repository.currentDefaultPackage()
        _state.update {
            it.copy(
                progress = null,
                pendingPrompt = null,
                promptLaunched = false,
                selected = emptySet(),
                // Offer the way back to stock if HOME doesn't open it yet.
                showRestoreGuide = stock != null && defaultPackage != stock.packageName,
            )
        }
        refresh()
        post("Removed $queueRemoved of $queueTotal launchers")
    }

    /** Guides the user back to the stock launcher; Android doesn't let apps change it directly. */
    fun restoreDefault() {
        val s = _state.value
        if (s.isOnStock) {
            post("${s.stockLauncher?.label} is already the default launcher")
        } else {
            _state.update { it.copy(showRestoreGuide = true) }
        }
    }

    fun dismissRestoreGuide() = _state.update { it.copy(showRestoreGuide = false) }

    private fun post(message: String) {
        _messages.trySend(message)
    }
}
