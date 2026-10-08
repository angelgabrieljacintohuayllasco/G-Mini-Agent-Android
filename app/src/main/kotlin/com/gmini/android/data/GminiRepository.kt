package com.gmini.android.data

import android.os.Build
import com.gmini.android.core.model.ChatResponse
import com.gmini.android.core.model.DeviceInfo
import com.gmini.android.core.model.Health
import com.gmini.android.core.model.Me
import com.gmini.android.core.model.PairClaimRequest
import com.gmini.android.core.model.SessionMessage
import com.gmini.android.core.model.SessionSummary
import com.gmini.android.core.model.TaskInfo
import com.gmini.android.core.model.TaskRequest
import com.gmini.android.core.model.VoiceTurnResponse
import com.gmini.android.core.net.GminiClient
import com.gmini.android.core.pairing.PairingTarget
import com.gmini.android.core.protocol.ChatEvent
import com.gmini.android.core.store.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Punto unico de acceso a la API remota. Construye un [GminiClient] para el
 * perfil activo (y lo reutiliza mientras no cambien host, puerto ni token) y
 * expone operaciones de alto nivel para las pantallas.
 */
class GminiRepository(private val secureStore: SecureStore) {

    private val _profiles = MutableStateFlow(secureStore.list())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(currentProfileFromStore())
    val activeProfile: StateFlow<ServerProfile?> = _activeProfile.asStateFlow()

    @Volatile
    private var cachedClient: GminiClient? = null

    @Volatile
    private var cachedKey: String? = null

    fun deviceName(): String = Build.MODEL ?: "Android"

    fun activeToken(): String? = _activeProfile.value?.let { secureStore.getToken(it.id) }

    /** Cliente del perfil activo, o `null` si todavia no hay ninguno emparejado. */
    fun currentClient(): GminiClient? {
        val profile = _activeProfile.value ?: return null
        val token = secureStore.getToken(profile.id)
        val key = "${profile.id}|${profile.host}|${profile.port}|${profile.secure}|$token"
        if (key != cachedKey || cachedClient == null) {
            cachedClient = GminiClient.fromProfile(profile, token)
            cachedKey = key
        }
        return cachedClient
    }

    private fun requireClient(): GminiClient =
        currentClient() ?: error("No hay un servidor emparejado.")

    // --- Perfiles ----------------------------------------------------------

    fun setActiveProfile(id: String) {
        secureStore.setActiveProfileId(id)
        invalidate()
    }

    suspend fun pair(target: PairingTarget, label: String, secure: Boolean): ServerProfile {
        val scheme = if (secure) "https" else "http"
        val temp = GminiClient.create("$scheme://${target.host}:${target.port}", { null })
        val claim = temp.rest.pairClaim(
            PairClaimRequest(code = target.code, deviceName = deviceName()),
        )
        val profile = ServerProfile(
            id = claim.deviceId,
            label = label.ifBlank { claim.serverName ?: target.host },
            host = target.host,
            port = target.port,
            secure = secure,
            deviceId = claim.deviceId,
            agentName = claim.agentName,
            serverName = claim.serverName,
            scopes = claim.scopes,
        )
        secureStore.upsert(profile)
        secureStore.setToken(profile.id, claim.token)
        secureStore.setActiveProfileId(profile.id)
        invalidate()
        return profile
    }

    suspend fun unpair(profile: ServerProfile) {
        val token = secureStore.getToken(profile.id)
        if (token != null) {
            runCatching {
                GminiClient.fromProfile(profile, token).rest.deleteDevice(profile.deviceId ?: profile.id)
            }
        }
        secureStore.remove(profile.id)
        invalidate()
    }

    // --- Operaciones -------------------------------------------------------

    suspend fun health(): Health = requireClient().rest.health()

    suspend fun me(): Me = requireClient().rest.me()

    suspend fun chat(message: String, sessionId: String?): ChatResponse =
        requireClient().rest.chat(message, sessionId)

    fun chatStream(message: String, sessionId: String?): Flow<ChatEvent> =
        requireClient().rest.chatStream(message, sessionId)

    suspend fun approve(approve: Boolean) = requireClient().rest.approve(approve)

    suspend fun listSessions(limit: Int = 20): List<SessionSummary> =
        requireClient().rest.listSessions(limit)

    suspend fun sessionMessages(sessionId: String, limit: Int = 100): List<SessionMessage> =
        requireClient().rest.sessionMessages(sessionId, limit)

    suspend fun listTasks(status: String? = null): List<TaskInfo> =
        requireClient().rest.listTasks(status)

    suspend fun createTask(request: TaskRequest) = requireClient().rest.createTask(request)

    suspend fun cancelTask(taskId: String) = requireClient().rest.cancelTask(taskId)

    suspend fun listDevices(): List<DeviceInfo> = requireClient().rest.listDevices()

    suspend fun tts(text: String, voice: String? = null): ByteArray =
        requireClient().rest.tts(com.gmini.android.core.model.TtsRequest(text = text, voice = voice))

    suspend fun stt(wav: ByteArray): String = requireClient().rest.stt(wav).text

    suspend fun voiceTurn(wav: ByteArray, sessionId: String?): VoiceTurnResponse =
        requireClient().rest.voiceTurn(wav, sessionId)

    private fun currentProfileFromStore(): ServerProfile? =
        secureStore.activeProfileId()?.let { id -> secureStore.get(id) }

    private fun invalidate() {
        _profiles.value = secureStore.list()
        _activeProfile.value = currentProfileFromStore()
        cachedClient = null
        cachedKey = null
    }
}
