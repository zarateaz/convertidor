package com.example.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.theme.CellarThemeType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val repository = MusicRepository(database.musicDao())

    // --- Core Media State Flows (synced with MusicPlayerManager) ---
    val currentSong: StateFlow<Song?> = MusicPlayerManager.currentSong
    val isPlaying: StateFlow<Boolean> = MusicPlayerManager.isPlaying
    val progress: StateFlow<Long> = MusicPlayerManager.progress
    val songs: StateFlow<List<Song>> = MusicPlayerManager.songs
    val queue: StateFlow<List<Song>> = MusicPlayerManager.queue
    val playbackMode: StateFlow<Int> = MusicPlayerManager.playbackMode

    // --- Search & Filters ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow("songs") // songs, artists, albums, playlists, genres, folders, favorites, stats
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    // --- UI Visualizer & Themes ---
    private val sharedPrefs = application.getSharedPreferences("zarate_player_prefs", android.content.Context.MODE_PRIVATE)
    private val _currentTheme = MutableStateFlow(
        run {
            val saved = application.getSharedPreferences("zarate_player_prefs", android.content.Context.MODE_PRIVATE)
                .getString("applied_theme_type", null)
            if (saved != null) {
                try {
                    CellarThemeType.valueOf(saved)
                } catch (e: Exception) {
                    CellarThemeType.COBRE
                }
            } else {
                CellarThemeType.COBRE
            }
        }
    )
    val currentTheme: StateFlow<CellarThemeType> = _currentTheme.asStateFlow()

    private val _visualizerStyle = MutableStateFlow("particles") // wave, particles, spectrum
    val visualizerStyle: StateFlow<String> = _visualizerStyle.asStateFlow()

    // --- Persistent Room Data Streams ---
    val dbFavorites: StateFlow<List<FavoriteSongEntity>> = repository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dbPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dbRecentlyPlayed: StateFlow<List<PlaybackStatEntity>> = repository.recentlyPlayedStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dbTopPlayed: StateFlow<List<PlaybackStatEntity>> = repository.topPlayedStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Selected Playlist Tracks ---
    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val selectedPlaylistSongs: StateFlow<List<PlaylistSongEntity>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) repository.getSongsForPlaylist(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Walkman DSP Settings (with DB persistence) ---
    val bassBoost = MusicPlayerManager.bassBoostLevel
    val vocalClarifier = MusicPlayerManager.vocalClarifierLevel
    val reverbType = MusicPlayerManager.reverbPreset
    val volumeNormalizer = MusicPlayerManager.volumeNormalizer
    val eqBands = MusicPlayerManager.eqBands
    val crossfade = MusicPlayerManager.crossfadeDuration

    init {
        // Initialize Player Manager with default library
        MusicPlayerManager.initialize(application)

        // Load saved custom equalizer preset from database if exists
        viewModelScope.launch {
            repository.equalizerPreset.collectLatest { savedPreset ->
                if (savedPreset != null) {
                    bassBoost.value = savedPreset.bassBoost
                    vocalClarifier.value = savedPreset.vocalClarifier
                    reverbType.value = savedPreset.reverbType
                    volumeNormalizer.value = savedPreset.volumeNormalizer
                    eqBands.value = listOf(
                        savedPreset.band31, savedPreset.band62, savedPreset.band125,
                        savedPreset.band250, savedPreset.band500, savedPreset.band1k,
                        savedPreset.band2k, savedPreset.band4k, savedPreset.band8k,
                        savedPreset.band16k
                    )
                }
            }
        }

        // Keep local database favorites in sync with Player's rapid visual favorite states
        viewModelScope.launch {
            dbFavorites.collectLatest { favList ->
                val idSet = favList.map { "${it.title}_${it.artist}" }.toSet()
                // Sync favorites to MusicPlayerManager set
                songs.value.forEach { song ->
                    val isFav = idSet.contains("${song.title}_${song.artist}")
                    val alreadyFav = MusicPlayerManager.favorites.value.contains(song.id)
                    if (isFav && !alreadyFav) {
                        MusicPlayerManager.toggleFavorite(song.id)
                    } else if (!isFav && alreadyFav) {
                        MusicPlayerManager.toggleFavorite(song.id)
                    }
                }
            }
        }
    }

    // --- UI Action Methods ---
    fun scanInternalStorage(context: android.content.Context) {
        MusicPlayerManager.scanInternalStorage(context)
    }

    fun scanDirectoryTree(context: android.content.Context, treeUri: android.net.Uri) {
        MusicPlayerManager.scanDirectoryTree(context, treeUri)
    }

    fun playFromUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                var title = "Archivo Importado"
                val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val displayName = cursor.getString(nameIndex)
                            if (!displayName.isNullOrBlank()) {
                                title = displayName
                            }
                        }
                    }
                }

                // Remove suffix if any
                if (title.contains('.')) {
                    title = title.substringBeforeLast('.')
                }
                title = title.replace('_', ' ').replace('-', ' ')

                // Try retrieving duration
                var duration = 180000L
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durationStr != null) {
                        duration = durationStr.toLong()
                    }
                    retriever.release()
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Error reading Uri duration", e)
                }

                val song = Song(
                    id = "uri_${uri.hashCode()}",
                    title = title,
                    artist = "Cava Importada",
                    album = "Archivo Externo",
                    duration = duration,
                    path = uri.toString(),
                    isSimulated = false,
                    format = "Hi-Fi MP3",
                    bitrate = "320 kbps",
                    sampleRate = "44.1 kHz / 16-bit",
                    isHiRes = false,
                    year = "Unknown",
                    lyrics = mapOf(
                        0L to "[Archivo de Audio Externo]",
                        5000L to "Disfruta de tu música en Zarate Player Pro."
                    ),
                    genre = "Imported File"
                )

                // Add to general list so it acts as active and doesn't crash on completion or skipping
                val currentSongs = MusicPlayerManager.songs.value.toMutableList()
                if (currentSongs.none { it.path == song.path }) {
                    currentSongs.add(song)
                    MusicPlayerManager.setSongs(currentSongs)
                }

                MusicPlayerManager.play(context, song)
                repository.incrementPlayCount(song.title, song.artist)
                android.widget.Toast.makeText(context, "Reproduciendo: $title", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to play Uri: ", e)
                android.widget.Toast.makeText(context, "Error al reproducir el archivo", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }

    fun setTheme(theme: CellarThemeType) {
        _currentTheme.value = theme
        sharedPrefs.edit().putString("applied_theme_type", theme.name).apply()
    }

    fun setVisualizerStyle(style: String) {
        _visualizerStyle.value = style
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    // --- Playback Controls ---
    fun selectAndPlay(song: Song) {
        viewModelScope.launch {
            MusicPlayerManager.play(getApplication(), song)
            // Increment local play count in statistics
            repository.incrementPlayCount(song.title, song.artist)
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            MusicPlayerManager.pause(getApplication())
        } else {
            MusicPlayerManager.resume(getApplication())
        }
    }

    fun skipNext() {
        MusicPlayerManager.next(getApplication())
    }

    fun skipPrevious() {
        MusicPlayerManager.previous(getApplication())
    }

    fun seekTo(progressMs: Long) {
        MusicPlayerManager.seekTo(progressMs)
    }

    fun togglePlaybackMode() {
        MusicPlayerManager.togglePlaybackMode()
    }

    // --- Favorites DB Operations ---
    fun toggleFavoriteSong(song: Song) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(song.title, song.artist)
            if (isFav) {
                repository.deleteFavorite(song.title, song.artist)
            } else {
                repository.insertFavorite(song.title, song.artist, song.album, song.path)
            }
            // Toggle local lightweight state
            MusicPlayerManager.toggleFavorite(song.id)
        }
    }

    // --- Playlists DB Operations ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createPlaylist(name)
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(
                playlistId = playlistId,
                title = song.title,
                artist = song.artist,
                album = song.album,
                path = song.path,
                duration = song.duration
            )
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, path: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, path)
        }
    }

    // --- DSP Equalizer Operations ---
    fun updateEqBand(index: Int, value: Float) {
        val current = eqBands.value.toMutableList()
        current[index] = value
        eqBands.value = current
        saveCurrentEqSettings()
    }

    fun setBassBoost(value: Int) {
        bassBoost.value = value
        saveCurrentEqSettings()
    }

    fun setVocalClarifier(value: Int) {
        vocalClarifier.value = value
        saveCurrentEqSettings()
    }

    fun setReverb(preset: String) {
        reverbType.value = preset
        saveCurrentEqSettings()
    }

    fun setVolumeNormalizer(enabled: Boolean) {
        volumeNormalizer.value = enabled
        saveCurrentEqSettings()
    }

    private fun saveCurrentEqSettings() {
        viewModelScope.launch {
            val bands = eqBands.value
            repository.saveEqualizer(
                EqualizerPresetEntity(
                    band31 = bands.getOrElse(0) { 0f },
                    band62 = bands.getOrElse(1) { 0f },
                    band125 = bands.getOrElse(2) { 0f },
                    band250 = bands.getOrElse(3) { 0f },
                    band500 = bands.getOrElse(4) { 0f },
                    band1k = bands.getOrElse(5) { 0f },
                    band2k = bands.getOrElse(6) { 0f },
                    band4k = bands.getOrElse(7) { 0f },
                    band8k = bands.getOrElse(8) { 0f },
                    band16k = bands.getOrElse(9) { 0f },
                    reverbType = reverbType.value,
                    bassBoost = bassBoost.value,
                    vocalClarifier = vocalClarifier.value,
                    volumeNormalizer = volumeNormalizer.value
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure audio stops if the VM is destroyed
        MusicPlayerManager.shutdown()
    }
}
