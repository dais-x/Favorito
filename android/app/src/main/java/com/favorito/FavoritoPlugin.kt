package com.favorito

import android.content.Intent
import android.provider.Settings
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "Favorito")
class FavoritoPlugin : Plugin() {
    override fun load() {
        SettingsStore.init(context)
    }

    @PluginMethod
    fun getSettings(call: PluginCall) {
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun setMasterEnabled(call: PluginCall) {
        SettingsStore.setMasterEnabled(call.getBoolean("enabled", false) == true)
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun setPromptType(call: PluginCall) {
        SettingsStore.setPromptType(call.getString("promptType", SettingsStore.PROMPT_VOICE) ?: SettingsStore.PROMPT_VOICE)
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun setMinimumDurationSeconds(call: PluginCall) {
        SettingsStore.setMinimumDurationSeconds(call.getInt("seconds", 90) ?: 90)
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun setVolumeSkipEnabled(call: PluginCall) {
        SettingsStore.setVolumeSkipEnabled(call.getBoolean("enabled", false) == true)
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun setLikeActionMode(call: PluginCall) {
        SettingsStore.setLikeActionMode(call.getString("mode", SettingsStore.LIKE_INTENT) ?: SettingsStore.LIKE_INTENT)
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun setYoutubeOauthToken(call: PluginCall) {
        SettingsStore.setYoutubeOauthToken(call.getString("token", "") ?: "")
        call.resolve(SettingsStore.snapshot().toJsObject())
    }

    @PluginMethod
    fun openNotificationListenerSettings(call: PluginCall) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).asNewTask())
        call.resolve()
    }

    @PluginMethod
    fun openAccessibilitySettings(call: PluginCall) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).asNewTask())
        call.resolve()
    }

    private fun Map<String, Any>.toJsObject(): JSObject {
        val obj = JSObject()
        forEach { (key, value) -> obj.put(key, value) }
        return obj
    }

    private fun Intent.asNewTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
