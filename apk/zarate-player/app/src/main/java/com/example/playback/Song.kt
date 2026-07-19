package com.example.playback

import java.io.Serializable

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in ms
    val path: String, // file path or sim_key
    val isSimulated: Boolean = false,
    val format: String = "FLAC",
    val bitrate: String = "1411 kbps",
    val sampleRate: String = "44.1 kHz / 16-bit",
    val isHiRes: Boolean = true,
    val year: String = "2099",
    val lyrics: Map<Long, String> = emptyMap(), // timestamp -> lyric line
    val genre: String = "Cyber-Ambient"
) : Serializable {
    fun getFormattedDuration(): String {
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
