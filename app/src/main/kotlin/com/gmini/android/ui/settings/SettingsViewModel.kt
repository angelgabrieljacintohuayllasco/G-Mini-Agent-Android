package com.gmini.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmini.android.core.store.ServerProfile
import com.gmini.android.data.AppSettings
import com.gmini.android.data.GminiRepository
import com.gmini.android.data.SettingsRepository
import com.gmini.android.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: GminiRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: Flow<AppSettings> = settingsRepository.settings
    val profiles: StateFlow<List<ServerProfile>> = repository.profiles
    val activeProfile: StateFlow<ServerProfile?> = repository.activeProfile

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDisplayTextSurface(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSurfaceDisplayText(enabled) }
    }

    fun setTtsSpeakSurface(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSurfaceTtsSpeak(enabled) }
    }

    fun setBackgroundConnection(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundConnectionEnabled(enabled) }
    }

    fun selectProfile(id: String) {
        repository.setActiveProfile(id)
    }

    fun unpair(profile: ServerProfile) {
        viewModelScope.launch { repository.unpair(profile) }
    }
}
