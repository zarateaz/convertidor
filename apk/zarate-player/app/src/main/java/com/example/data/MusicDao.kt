package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- Favorites ---
    @Query("SELECT * FROM favorite_songs ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE title = :title AND artist = :artist")
    suspend fun deleteFavoriteByDetails(title: String, artist: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE title = :title AND artist = :artist)")
    suspend fun isFavorite(title: String, artist: String): Boolean


    // --- Playlists ---
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND path = :path")
    suspend fun deletePlaylistSong(playlistId: Long, path: String)


    // --- Stats ---
    @Query("SELECT * FROM playback_stats ORDER BY playCount DESC LIMIT 15")
    fun getTopPlayedStats(): Flow<List<PlaybackStatEntity>>

    @Query("SELECT * FROM playback_stats ORDER BY lastPlayed DESC LIMIT 15")
    fun getRecentlyPlayedStats(): Flow<List<PlaybackStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStat(stat: PlaybackStatEntity)

    @Query("SELECT * FROM playback_stats WHERE title = :title AND artist = :artist")
    suspend fun getStatForSong(title: String, artist: String): PlaybackStatEntity?


    // --- Equalizer Presets ---
    @Query("SELECT * FROM equalizer_presets WHERE id = 1")
    fun getEqualizerPreset(): Flow<EqualizerPresetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEqualizerPreset(preset: EqualizerPresetEntity)
}
