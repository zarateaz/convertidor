package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.example.playback.MusicPlayerManager
import com.example.playback.MusicViewModel
import com.example.playback.Song
import com.example.ui.components.HolographicVisualizer
import com.example.ui.theme.LocalCyberCellarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberCellarColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val playbackMode by viewModel.playbackMode.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()

    var showQueueDialog by remember { mutableStateOf(false) }

    // Floating vinyl rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val visualizerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 1800 else 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_visualizer_phase"
    )

    val visualizerPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 420 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vinyl_visualizer_pulse"
    )

    val activeSong = currentSong

    Box(modifier = modifier.fillMaxSize()) {
        // Background Visualizer Layer (Holographic wood barrel)
        HolographicVisualizer(
            isPlaying = isPlaying,
            style = visualizerStyle,
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent overlay for glassmorphic depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundBase.copy(alpha = 0.45f))
        )

        // Main Scrollable Control panel
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Visualizer Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Visualizer toggle
                IconButton(
                    onClick = {
                        val nextStyle = when (visualizerStyle) {
                            "particles" -> "wave"
                            "wave" -> "spectrum"
                            else -> "particles"
                        }
                        viewModel.setVisualizerStyle(nextStyle)
                    },
                    modifier = Modifier.testTag("visualizer_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Cambiar visualizador",
                        tint = colors.primaryNeon
                    )
                }

                Text(
                    text = "REPRODUCCIÓN PREMIUM",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )

                // Right: Queue Toggle
                IconButton(
                    onClick = { showQueueDialog = true },
                    modifier = Modifier.testTag("queue_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Cola de reproducción",
                        tint = colors.secondaryAccent
                    )
                }
            }

            if (activeSong == null) {
                // Empty state inside playing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = colors.secondaryAccent,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SELECCIONE UNA CANCIÓN DE LA CAVA",
                            color = colors.primaryNeon,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // --- NOW PLAYING ACTIVE CONTENT ---

                // 1. Simple Square Album Cover with Theme Borders
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .testTag("album_art_container")
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 2.dp,
                            color = colors.primaryNeon,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.ui.components.AlbumArt(
                        path = activeSong.path,
                        context = androidx.compose.ui.platform.LocalContext.current,
                        fallbackTint = colors.primaryNeon
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Song & Artist Headers + High-Res indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeSong.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeSong.artist} — ${activeSong.album} (${activeSong.year})",
                            color = colors.primaryNeon.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Favorite Button
                    IconButton(
                        onClick = { viewModel.toggleFavoriteSong(activeSong) },
                        modifier = Modifier.testTag("heart_favorite_nowplaying")
                    ) {
                        val isFav = MusicPlayerManager.favorites.value.contains(activeSong.id)
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFav) colors.primaryNeon else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Sony Hi-Res Tech Spec panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(colors.cellarMetal.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, colors.secondaryAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeSong.isHiRes) {
                                Box(
                                    modifier = Modifier
                                        .background(colors.secondaryAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(0.8.dp, colors.secondaryAccent, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Hi-Res AUDIO",
                                        color = colors.secondaryAccent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "${activeSong.format} | ${activeSong.bitrate}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = activeSong.sampleRate,
                            color = colors.primaryNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3. Dual scrolling Synchronised Lyrics panel (Walkman style)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.secondaryAccent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    SynchronizedLyricsView(activeSong = activeSong, progress = progress)
                }

                // 4. Scrubbing Timeline Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    com.example.ui.components.FuturisticSlider(
                        value = progress.toFloat().coerceIn(0f, activeSong.duration.toFloat()),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..activeSong.duration.toFloat(),
                        primaryColor = colors.primaryNeon,
                        secondaryColor = colors.secondaryAccent,
                        modifier = Modifier.testTag("scrubbing_slider")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Current time progress
                        Text(
                            text = formatMsToTime(progress),
                            color = colors.primaryNeon,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        // Remaining time
                        Text(
                            text = "-" + formatMsToTime(activeSong.duration - progress),
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Tactile Master Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Mode Toggle
                    IconButton(
                        onClick = { viewModel.togglePlaybackMode() },
                        modifier = Modifier.testTag("shuffle_toggle_btn")
                    ) {
                        val shuffleOn = playbackMode == 1
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (shuffleOn) colors.primaryNeon else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Skip Previous
                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("skip_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Master Play/Pause with custom glowing background
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(colors.primaryNeon, colors.secondaryAccent)
                                )
                            )
                            .clickable { viewModel.togglePlayPause() }
                            .testTag("play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Skip Next
                    IconButton(
                        onClick = { viewModel.skipNext() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("skip_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Repeat Mode Toggle
                    IconButton(
                        onClick = { viewModel.togglePlaybackMode() },
                        modifier = Modifier.testTag("repeat_toggle_btn")
                    ) {
                        val isRepeatAll = playbackMode == 2
                        val isRepeatOne = playbackMode == 3
                        val repColor = if (isRepeatAll || isRepeatOne) colors.primaryNeon else Color.Gray
                        val repIcon = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat

                        Icon(
                            imageVector = repIcon,
                            contentDescription = "Repetir",
                            tint = repColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // --- Active Queue overlay ---
    if (showQueueDialog) {
        val activeQueue by viewModel.queue.collectAsState()
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = {
                Text(
                    "COLA DE REPRODUCCIÓN EN CURSO",
                    color = colors.primaryNeon,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Lista de próximas pistas de la cava:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.height(250.dp)) {
                        itemsIndexed(activeQueue) { idx, song ->
                            val isThis = song.id == activeSong?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectAndPlay(song)
                                        showQueueDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        color = if (isThis) colors.primaryNeon else Color.Gray,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = song.title,
                                            color = if (isThis) colors.primaryNeon else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isThis) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "Playing", tint = colors.primaryNeon, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(text = song.getFormattedDuration(), color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Divider(color = Color.DarkGray.copy(alpha = 0.3f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("CERRAR COLA", color = colors.secondaryAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            containerColor = colors.cellarMetal
        )
    }
}

@Composable
fun SynchronizedLyricsView(activeSong: Song, progress: Long) {
    val colors = LocalCyberCellarColors.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val lyricsList = activeSong.lyrics.entries.sortedBy { it.key }

    // Find active lyric index
    var activeIndex by remember { mutableStateOf(-1) }
    var previousActiveIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(progress) {
        var foundIndex = -1
        for (i in lyricsList.indices) {
            if (progress >= lyricsList[i].key) {
                foundIndex = i
            } else {
                break
            }
        }
        activeIndex = foundIndex
    }

    // Scroll to active lyric line dynamically
    LaunchedEffect(activeIndex) {
        if (activeIndex != -1 && activeIndex != previousActiveIndex) {
            previousActiveIndex = activeIndex
            coroutineScope.launch {
                listState.animateScrollToItem(activeIndex)
            }
        }
    }

    if (lyricsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "[Letras Sincronizadas No Disponibles]",
                color = Color.Gray,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 40.dp)
        ) {
            itemsIndexed(lyricsList) { idx, entry ->
                val isActive = idx == activeIndex
                val alpha = if (isActive) 1f else 0.35f
                val fontSize = if (isActive) 14.sp else 12.sp
                val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal

                Text(
                    text = entry.value,
                    color = if (isActive) colors.primaryNeon else Color.White,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(if (isActive) 1.05f else 1f)
                        .padding(horizontal = 6.dp),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// Converts millisecond progress to mm:ss format
fun formatMsToTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
