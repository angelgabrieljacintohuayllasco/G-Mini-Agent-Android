package com.gmini.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gmini.android.core.GminiJson
import com.gmini.android.core.store.ProfileStore
import com.gmini.android.core.store.ServerProfile
import com.gmini.android.core.store.TokenStore
import kotlinx.serialization.builtins.ListSerializer

/**
 * Almacen cifrado respaldado por el Keystore de Android. Guarda los perfiles de
 * servidor y, por separado, el token de cada uno. Implementa los contratos
 * [ProfileStore] y [TokenStore] del nucleo.
 */
class SecureStore(context: Context) : ProfileStore, TokenStore {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // --- ProfileStore ------------------------------------------------------

    override fun list(): List<ServerProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            GminiJson.decodeFromString(ListSerializer(ServerProfile.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun get(id: String): ServerProfile? = list().firstOrNull { it.id == id }

    override fun upsert(profile: ServerProfile) {
        val updated = list().filterNot { it.id == profile.id } + profile
        writeProfiles(updated)
    }

    override fun remove(id: String) {
        writeProfiles(list().filterNot { it.id == id })
        clearToken(id)
        if (activeProfileId() == id) {
            setActiveProfileId(list().firstOrNull()?.id)
        }
    }

    override fun activeProfileId(): String? = prefs.getString(KEY_ACTIVE, null)

    override fun setActiveProfileId(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
        }.apply()
    }

    // --- TokenStore --------------------------------------------------------

    override fun getToken(profileId: String): String? = prefs.getString(tokenKey(profileId), null)

    override fun setToken(profileId: String, token: String) {
        prefs.edit().putString(tokenKey(profileId), token).apply()
    }

    override fun clearToken(profileId: String) {
        prefs.edit().remove(tokenKey(profileId)).apply()
    }

    // --- Interno -----------------------------------------------------------

    private fun writeProfiles(profiles: List<ServerProfile>) {
        val raw = GminiJson.encodeToString(ListSerializer(ServerProfile.serializer()), profiles)
        prefs.edit().putString(KEY_PROFILES, raw).apply()
    }

    private fun tokenKey(profileId: String): String = "token_$profileId"

    companion object {
        const val PREFS_FILE: String = "gmini_secure_prefs"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active"
    }
}
