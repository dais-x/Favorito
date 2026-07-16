package com.favorito

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Intercepts long-press volume keys for track skip.
 *
 * Battery rule: when Favorito is disabled or music is not actively playing,
 * onKeyEvent returns before allocation, scheduling, DB access, or IPC. The
 * service may be enabled by the user, but it remains computationally idle.
 */
class VolumeKeyAccessibilityService : AccessibilityService() {
    private var consumedLongPressKeyCode: Int? = null
    private var lastSkipAtMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!SettingsStore.masterEnabled ||
            !SettingsStore.volumeSkipEnabled ||
            !PlaybackStateHolder.isMusicActivelyPlaying
        ) {
            consumedLongPressKeyCode = null
            return false
        }

        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        if (event.action == KeyEvent.ACTION_UP && consumedLongPressKeyCode == keyCode) {
            consumedLongPressKeyCode = null
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount == 0) {
            return false
        }

        val now = event.eventTime
        if (now - lastSkipAtMs < SKIP_DEBOUNCE_MS) return true
        lastSkipAtMs = now
        consumedLongPressKeyCode = keyCode

        val mediaKey = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            KeyEvent.KEYCODE_MEDIA_NEXT
        } else {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        dispatchMediaKey(mediaKey)
        return true
    }

    private fun dispatchMediaKey(mediaKeyCode: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val down = KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, mediaKeyCode)
        audio.dispatchMediaKeyEvent(down)
        audio.dispatchMediaKeyEvent(up)
    }

    companion object {
        private const val SKIP_DEBOUNCE_MS = 900L
    }
}
