package com.favorito

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences is used for the Capacitor bridge because every setting is
 * a scalar and must be readable from services without starting Room.
 *
 * Volatile mirrors avoid disk reads on hot event paths. Preference writes are
 * rare user actions, while key and media callbacks must be near-zero cost.
 */
object SettingsStore {
    private const val PREFS = "favorito_settings"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_PROMPT_TYPE = "prompt_type"
    private const val KEY_MIN_DURATION = "minimum_duration_seconds"
    private const val KEY_VOLUME_SKIP = "volume_skip_enabled"
    private const val KEY_LIKE_ACTION = "like_action_mode"
    private const val KEY_YOUTUBE_TOKEN = "youtube_oauth_token"

    const val PROMPT_VOICE = "voice"
    const val PROMPT_SUBTLE = "subtle"
    const val LIKE_INTENT = "intent"
    const val LIKE_QUEUE = "queue"

    private lateinit var prefs: SharedPreferences

    @Volatile var masterEnabled: Boolean = false
        private set
    @Volatile var promptType: String = PROMPT_VOICE
        private set
    @Volatile var minimumDurationSeconds: Int = 90
        private set
    @Volatile var volumeSkipEnabled: Boolean = false
        private set
    @Volatile var likeActionMode: String = LIKE_INTENT
        private set
    @Volatile var youtubeOauthToken: String = ""
        private set

    private val reloadListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadFromDisk() }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadFromDisk()
        prefs.registerOnSharedPreferenceChangeListener(reloadListener)
    }

    private fun loadFromDisk() {
        masterEnabled = prefs.getBoolean(KEY_MASTER, false)
        promptType = prefs.getString(KEY_PROMPT_TYPE, PROMPT_VOICE) ?: PROMPT_VOICE
        minimumDurationSeconds = prefs.getInt(KEY_MIN_DURATION, 90).coerceAtLeast(1)
        volumeSkipEnabled = prefs.getBoolean(KEY_VOLUME_SKIP, false)
        likeActionMode = prefs.getString(KEY_LIKE_ACTION, LIKE_INTENT) ?: LIKE_INTENT
        youtubeOauthToken = prefs.getString(KEY_YOUTUBE_TOKEN, "") ?: ""
    }

    fun snapshot(): Map<String, Any> = mapOf(
        "masterEnabled" to masterEnabled,
        "promptType" to promptType,
        "minimumDurationSeconds" to minimumDurationSeconds,
        "volumeSkipEnabled" to volumeSkipEnabled,
        "likeActionMode" to likeActionMode,
        "youtubeOauthTokenConfigured" to youtubeOauthToken.isNotBlank()
    )

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER, enabled).apply()
    }

    fun setPromptType(value: String) {
        val normalized = if (value == PROMPT_SUBTLE) PROMPT_SUBTLE else PROMPT_VOICE
        prefs.edit().putString(KEY_PROMPT_TYPE, normalized).apply()
    }

    fun setMinimumDurationSeconds(value: Int) {
        prefs.edit().putInt(KEY_MIN_DURATION, value.coerceAtLeast(1)).apply()
    }

    fun setVolumeSkipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_SKIP, enabled).apply()
    }

    fun setLikeActionMode(value: String) {
        val normalized = if (value == LIKE_QUEUE) LIKE_QUEUE else LIKE_INTENT
        prefs.edit().putString(KEY_LIKE_ACTION, normalized).apply()
    }

    fun setYoutubeOauthToken(token: String) {
        prefs.edit().putString(KEY_YOUTUBE_TOKEN, token.trim()).apply()
    }
}
