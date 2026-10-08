package com.gmini.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gmini.android.di.AppContainer
import com.gmini.android.ui.chat.ChatViewModel
import com.gmini.android.ui.pairing.PairingViewModel
import com.gmini.android.ui.sessions.SessionsViewModel
import com.gmini.android.ui.settings.SettingsViewModel
import com.gmini.android.ui.tasks.TasksViewModel

/** Crea los ViewModel a partir del contenedor de dependencias de la app. */
class GminiViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ChatViewModel::class.java) ->
            ChatViewModel(container.repository, container.connection)

        modelClass.isAssignableFrom(PairingViewModel::class.java) ->
            PairingViewModel(container.repository)

        modelClass.isAssignableFrom(SessionsViewModel::class.java) ->
            SessionsViewModel(container.repository)

        modelClass.isAssignableFrom(TasksViewModel::class.java) ->
            TasksViewModel(container.repository)

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.repository, container.settings)

        else -> error("ViewModel no registrado: ${modelClass.name}")
    } as T
}
