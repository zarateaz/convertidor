package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.MusicViewModel
import com.example.ui.theme.LocalCyberCellarColors

@Composable
fun EqualizerSheet(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val colors = LocalCyberCellarColors.current
    val eqValues by viewModel.eqBands.collectAsState()
    val bassBoostVal by viewModel.bassBoost.collectAsState()
    val vocalVal by viewModel.vocalClarifier.collectAsState()
    val reverbSelected by viewModel.reverbType.collectAsState()
    val normalizerVal by viewModel.volumeNormalizer.collectAsState()

    val bandsList = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(2.dp, colors.secondaryAccent.copy(alpha = 0.4f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .testTag("equalizer_card"),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundBase.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.secondaryAccent.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Equalizador",
                        tint = colors.primaryNeon,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SONY DSP AUDIO CORE",
                        color = colors.primaryNeon,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_eq_button")
                ) {
                    Text("CERRAR COMPUERTA", color = colors.secondaryAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Divider(color = colors.secondaryAccent.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

            // 1. 10-Band EQ Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ECUALIZADOR DE 10 BANDAS",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Modo: Cava-Sintonizado",
                    color = colors.primaryNeon.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 10-Band EQ Horizontal Scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(colors.cellarMetal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .border(1.dp, colors.secondaryAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(bandsList) { index, hz ->
                        val bandVal = eqValues.getOrElse(index) { 0f }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                        ) {
                            Text(
                                text = "${bandVal.toInt()}dB",
                                color = colors.primaryNeon,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Slider(
                                value = bandVal,
                                onValueChange = { viewModel.updateEqBand(index, it) },
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("eq_slider_$hz"),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.primaryNeon,
                                    activeTrackColor = colors.primaryNeon.copy(alpha = 0.8f),
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )

                            Text(
                                text = hz,
                                color = colors.secondaryAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Rotary Slider Effects (xBass, Vocal, Reverb)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // xBass (Bass Boost) Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.cellarMetal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.secondaryAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "x-BASS BOOST",
                        color = colors.secondaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = bassBoostVal.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.secondaryAccent,
                            activeTrackColor = colors.secondaryAccent
                        ),
                        modifier = Modifier.testTag("xbass_slider")
                    )
                    Text(
                        text = "${bassBoostVal}% INTENSIDAD",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Vocal Clarifier Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.cellarMetal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.secondaryAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "CLARIFICADOR VOZ",
                        color = colors.secondaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = vocalVal.toFloat(),
                        onValueChange = { viewModel.setVocalClarifier(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.secondaryAccent,
                            activeTrackColor = colors.secondaryAccent
                        ),
                        modifier = Modifier.testTag("voice_slider")
                    )
                    Text(
                        text = "${vocalVal}% FILTRADO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reverb selector and Volume Normalization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reverb Selector
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.cellarMetal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.secondaryAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "REVERBERACIÓN DEL ALMA",
                        color = colors.secondaryAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val reverbOptions = listOf("None", "Room", "Large Hall", "Cave")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        reverbOptions.forEach { opt ->
                            val isSel = reverbSelected == opt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) colors.primaryNeon else Color.Transparent)
                                    .clickable { viewModel.setReverb(opt) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("reverb_opt_$opt")
                            ) {
                                Text(
                                    text = opt.uppercase(),
                                    color = if (isSel) Color.Black else Color.Gray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Volume Normalization Selector
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.cellarMetal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.secondaryAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "NORMALIZACIÓN",
                            color = colors.secondaryAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Nivelar Ganancia",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )
                    }

                    Switch(
                        checked = normalizerVal,
                        onCheckedChange = { viewModel.setVolumeNormalizer(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primaryNeon,
                            checkedTrackColor = colors.primaryNeon.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.scale(0.8f).testTag("normalizer_switch")
                    )
                }
            }
        }
    }
}

