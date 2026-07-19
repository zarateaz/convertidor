package com.example.playback

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.sin

object MusicPlayerManager {
    private const val TAG = "MusicPlayerManager"

    // --- State Streams ---
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet()) // set of song IDs
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    // --- Walkman DSP Effects State ---
    val bassBoostLevel = MutableStateFlow(30) // 0 - 100
    val vocalClarifierLevel = MutableStateFlow(20) // 0 - 100
    val reverbPreset = MutableStateFlow("Cave") // Room, Hall, Cave, None
    val volumeNormalizer = MutableStateFlow(true)
    val eqBands = MutableStateFlow(List(10) { 0f }) // 10 bands (-12dB to +12dB)
    val crossfadeDuration = MutableStateFlow(3) // 0 to 12s

    // Playback Modes: 0 = SEQUENTIAL, 1 = SHUFFLE, 2 = REPEAT_ALL, 3 = REPEAT_ONE
    private val _playbackMode = MutableStateFlow(0)
    val playbackMode: StateFlow<Int> = _playbackMode.asStateFlow()

    // --- Audio Engines ---
    private var mediaPlayer: MediaPlayer? = null
    private var synthJob: Job? = null
    private var synthTrack: AudioTrack? = null
    private var progressJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --- Simulated High-Res Cellar Playlist ---
    val simulatedSongs = listOf(
        Song(
            id = "sim_chateau_2099",
            title = "Château Cyberpunk 2099",
            artist = "Acid Sommelier",
            album = "Grand Cru Holographic",
            duration = 180000L,
            path = "sim_chateau_2099",
            isSimulated = true,
            format = "MQA / FLAC",
            bitrate = "9216 kbps",
            sampleRate = "352.8 kHz / 24-bit",
            isHiRes = true,
            year = "2099",
            genre = "Subterranean Deep Techno",
            lyrics = mapOf(
                0L to "[Atmospheric hum & Oak cellar reverberations]",
                15000L to "In the digital cellars under Neo-Zarate...",
                30000L to "The copper tubes flow with synthetic wine...",
                45000L to "Beats echoing off toasted oak circuits...",
                60000L to "[Sizzling ultraviolet bass drops]",
                75000L to "Taste the light, hear the aged steel resonance...",
                90000L to "Luxury underground, 2099 reserve.",
                120000L to "[Deep synthesizer chord progression]"
            )
        ),
        Song(
            id = "sim_subter_velvet",
            title = "Subterranean Velvet",
            artist = "Oak Barrel Drone",
            album = "Cava Bass Resonator",
            duration = 240000L,
            path = "sim_subter_velvet",
            isSimulated = true,
            format = "DSD256",
            bitrate = "11.2 MHz",
            sampleRate = "1-bit Direct Stream",
            isHiRes = true,
            year = "2102",
            genre = "Cellar Drone Ambient",
            lyrics = mapOf(
                0L to "[Subsonic 32Hz wine barrel vibrations]",
                20000L to "Copper vines, electric roots...",
                40000L to "Aging in silicon, tasting of ultraviolet...",
                80000L to "Subterranean pressure, perfect vacuum vaults...",
                120000L to "[Vocal filter: Clarified oxygen injection]"
            )
        ),
        Song(
            id = "sim_neon_tannins",
            title = "Neon Tannins",
            artist = "Ultraviolet Grape",
            album = "Synthetic Fermentation",
            duration = 150000L,
            path = "sim_neon_tannins",
            isSimulated = true,
            format = "ALAC",
            bitrate = "4608 kbps",
            sampleRate = "192 kHz / 24-bit",
            isHiRes = true,
            year = "2098",
            genre = "Cyber-Jazz",
            lyrics = mapOf(
                0L to "[Sophisticated copper brass and jazz piano]",
                10000L to "A glass of cybernetic red, glowing warm...",
                30000L to "Reflecting god rays on the steel counter...",
                50000L to "Neon tannins dancing in the bloodstream...",
                80000L to "[Seductive saxophone with bitcrush feedback]"
            )
        ),
        Song(
            id = "sim_toxic_reserve",
            title = "Toxic Reserve 12yo",
            artist = "Rusted Copper Band",
            album = "Acid Cellars",
            duration = 210000L,
            path = "sim_toxic_reserve",
            isSimulated = true,
            format = "FLAC Studio",
            bitrate = "6144 kbps",
            sampleRate = "96 kHz / 24-bit",
            isHiRes = true,
            year = "2095",
            genre = "Acid Industrial",
            lyrics = mapOf(
                0L to "[Industrial pneumatic gate sound opening]",
                15000L to "Corroded barrels leaking green neon light...",
                30000L to "Toxic reserve, brewed in dark luxury...",
                45000L to "Breathe the fumes, feel the copper beat...",
                60000L to "[Glitch step rhythm with high-voltage arcs]"
            )
        )
    )

    private const val PREFS_NAME = "ZaratePlayerPrefs"
    private const val KEY_SAVED_TREES = "SavedTrees"

    fun initialize(context: Context) {
        _songs.value = emptyList()
        _queue.value = emptyList()
        scanInternalStorage(context)
        restoreSavedDirectories(context)
    }

    private fun restoreSavedDirectories(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTrees = prefs.getStringSet(KEY_SAVED_TREES, emptySet()) ?: emptySet()
        savedTrees.forEach { uriStr ->
            try {
                scanDirectoryTree(context, Uri.parse(uriStr), saveUri = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring tree URI: $uriStr", e)
            }
        }
    }

    // --- External Storage Music Scanner ---
    fun scanInternalStorage(context: Context) {
        coroutineScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Song>()

            val resolver: ContentResolver = context.contentResolver
            val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.YEAR
            )
            // Extensive query: query all audio files or files ending with mp3, flac, wav, m4a to be 100% automatic
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR ${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav'"

            try {
                resolver.query(uri, projection, selection, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(pathCol)
                        if (File(path).exists()) {
                            val duration = cursor.getLong(durationCol)
                            if (duration > 5000) { // filter short alert tones
                                val title = cursor.getString(titleCol) ?: "Canción Desconocida"
                                val artist = cursor.getString(artistCol) ?: "Artista Desconocido"
                                val album = cursor.getString(albumCol) ?: "Cava Reserve"
                                val yearStr = cursor.getString(yearCol) ?: "Unknown"
                                
                                val extension = path.substringAfterLast('.', "mp3").uppercase()
                                val format = if (extension == "FLAC" || extension == "M4A" || extension == "WAV") extension else "Hi-Fi MP3"
                                val isHiRes = extension == "FLAC" || extension == "WAV"
                                
                                list.add(
                                    Song(
                                        id = cursor.getLong(idCol).toString(),
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = duration,
                                        path = path,
                                        isSimulated = false,
                                        format = format,
                                        bitrate = if (isHiRes) "1411 kbps" else "320 kbps",
                                        sampleRate = if (isHiRes) "48 kHz / 24-bit" else "44.1 kHz / 16-bit",
                                        isHiRes = isHiRes,
                                        year = yearStr,
                                        lyrics = mapOf(
                                            0L to "[Archivo de Audio Local]",
                                            5000L to "Disfruta de tu música en Zarate Player Pro."
                                        ),
                                        genre = "Local Storage"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning audio storage: ", e)
            }

            // --- Deep Recurse Directory Scanner Fallback ---
            // If some files are not yet in MediaStore, we walk standard storage locations manually!
            try {
                val directoriesToScan = listOf(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    File("/sdcard/Music"),
                    File("/sdcard/Download"),
                    File("/sdcard/Download/Music"),
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                    context.getExternalFilesDir(null)
                )

                val scannedPaths = mutableSetOf<String>()
                // Add paths already found via MediaStore
                list.forEach { scannedPaths.add(it.path) }

                fun recursiveScan(folder: File?) {
                    if (folder == null || !folder.exists() || !folder.isDirectory) return
                    val files = folder.listFiles() ?: return
                    for (file in files) {
                        if (file.isDirectory) {
                            recursiveScan(file)
                        } else if (file.isFile) {
                            val path = file.absolutePath
                            val nameLower = file.name.lowercase()
                            if ((nameLower.endsWith(".mp3") || nameLower.endsWith(".flac") || nameLower.endsWith(".wav") || nameLower.endsWith(".m4a")) && !scannedPaths.contains(path)) {
                                scannedPaths.add(path)
                                if (list.none { it.path == path }) {
                                    val title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
                                    val duration = 180000L // default 3 mins
                                    val extension = file.extension.uppercase()
                                    val format = if (extension == "FLAC" || extension == "M4A" || extension == "WAV") extension else "Hi-Fi MP3"
                                    val isHiRes = extension == "FLAC" || extension == "WAV"
                                    list.add(
                                        Song(
                                            id = "manual_${file.hashCode()}",
                                            title = title,
                                            artist = "Cava Local",
                                            album = "Direct Scanner",
                                            duration = duration,
                                            path = path,
                                            isSimulated = false,
                                            format = format,
                                            bitrate = if (isHiRes) "1411 kbps" else "320 kbps",
                                            sampleRate = if (isHiRes) "48 kHz / 24-bit" else "44.1 kHz / 16-bit",
                                            isHiRes = isHiRes,
                                            year = "Unknown",
                                            lyrics = mapOf(
                                                0L to "[Archivo de Audio Local Directo]",
                                                5000L to "Disfruta de tu música en Zarate Player Pro."
                                            ),
                                            genre = "Manual Scan"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                for (dir in directoriesToScan) {
                    recursiveScan(dir)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing manual deep recursive scan fallback: ", e)
            }

            withContext(Dispatchers.Main) {
                _songs.value = list
                // Re-populate queue if it was empty or default
                _queue.value = list
            }
        }
    }

    // --- Directory Tree Scanner (SAF) ---
    fun scanDirectoryTree(context: Context, treeUri: Uri, saveUri: Boolean = true) {
        if (saveUri) {
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedTrees = prefs.getStringSet(KEY_SAVED_TREES, emptySet())?.toMutableSet() ?: mutableSetOf()
                savedTrees.add(treeUri.toString())
                prefs.edit().putStringSet(KEY_SAVED_TREES, savedTrees).apply()
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to take persistable URI permission", e)
            }
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            val list = _songs.value.toMutableList()
            val existingPaths = list.map { it.path }.toSet()
            
            try {
                val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                if (root != null && root.isDirectory) {
                    val contentResolver = context.contentResolver
                    
                    fun recursiveDocumentScan(dir: androidx.documentfile.provider.DocumentFile) {
                        for (file in dir.listFiles()) {
                            if (file.isDirectory) {
                                recursiveDocumentScan(file)
                            } else if (file.isFile) {
                                val nameLower = file.name?.lowercase() ?: ""
                                val uriString = file.uri.toString()
                                
                                if ((nameLower.endsWith(".mp3") || nameLower.endsWith(".flac") || nameLower.endsWith(".wav") || nameLower.endsWith(".m4a")) && !existingPaths.contains(uriString)) {
                                    val title = file.name?.substringBeforeLast('.')?.replace('_', ' ')?.replace('-', ' ') ?: "Audio Importado"
                                    val extension = nameLower.substringAfterLast('.', "mp3").uppercase()
                                    val format = if (extension == "FLAC" || extension == "M4A" || extension == "WAV") extension else "Hi-Fi MP3"
                                    val isHiRes = extension == "FLAC" || extension == "WAV"
                                    
                                    list.add(
                                        Song(
                                            id = "tree_${file.uri.hashCode()}",
                                            title = title,
                                            artist = "Cava Importada",
                                            album = "Carpeta Externa",
                                            duration = 180000L, // default 3 mins if duration is unreadable
                                            path = uriString,
                                            isSimulated = false,
                                            format = format,
                                            bitrate = if (isHiRes) "1411 kbps" else "320 kbps",
                                            sampleRate = if (isHiRes) "48 kHz / 24-bit" else "44.1 kHz / 16-bit",
                                            isHiRes = isHiRes,
                                            year = "Unknown",
                                            lyrics = mapOf(
                                                0L to "[Audio Importado de Carpeta]",
                                                5000L to "Disfruta de tu música en Zarate Player Pro."
                                            ),
                                            genre = "Folder Import"
                                        )
                                    )
                                }
                            }
                        }
                    }
                    recursiveDocumentScan(root)
                    
                    withContext(Dispatchers.Main) {
                        _songs.value = list
                        _queue.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning directory tree: ", e)
            }
        }
    }

    // --- Playback Management ---
    fun play(context: Context, song: Song) {
        stopEngines()
        _currentSong.value = song
        _isPlaying.value = true
        _progress.value = 0L

        if (song.isSimulated) {
            startSynthesizerEngine(song)
        } else {
            startMediaPlayerEngine(context, song)
        }

        startProgressTracker(context)
        updateNotificationService(context, "PLAY")
    }

    fun pause(context: Context) {
        _isPlaying.value = false
        if (_currentSong.value?.isSimulated == true) {
            pauseSynthesizerEngine()
        } else {
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing MediaPlayer: ", e)
            }
        }
        updateNotificationService(context, "PAUSE")
    }

    fun resume(context: Context) {
        val song = _currentSong.value ?: return
        _isPlaying.value = true
        if (song.isSimulated) {
            resumeSynthesizerEngine(song)
        } else {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming MediaPlayer: ", e)
            }
        }
        updateNotificationService(context, "PLAY")
    }

    fun next(context: Context) {
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value
        val index = q.indexOf(current)
        val nextSong = when (playbackMode.value) {
            1 -> q.random() // Shuffle
            else -> {
                val nextIndex = (index + 1) % q.size
                q[nextIndex]
            }
        }
        play(context, nextSong)
    }

    fun previous(context: Context) {
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value
        val index = q.indexOf(current)
        val prevIndex = if (index - 1 < 0) q.size - 1 else index - 1
        play(context, q[prevIndex])
    }

    fun seekTo(progressMs: Long) {
        _progress.value = progressMs
        val song = _currentSong.value ?: return
        if (!song.isSimulated) {
            try {
                mediaPlayer?.seekTo(progressMs.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "Seek error: ", e)
            }
        }
    }

    fun togglePlaybackMode() {
        // 0 = SEQ, 1 = SHUFFLE, 2 = REPEAT_ALL, 3 = REPEAT_ONE
        _playbackMode.value = (_playbackMode.value + 1) % 4
    }

    fun setQueue(newQueue: List<Song>) {
        _queue.value = newQueue
    }

    fun setSongs(newSongs: List<Song>) {
        _songs.value = newSongs
        _queue.value = newSongs
    }

    fun toggleFavorite(songId: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _favorites.value = current
    }

    // --- Native Atmospheric Synthesizer Sound Engine ---
    // Generates ambient drone frequencies mimicking aging oak barrels and digital pressure
    private fun startSynthesizerEngine(song: Song) {
        synthJob = coroutineScope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            synthTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize.coerceAtLeast(8192),
                AudioTrack.MODE_STREAM
            )

            try {
                synthTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack play error: ", e)
                return@launch
            }

            // Synth frequencies based on wine song choice
            val baseFreq = when (song.id) {
                "sim_chateau_2099" -> 110.0 // A2 (warm deep)
                "sim_subter_velvet" -> 73.4 // D2 (deep subsonic rumble)
                "sim_neon_tannins" -> 146.8 // D3 (velvet cello register)
                "sim_toxic_reserve" -> 92.5 // F#2 (corroded electric edge)
                else -> 110.0
            }

            val buffer = ShortArray(4096)
            var angle = 0.0
            var subAngle = 0.0

            while (isActive && _isPlaying.value) {
                for (i in buffer.indices) {
                    // Modulation based on xBass and Equalizer settings
                    val bassMultiplier = 1.0 + (bassBoostLevel.value / 100.0)
                    val vocalMultiplier = 1.0 + (vocalClarifierLevel.value / 100.0)

                    // Wave 1: Pure Sine Base
                    val wave1 = sin(angle)
                    
                    // Wave 2: Subharmonic (1 octave lower, beefed up by Bass Boost)
                    val wave2 = sin(subAngle) * 0.7 * bassMultiplier
                    
                    // Wave 3: Holographic shimmer (Higher overtone, brightened by voice/EQ)
                    val wave3 = sin(angle * 3.0) * 0.15 * vocalMultiplier

                    // Fused drone signal with ambient wind/crackle simulation
                    var signal = (wave1 + wave2 + wave3) / 3.0
                    
                    // Add micro-crackle noise representing ancient cellar barrels
                    if (Math.random() > 0.992) {
                        signal += (Math.random() - 0.5) * 0.05
                    }

                    // Normalize & Clamp to fit PCM 16-bit
                    val sample = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    buffer[i] = sample

                    // Frequencies with phase sweep (LFO-like modulation)
                    val lfo = 1.0 + 0.005 * sin(angle * 0.0001)
                    angle += 2.0 * Math.PI * (baseFreq * lfo) / sampleRate
                    subAngle += 2.0 * Math.PI * ((baseFreq / 2.0) * lfo) / sampleRate
                }
                synthTrack?.write(buffer, 0, buffer.size)
            }
        }
    }

    private fun pauseSynthesizerEngine() {
        synthJob?.cancel()
        synthJob = null
        try {
            synthTrack?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing synthTrack: ", e)
        }
    }

    private fun resumeSynthesizerEngine(song: Song) {
        startSynthesizerEngine(song)
    }

    // --- Android MediaPlayer Engine ---
    private fun startMediaPlayerEngine(context: Context, song: Song) {
        try {
            mediaPlayer = MediaPlayer().apply {
                val uri = if (song.path.startsWith("content://")) {
                    Uri.parse(song.path)
                } else {
                    Uri.fromFile(File(song.path))
                }
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener {
                    handleSongCompletion(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaPlayer: ", e)
        }
    }

    private fun handleSongCompletion(context: Context) {
        when (playbackMode.value) {
            3 -> { // Repeat One
                val current = _currentSong.value
                if (current != null) play(context, current)
            }
            else -> {
                next(context)
            }
        }
    }

    // --- Tracking Progression Timer ---
    private fun startProgressTracker(context: Context) {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive) {
                delay(1000)
                val current = _currentSong.value ?: break
                if (_isPlaying.value) {
                    val nextProgress = _progress.value + 1000L
                    if (nextProgress >= current.duration) {
                        _progress.value = current.duration
                        handleSongCompletion(context)
                    } else {
                        _progress.value = nextProgress
                    }
                }
            }
        }
    }

    private fun stopEngines() {
        synthJob?.cancel()
        synthJob = null
        try {
            synthTrack?.stop()
            synthTrack?.release()
        } catch (e: Exception) {
            // silent catch
        }
        synthTrack = null

        progressJob?.cancel()
        progressJob = null

        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer on stopEngines: ", e)
        }
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer on stopEngines: ", e)
        }
        mediaPlayer = null
    }

    fun shutdown() {
        stopEngines()
        coroutineScope.cancel()
    }

    private fun updateNotificationService(context: Context, action: String) {
        val intent = android.content.Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MusicPlaybackService for notification: ", e)
        }
    }
}
