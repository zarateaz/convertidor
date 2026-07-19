package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs")
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val duration: Long
)

@Entity(tableName = "playback_stats")
data class PlaybackStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val playCount: Int = 1,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "equalizer_presets")
data class EqualizerPresetEntity(
    @PrimaryKey val id: Int = 1, // Store a single customizable user preset
    val name: String = "Cyber-Cava Custom",
    val band31: Float = 0f,
    val band62: Float = 0f,
    val band125: Float = 0f,
    val band250: Float = 0f,
    val band500: Float = 0f,
    val band1k: Float = 0f,
    val band2k: Float = 0f,
    val band4k: Float = 0f,
    val band8k: Float = 0f,
    val band16k: Float = 0f,
    val reverbType: String = "Cave", // None, Room, Large Hall, Wine Cave
    val bassBoost: Int = 30, // 0 to 100
    val vocalClarifier: Int = 20, // 0 to 100
    val volumeNormalizer: Boolean = true
)
