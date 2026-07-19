package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.playback.MusicViewModel
import com.example.ui.screens.MainContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core ViewModel Setup
        val viewModel = ViewModelProvider(this)[MusicViewModel::class.java]

        setContent {
            MainContainer(viewModel = viewModel)
        }
    }
}
