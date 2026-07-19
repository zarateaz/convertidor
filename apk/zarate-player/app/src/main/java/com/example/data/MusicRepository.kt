package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(private val musicDao: MusicDao) {

    val allFavorites: Flow<List<FavoriteSongEntity>> = musicDao.getAllFavorites()
    val allPlaylists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
    val topPlayedStats: Flow<List<PlaybackStatEntity>> = musicDao.getTopPlayedStats()
    val recentlyPlayedStats: Flow<List<PlaybackStatEntity>> = musicDao.getRecentlyPlayedStats()
    val equalizerPreset: Flow<EqualizerPresetEntity?> = musicDao.getEqualizerPreset()

    // --- Favorites ---
    suspend fun insertFavorite(title: String, artist: String, album: String, path: String) {
        withContext(Dispatchers.IO) {
            musicDao.insertFavorite(
                FavoriteSongEntity(
                    title = title,
                    artist = artist,
                    album = album,
                    path = path
                )
            )
        }
    }

    suspend fun deleteFavorite(title: String, artist: String) {
        withContext(Dispatchers.IO) {
            musicDao.deleteFavoriteByDetails(title, artist)
        }
    }

    suspend fun isFavorite(title: String, artist: String): Boolean {
        return withContext(Dispatchers.IO) {
            musicDao.isFavorite(title, artist)
        }
    }


    // --- Playlists ---
    suspend fun createPlaylist(name: String): Long {
        return withContext(Dispatchers.IO) {
            musicDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            musicDao.deletePlaylistById(playlistId)
        }
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> {
        return musicDao.getSongsForPlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, title: String, artist: String, album: String, path: String, duration: Long) {
        withContext(Dispatchers.IO) {
            musicDao.insertPlaylistSong(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    title = title,
                    artist = artist,
                    album = album,
                    path = path,
                    duration = duration
                )
            )
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, path: String) {
        withContext(Dispatchers.IO) {
            musicDao.deletePlaylistSong(playlistId, path)
        }
    }


    // --- Stats ---
    suspend fun incrementPlayCount(title: String, artist: String) {
        withContext(Dispatchers.IO) {
            val existing = musicDao.getStatForSong(title, artist)
            if (existing != null) {
                musicDao.insertStat(
                    existing.copy(
                        playCount = existing.playCount + 1,
                        lastPlayed = System.currentTimeMillis()
                    )
                )
            } else {
                musicDao.insertStat(
                    PlaybackStatEntity(
                        title = title,
                        artist = artist,
                        playCount = 1,
                        lastPlayed = System.currentTimeMillis()
                    )
                )
            }
        }
    }


    // --- Equalizer ---
    suspend fun saveEqualizer(preset: EqualizerPresetEntity) {
        withContext(Dispatchers.IO) {
            musicDao.saveEqualizerPreset(preset)
        }
    }
}
