package com.rebelroot.omni.sync.mozilla

data class LoginEntry(
    val guid: String,
    val hostname: String,
    val username: String,
    val password: String,
    val httpRealm: String? = null,
    val formSubmitUrl: String? = null,
    val timeCreated: Long = System.currentTimeMillis(),
    val timeLastUsed: Long = System.currentTimeMillis(),
    val timePasswordChanged: Long = System.currentTimeMillis()
)

class MozillaLoginBridge {

    /**
     * Deduplicates and resolves login conflicts using LWW (last write wins on password changed).
     */
    fun mergeLogins(local: List<LoginEntry>, remote: List<LoginEntry>): List<LoginEntry> {
        val merged = mutableMapOf<String, LoginEntry>()

        for (login in local) {
            val key = "${login.hostname}:${login.username}"
            merged[key] = login
        }

        for (login in remote) {
            val key = "${login.hostname}:${login.username}"
            val existing = merged[key]
            if (existing == null || login.timePasswordChanged > existing.timePasswordChanged) {
                merged[key] = login
            }
        }

        return merged.values.toList()
    }
}
