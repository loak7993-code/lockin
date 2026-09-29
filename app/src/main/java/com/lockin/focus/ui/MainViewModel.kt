package com.lockin.focus.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.lockin.focus.LockIn
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.SeedPreset
import com.lockin.focus.core.model.ThemeMode
import com.lockin.focus.data.AppCatalog
import com.lockin.focus.data.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val session = LockIn.engine.state
    val lastEnded = LockIn.engine.lastEnded
    val settings = LockIn.settingsFlow
    val history = LockIn.historyFlow

    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var appsLoading by mutableStateOf(true)
        private set
    var appsFailed by mutableStateOf(false)
        private set

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            appsLoading = true
            appsFailed = false
            val result = withContext(Dispatchers.IO) {
                runCatching { AppCatalog.loadInstalled(getApplication()) }
            }
            appsFailed = result.isFailure
            installedApps = result.getOrDefault(emptyList())
            appsLoading = false
        }
    }

    // ---- session -----------------------------------------------------------

    fun startSession(task: String) = LockIn.startSession(task, settings.value.intervalMs)

    fun completeSession() = LockIn.completeSession()

    fun escapeSession() = LockIn.escapeSession()

    // ---- blocklist ---------------------------------------------------------

    fun setBlocked(pkg: String, blocked: Boolean) = update { current ->
        val next = if (blocked) current.blocked + pkg else current.blocked - pkg
        current.copy(blocked = next, allowed = current.allowed - pkg)
    }

    fun setAllowed(pkg: String, allowed: Boolean) = update { current ->
        if (allowed) {
            current.copy(allowed = current.allowed + pkg, blocked = current.blocked - pkg)
        } else {
            current.copy(allowed = current.allowed - pkg)
        }
    }

    fun blockAllUserApps() = update { current ->
        val target = installedApps.filterNot { it.system }.map { it.packageName }.toSet()
        current.copy(blocked = current.blocked + target, allowed = current.allowed - target)
    }

    fun clearBlocked() = update { it.copy(blocked = emptySet()) }

    /**
     * Everything the user can see, minus LockIn itself. Packages the access
     * policy will never block (dialer, launcher, system UI) are still recorded —
     * keeping the list honest about what was selected — and the policy overrides
     * them at enforcement time.
     */
    fun blockAllInstalled() = update { current ->
        val self = getApplication<Application>().packageName
        val everything = installedApps
            .filterNot { it.packageName == self }
            .map { it.packageName }
            .toSet()
        current.copy(blocked = current.blocked + everything, allowed = emptySet())
    }

    // ---- settings ----------------------------------------------------------

    fun setIntervalMinutes(minutes: Long) = update { it.copy(intervalMs = minutes * 60_000L) }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setSeed(preset: SeedPreset) = update { it.copy(seed = preset) }

    fun setVibrate(enabled: Boolean) = update { it.copy(vibrateOnCheckIn = enabled) }

    fun setDoomDetection(enabled: Boolean) = update { it.copy(doomDetection = enabled) }

    fun setDoomThresholdMinutes(minutes: Long) = update { it.copy(doomThresholdMs = minutes * 60_000L) }

    fun setGoalRoulette(enabled: Boolean) = update { it.copy(goalRoulette = enabled) }

    fun setAutoResume(enabled: Boolean) = update { it.copy(autoResumeOnBoot = enabled) }

    fun markOnboarded() = update { it.copy(onboarded = true) }

    fun clearHistory() = viewModelScope.launch { LockIn.clearHistory() }

    /**
     * Pulls a colour out of a photo the user chose and rebuilds the whole scheme
     * from it — the Pixel "theme from wallpaper" idea, pointed at an image.
     */
    fun applySeedFromImage(uri: Uri) {
        viewModelScope.launch {
            val argb = withContext(Dispatchers.IO) { extractColor(uri) } ?: return@launch
            update { it.copy(seed = SeedPreset.CUSTOM, customSeedArgb = argb, dynamicColor = false) }
        }
    }

    private fun extractColor(uri: Uri): Int? = runCatching {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream) ?: return@use null
            val palette = Palette.from(bitmap).clearFilters().maximumColorCount(24).generate()
            palette.vibrantSwatch?.rgb ?: palette.mutedSwatch?.rgb ?: palette.dominantSwatch?.rgb
        }
    }.getOrNull()

    private fun update(transform: (FocusSettings) -> FocusSettings) {
        viewModelScope.launch { LockIn.updateSettings(transform) }
    }
}
