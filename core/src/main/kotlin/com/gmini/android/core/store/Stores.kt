package com.gmini.android.core.store

/**
 * Guarda los tokens de dispositivo. La implementacion de Android lo respalda
 * con el Keystore (EncryptedSharedPreferences); el nucleo solo conoce el
 * contrato para poder probarse en la JVM.
 */
public interface TokenStore {
    public fun getToken(profileId: String): String?
    public fun setToken(profileId: String, token: String)
    public fun clearToken(profileId: String)
}

/** Guarda los perfiles de servidor y cual esta activo. */
public interface ProfileStore {
    public fun list(): List<ServerProfile>
    public fun get(id: String): ServerProfile?
    public fun upsert(profile: ServerProfile)
    public fun remove(id: String)
    public fun activeProfileId(): String?
    public fun setActiveProfileId(id: String?)
}
