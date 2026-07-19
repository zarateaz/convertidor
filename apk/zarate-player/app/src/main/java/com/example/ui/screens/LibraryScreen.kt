package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.data.PlaylistEntity
import com.example.playback.MusicPlayerManager
import com.example.playback.MusicViewModel
import com.example.playback.Song
import com.example.ui.theme.LocalCyberCellarColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalCyberCellarColors.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.playFromUri(context, uri)
        }
    }

    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val favoritesSet by viewModel.currentSong.collectAsState() // or lightweight favorites
    val favoritesState by MusicViewModel(viewModel.getApplication()).dbFavorites.collectAsState(initial = emptyList())
    val dbPlaylists by viewModel.dbPlaylists.collectAsState()

    var showPlaylistCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedSongForPlaylistId by remember { mutableStateOf<Song?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    // Synchronize UI favorites set
    val localFavoritesList = songs.filter { s ->
        favoritesState.any { it.title == s.title && it.artist == s.artist } || s.id.startsWith("sim_") && MusicPlayerManager.favorites.value.contains(s.id)
    }

    // Filter list based on search query
    val filteredSongs = songs.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.artist.contains(searchQuery, ignoreCase = true) ||
        it.album.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // --- 1. Cyberpunk Search Bar ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("BUSCAR FRECUENCIA MUSICAL...", color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = colors.primaryNeon) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.secondaryAccent)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .testTag("search_bar"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primaryNeon,
                unfocusedBorderColor = colors.secondaryAccent.copy(alpha = 0.4f),
                focusedContainerColor = colors.cellarMetal.copy(alpha = 0.2f),
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // --- 2. Horizontal Category Tabs ---
        val categories = listOf(
            "songs" to "CANCIONES",
            "artists" to "ARTISTAS",
            "albums" to "ÁLBUMES",
            "playlists" to "LISTAS",
            "genres" to "GÉNEROS",
            "favorites" to "FAVORITOS"
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { (key, title) ->
                val isSelected = selectedTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(listOf(colors.primaryNeon, colors.secondaryAccent))
                            } else {
                                Brush.horizontalGradient(listOf(colors.cellarMetal.copy(alpha = 0.5f), colors.cellarMetal.copy(alpha = 0.2f)))
                            }
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.primaryNeon else colors.secondaryAccent.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.setSelectedTab(key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("category_tab_$key")
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.Black else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Dynamic Category Content ---
        when (selectedTab) {
            "songs" -> {
                // System File Explorer direct shortcut
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { filePickerLauncher.launch("audio/*") }
                        .testTag("browse_system_files_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.cellarMetal.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.primaryNeon.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.primaryNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Explorador",
                                tint = colors.primaryNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EXPLORADOR DE ARCHIVOS ANDROID",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Toca para reproducir MP3/FLAC desde cualquier carpeta",
                                color = Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ir",
                            tint = colors.primaryNeon,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                SongListView(filteredSongs, viewModel) { song ->
                    selectedSongForPlaylistId = song
                    showPlaylistPicker = true
                }
            }
            "artists" -> {
                val artistGroups = filteredSongs.groupBy { it.artist }
                ArtistGroupView(artistGroups, viewModel)
            }
            "albums" -> {
                val albumGroups = filteredSongs.groupBy { it.album }
                AlbumGroupView(albumGroups, viewModel)
            }
            "playlists" -> {
                PlaylistGroupView(
                    playlists = dbPlaylists,
                    viewModel = viewModel,
                    onCreatePlaylistClick = { showPlaylistCreateDialog = true }
                )
            }
            "genres" -> {
                val genreGroups = filteredSongs.groupBy { it.genre }
                GenreGroupView(genreGroups, viewModel)
            }
            "favorites" -> {
                if (localFavoritesList.isEmpty()) {
                    EmptyStateView("BÓVEDA DE FAVORITOS VACÍA", "Presiona el ícono de corazón neón en el reproductor para almacenar pistas aquí.")
                } else {
                    SongListView(localFavoritesList, viewModel) { song ->
                        selectedSongForPlaylistId = song
                        showPlaylistPicker = true
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showPlaylistCreateDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistCreateDialog = false },
            title = { Text("CREAR NUEVA CAVA DE MÚSICA", color = colors.primaryNeon, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre de la Lista", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.primaryNeon),
                    modifier = Modifier.testTag("playlist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showPlaylistCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryNeon),
                    modifier = Modifier.testTag("confirm_create_playlist")
                ) {
                    Text("REGISTRAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistCreateDialog = false }) {
                    Text("CANCELAR", color = colors.secondaryAccent)
                }
            },
            containerColor = colors.cellarMetal
        )
    }

    if (showPlaylistPicker) {
        val songToAdd = selectedSongForPlaylistId
        if (songToAdd != null) {
            AlertDialog(
                onDismissRequest = { showPlaylistPicker = false },
                title = { Text("AÑADIR A CAVA DE LISTAS", color = colors.primaryNeon, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Seleccione una lista para guardar:\n${songToAdd.title}", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                        if (dbPlaylists.isEmpty()) {
                            Text(text = "[No tienes listas creadas todavía]", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            LazyColumn(modifier = Modifier.height(180.dp)) {
                                items(dbPlaylists) { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addSongToPlaylist(pl.id, songToAdd)
                                                showPlaylistPicker = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = colors.secondaryAccent)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = pl.name, color = Color.White, fontSize = 13.sp)
                                    }
                                    Divider(color = Color.DarkGray.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaylistPicker = false }) {
                        Text("CERRAR", color = colors.secondaryAccent)
                    }
                },
                containerColor = colors.cellarMetal
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListView(
    songsList: List<Song>,
    viewModel: MusicViewModel,
    onLongClickSong: (Song) -> Unit
) {
    val colors = LocalCyberCellarColors.current
    val activeSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    if (songsList.isEmpty()) {
        EmptyStateView("FRECUENCIA NO HALLADA", "No hay archivos cargados que coincidan con la firma filtrada.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(songsList) { song ->
                val isActive = activeSong?.id == song.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewModel.selectAndPlay(song) },
                            onLongClick = { onLongClickSong(song) }
                        )
                        .testTag("song_item_${song.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) colors.cellarMetal.copy(alpha = 0.5f) else colors.cellarMetal.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = if (isActive) {
                        BorderStroke(1.2.dp, colors.primaryNeon)
                    } else {
                        BorderStroke(0.6.dp, colors.secondaryAccent.copy(alpha = 0.15f))
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom Index or Status Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.backgroundBase.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isActive && isPlaying) {
                                // Animated bar placeholder
                                Icon(Icons.Default.VolumeUp, contentDescription = "Playing", tint = colors.primaryNeon, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.MusicNote, contentDescription = "Song", tint = colors.secondaryAccent, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Títulos y metadatos
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isActive) colors.primaryNeon else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${song.artist} — ${song.album}",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Hi-Res indicator if applicable
                                if (song.isHiRes) {
                                    Box(
                                        modifier = Modifier
                                            .background(colors.secondaryAccent.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                            .border(0.5.dp, colors.secondaryAccent, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "HI-RES",
                                            color = colors.secondaryAccent,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Duration & Action
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = song.getFormattedDuration(),
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = song.format,
                                color = colors.primaryNeon.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Favorite heart toggle
                        IconButton(
                            onClick = { viewModel.toggleFavoriteSong(song) },
                            modifier = Modifier.size(36.dp).testTag("heart_btn_${song.id}")
                        ) {
                            val isFav = MusicPlayerManager.favorites.value.contains(song.id) || viewModel.dbFavorites.collectAsState(emptyList()).value.any { it.title == song.title }
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (isFav) colors.primaryNeon else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistGroupView(
    groups: Map<String, List<Song>>,
    viewModel: MusicViewModel
) {
    val colors = LocalCyberCellarColors.current
    if (groups.isEmpty()) {
        EmptyStateView("SIN ARTISTAS", "No se detectaron frecuencias de artistas.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(groups.keys.toList()) { artist ->
                val songsOfArtist = groups[artist] ?: emptyList()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setSelectedTab("songs")
                            viewModel.setSearchQuery(artist)
                        },
                    colors = CardDefaults.cardColors(containerColor = colors.cellarMetal.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = colors.primaryNeon, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = artist, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${songsOfArtist.size} Temas en Cava", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumGroupView(
    groups: Map<String, List<Song>>,
    viewModel: MusicViewModel
) {
    val colors = LocalCyberCellarColors.current
    if (groups.isEmpty()) {
        EmptyStateView("SIN ÁLBUMES", "No se detectaron firmas de álbumes.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(groups.keys.toList()) { album ->
                val songsOfAlbum = groups[album] ?: emptyList()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setSelectedTab("songs")
                            viewModel.setSearchQuery(album)
                        },
                    colors = CardDefaults.cardColors(containerColor = colors.cellarMetal.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = colors.secondaryAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = album, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "De: ${songsOfAlbum.firstOrNull()?.artist ?: "Varios"}", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreGroupView(
    groups: Map<String, List<Song>>,
    viewModel: MusicViewModel
) {
    val colors = LocalCyberCellarColors.current
    if (groups.isEmpty()) {
        EmptyStateView("SIN GÉNEROS", "No se detectaron filtros de géneros.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(groups.keys.toList()) { genre ->
                val songsOfGenre = groups[genre] ?: emptyList()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setSelectedTab("songs")
                            viewModel.setSearchQuery(genre)
                        },
                    colors = CardDefaults.cardColors(containerColor = colors.cellarMetal.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = colors.primaryNeon, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = genre.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "${songsOfGenre.size} Archivos de Audio", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistGroupView(
    playlists: List<PlaylistEntity>,
    viewModel: MusicViewModel,
    onCreatePlaylistClick: () -> Unit
) {
    val colors = LocalCyberCellarColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onCreatePlaylistClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.primaryNeon, RoundedCornerShape(10.dp))
                .testTag("create_playlist_btn"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = colors.primaryNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "CONSTRUIR NUEVA LISTA CAVA", color = colors.primaryNeon, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            EmptyStateView("SIN LISTAS DE REPRODUCCIÓN", "Personaliza tu cava de música agrupando pistas de alta resolución.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(playlists) { pl ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cellarMetal.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        // Filter songs in library that match this playlist
                                        viewModel.setSelectedTab("songs")
                                        // Just quick list load simulation
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = colors.primaryNeon, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(text = pl.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "Zarate Vault Playlist", color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deletePlaylist(pl.id) },
                                modifier = Modifier.testTag("delete_playlist_${pl.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = colors.secondaryAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(title: String, subtitle: String) {
    val colors = LocalCyberCellarColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = colors.secondaryAccent.copy(alpha = 0.4f),
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            color = colors.primaryNeon,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}
