package com.favorito

/**
 * Intentionally tiny process-local state shared by Android callbacks.
 *
 * AccessibilityService.onKeyEvent() is a latency-sensitive path that can fire
 * while the screen is locked. It must not open Room, bind a service, query
 * MediaSessionManager, or allocate work just to decide "do nothing".
 */
object PlaybackStateHolder {
    @Volatile
    var isMusicActivelyPlaying: Boolean = false

    @Volatile
    var activePackageName: String? = null

    @Volatile
    var lastTrackKey: String? = null

    fun clear() {
        isMusicActivelyPlaying = false
        activePackageName = null
        lastTrackKey = null
    }
}
