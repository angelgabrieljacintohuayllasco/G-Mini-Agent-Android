package com.gmini.android.di

import android.content.Context
import com.gmini.android.GminiApplication
import com.gmini.android.data.AgentConnection
import com.gmini.android.data.GminiRepository
import com.gmini.android.data.SecureStore
import com.gmini.android.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Inyeccion de dependencias manual. Una sola instancia vive en la [Application]
 * y la comparten las pantallas, los ViewModel y el servicio en primer plano.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secureStore: SecureStore by lazy { SecureStore(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val repository: GminiRepository by lazy { GminiRepository(secureStore) }

    val connection: AgentConnection by lazy { AgentConnection(appScope) }
}

/** Atajo para obtener el contenedor desde cualquier [Context]. */
fun Context.appContainer(): AppContainer =
    (applicationContext as GminiApplication).container
