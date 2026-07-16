package com.favorito

data class TrackSnapshot(
    val trackKey: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)
