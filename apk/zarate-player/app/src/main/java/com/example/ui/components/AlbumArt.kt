package com.example.ui.components

import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumArt(path: String, context: android.content.Context, modifier: Modifier = Modifier, fallbackTint: Color = Color.Gray) {
    val bitmap = remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (path.startsWith("content://")) {
                    retriever.setDataSource(context, android.net.Uri.parse(path))
                } else {
                    retriever.setDataSource(path)
                }
                val art = retriever.embeddedPicture
                if (art != null) {
                    val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                    bitmap.value = bmp?.asImageBitmap()
                }
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    if (bitmap.value != null) {
        Image(
            bitmap = bitmap.value!!,
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = fallbackTint.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}
