package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.playback.MusicViewModel
import com.example.playback.Song
import com.example.ui.components.EqualizerSheet
import com.example.ui.theme.CellarThemeType
import com.example.ui.theme.LocalCyberCellarColors
import com.example.ui.theme.ZaratePlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Subscribed states
    val currentTheme by viewModel.currentTheme.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val crossfadeVal by viewModel.crossfade.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()

    // Database states
    val dbRecentlyPlayed by viewModel.dbRecentlyPlayed.collectAsState()
    val dbTopPlayed by viewModel.dbTopPlayed.collectAsState()

    // Nav navigation states
    var activeScreen by remember { mutableStateOf("library") } // library, now_playing

    // Modal Sheet toggle states
    var showEqualizer by remember { mutableStateOf(false) }
    var showStatsView by remember { mutableStateOf(false) }

    // Sleep Timer countdown states
    var sleepMinutesLeft by remember { mutableStateOf(0) }
    var isTimerActive by remember { mutableStateOf(false) }

    // Post notification & read audio permission launcher (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        val storageGranted = results[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted || storageGranted) {
            Toast.makeText(context, "Sincronizando cava con biblioteca interna...", Toast.LENGTH_SHORT).show()
            viewModel.scanInternalStorage(context)
        }
    }

    // Trigger permission requests on start
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.scanInternalStorage(context)
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Explorando carpeta de música...", Toast.LENGTH_SHORT).show()
            viewModel.scanDirectoryTree(context, uri)
        }
    }

    // Sleep Timer countdown thread
    LaunchedEffect(isTimerActive, sleepMinutesLeft) {
        if (isTimerActive && sleepMinutesLeft > 0) {
            delay(60000L) // Wait 1 minute
            sleepMinutesLeft -= 1
            if (sleepMinutesLeft <= 0) {
                isTimerActive = false
                viewModel.togglePlayPause() // Stop playback
                Toast.makeText(context, "Temporizador finalizado. Música pausada.", Toast.LENGTH_LONG).show()
            }
        }
    }

    ZaratePlayerTheme(themeType = currentTheme) {
        val colors = LocalCyberCellarColors.current

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // Cyberpunk Slat Sidebar
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(colors.backgroundBase)
                        .border(1.5.dp, colors.secondaryAccent.copy(alpha = 0.3f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .testTag("drawer_sheet"),
                    drawerContainerColor = colors.backgroundBase,
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        // Title Header
                        Text(
                            text = "ZARATE VINTAGE SYSTEM",
                            color = colors.primaryNeon,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Consola de Configuración Cava v2.99",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Divider(color = colors.secondaryAccent.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                        // A. THEME SELECTOR: "bien añadelle a se pront que el cava cambie con lo temas"
                        Text(
                            text = "ESTILO CYBERPUNK DE CAVA",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        CellarThemeType.values().forEach { type ->
                            val isSel = currentTheme == type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) colors.cellarMetal.copy(alpha = 0.6f) else Color.Transparent)
                                    .border(
                                        0.8.dp,
                                        if (isSel) colors.primaryNeon else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setTheme(type) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("theme_btn_${type.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Miniature color indicator dot
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (type) {
                                                CellarThemeType.COBRE -> Color(0xFFFFBF00)
                                                CellarThemeType.TOXICO -> Color(0xFF39FF14)
                                                CellarThemeType.NEON -> Color(0xFFBF00FF)
                                                CellarThemeType.ACERO -> Color(0xFF00E5FF)
                                                CellarThemeType.SANGRE -> Color(0xFFFF0033)
                                                CellarThemeType.CYBERPUNK -> Color(0xFFFCEE09)
                                                CellarThemeType.FANTASMA -> Color(0xFF00FFCC)
                                                CellarThemeType.SOLAR -> Color(0xFFFF5500)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = type.displayName.uppercase(),
                                    color = if (isSel) colors.primaryNeon else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Divider(color = colors.secondaryAccent.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                        // B. WALKMAN SONY DSP CORE
                        Text(
                            text = "AUDIO DE ALTA DEFINICIÓN SONY",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    showEqualizer = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cellarMetal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.primaryNeon.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .testTag("open_eq_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = colors.primaryNeon)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SINTONIZAR EQ & EFECTOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // C. SLEEP TIMER (TEMPORIZADOR DE APAGADO)
                        Text(
                            text = "TEMPORIZADOR DE APAGADO",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        val timerOptions = listOf(0, 5, 15, 30, 60)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            timerOptions.forEach { mins ->
                                val isTimerSel = sleepMinutesLeft == mins && isTimerActive
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isTimerSel) colors.primaryNeon else colors.cellarMetal.copy(alpha = 0.3f))
                                        .border(0.5.dp, colors.secondaryAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (mins == 0) {
                                                isTimerActive = false
                                                sleepMinutesLeft = 0
                                            } else {
                                                sleepMinutesLeft = mins
                                                isTimerActive = true
                                                Toast
                                                    .makeText(context, "Música se apagará en $mins minutos", Toast.LENGTH_SHORT)
                                                    .show()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("sleep_timer_opt_$mins")
                                ) {
                                    Text(
                                        text = if (mins == 0) "OFF" else "${mins}M",
                                        color = if (isTimerSel) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        if (isTimerActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "BÓVEDA SE APAGARÁ EN: ${sleepMinutesLeft} MINUTOS",
                                color = colors.primaryNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Divider(color = colors.secondaryAccent.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                        // D. CROSSFADE CONFIGURATION
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CROSSFADE GRADUAL",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${crossfadeVal}s",
                                color = colors.primaryNeon,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = crossfadeVal.toFloat(),
                            onValueChange = { viewModel.crossfade.value = it.toInt() },
                            valueRange = 0f..12f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.secondaryAccent,
                                activeTrackColor = colors.secondaryAccent
                            ),
                            modifier = Modifier.testTag("crossfade_slider")
                        )

                        Divider(color = colors.secondaryAccent.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                        // E. HISTORIC STATISTICS BUTTON
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    showStatsView = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cellarMetal.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.secondaryAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .testTag("open_stats_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.QueryStats, contentDescription = null, tint = colors.secondaryAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BÓVEDA DE ESTADÍSTICAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        ) {
            // Main scaffold container
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = colors.primaryNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ZARATE PLAYER",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("menu_drawer_toggle")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = colors.secondaryAccent)
                            }
                        },
                        actions = {
                            // Folder icon to open system file explorer directly
                            IconButton(
                                onClick = { directoryPickerLauncher.launch(null) },
                                modifier = Modifier.testTag("open_file_picker_btn")
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = "Abrir Carpeta de Música",
                                    tint = colors.secondaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.scanInternalStorage(context) },
                                modifier = Modifier.testTag("refresh_storage_btn")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = "Escanear almacenamiento", tint = colors.primaryNeon)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = colors.backgroundBase
                        )
                    )
                },
                bottomBar = {
                    // Custom Cyberpunk Bottom Navigation
                    Column {
                        Divider(color = colors.secondaryAccent.copy(alpha = 0.15f), thickness = 1.dp)
                        NavigationBar(
                            containerColor = colors.backgroundBase,
                            tonalElevation = 10.dp
                        ) {
                        NavigationBarItem(
                            selected = activeScreen == "library",
                            onClick = { activeScreen = "library" },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Biblioteca") },
                            label = { Text("BIBLIOTECA", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = colors.primaryNeon,
                                indicatorColor = colors.primaryNeon,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            ),
                            modifier = Modifier.testTag("nav_library_tab")
                        )

                        NavigationBarItem(
                            selected = activeScreen == "now_playing",
                            onClick = { activeScreen = "now_playing" },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Reproductor") },
                            label = { Text("REPRODUCTOR", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = colors.primaryNeon,
                                indicatorColor = colors.primaryNeon,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            ),
                            modifier = Modifier.testTag("nav_now_playing_tab")
                        )
                    }
                }
            },
            containerColor = colors.backgroundBase
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Screen Switching Animation
                    AnimatedContent(
                        targetState = activeScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "screen_navigation"
                    ) { screen ->
                        when (screen) {
                            "library" -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LibraryScreen(viewModel = viewModel)

                                    // F. Persistent Mini-Player Ribbon when in library mode
                                    if (currentSong != null) {
                                        MiniPlayerRibbon(
                                            song = currentSong!!,
                                            isPlaying = isPlaying,
                                            progress = progress,
                                            colors = colors,
                                            onPlayPauseClick = { viewModel.togglePlayPause() },
                                            onNextClick = { viewModel.skipNext() },
                                            onMiniPlayerClick = { activeScreen = "now_playing" }
                                        )
                                    }
                                }
                            }
                            "now_playing" -> {
                                NowPlayingScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // --- Dialog-sheets ---
        if (showEqualizer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showEqualizer = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(modifier = Modifier.clickable(enabled = false, onClick = {})) {
                    EqualizerSheet(viewModel = viewModel, onDismiss = { showEqualizer = false })
                }
            }
        }

        if (showStatsView) {
            AlertDialog(
                onDismissRequest = { showStatsView = false },
                title = { Text("BÓVEDA HISTÓRICA DE AUDIO", color = colors.primaryNeon, fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Métricas de reproducción sintonizadas:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
                        if (dbTopPlayed.isEmpty()) {
                            Text("[Métricas sin registrar, comienza a escuchar tracks]", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        } else {
                            Text("TEMAS MÁS REPRODUCIDOS", color = colors.secondaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(modifier = Modifier.height(180.dp)) {
                                items(dbTopPlayed) { stat ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = stat.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(text = stat.artist, color = Color.Gray, fontSize = 10.sp)
                                        }
                                        Text(text = "${stat.playCount} repros", color = colors.primaryNeon, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                    Divider(color = Color.DarkGray.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStatsView = false }) {
                        Text("CERRAR COMPUERTA", color = colors.secondaryAccent, fontFamily = FontFamily.Monospace)
                    }
                },
                containerColor = colors.cellarMetal
            )
        }
    }
}

@Composable
fun BoxScope.MiniPlayerRibbon(
    song: Song,
    isPlaying: Boolean,
    progress: Long,
    colors: com.example.ui.theme.CyberCellarColors,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onMiniPlayerClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cellarMetal.copy(alpha = 0.96f))
            .border(1.dp, colors.primaryNeon.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onMiniPlayerClick() }
            .padding(10.dp)
            .testTag("mini_player_ribbon")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = colors.primaryNeon,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} — ${song.format} HI-RES",
                            color = colors.primaryNeon.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(36.dp).testTag("mini_play_pause_btn")) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = onNextClick, modifier = Modifier.size(36.dp).testTag("mini_next_btn")) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tiny linear progress bar representing Walkman precise progression
            val percent = if (song.duration > 0) progress.toFloat() / song.duration else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.DarkGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent)
                        .background(colors.primaryNeon)
                )
            }
        }
    }
}
