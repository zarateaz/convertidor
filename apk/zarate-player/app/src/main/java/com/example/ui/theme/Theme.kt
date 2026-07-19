package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class CellarThemeType(val displayName: String) {
    COBRE("Cobre Envejecido"),
    TOXICO("Ácido Tóxico"),
    NEON("Ultravioleta Neón"),
    ACERO("Acero Frío"),
    SANGRE("Gótico Vampírico"),
    CYBERPUNK("Sinfonía Cyberpunk"),
    FANTASMA("Espectro Fantasma"),
    SOLAR("Fuego Solar")
}

data class CyberCellarColors(
    val primaryNeon: Color,
    val secondaryAccent: Color,
    val cellarWood: Color,
    val cellarMetal: Color,
    val backgroundBase: Color,
    val onBackgroundNeon: Color,
    val isDark: Boolean = true
)

val LocalCyberCellarColors = staticCompositionLocalOf {
    CyberCellarColors(
        primaryNeon = WarmAmber,
        secondaryAccent = AgedCopper,
        cellarWood = Color(0xFF381A0B),
        cellarMetal = IronPlate,
        backgroundBase = CarbonBlack,
        onBackgroundNeon = WarmAmber
    )
}

private val CobreCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFFFF9E00), // Super electric glowing amber
    secondaryAccent = Color(0xFFFF5D00), // Neon flame orange
    cellarWood = Color(0xFF1E0E06),
    cellarMetal = Color(0xFF2E1C12),
    backgroundBase = Color(0xFF0A0503),
    onBackgroundNeon = Color(0xFFFFB732)
)

private val ToxicoCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFF39FF14), // Fluorescent acid green
    secondaryAccent = Color(0xFF00FFCC), // Neon radioactive mint/teal
    cellarWood = Color(0xFF091406),
    cellarMetal = Color(0xFF152A11),
    backgroundBase = Color(0xFF030602),
    onBackgroundNeon = Color(0xFF39FF14)
)

private val NeonCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFFD300FF), // Pure UV purple-pink
    secondaryAccent = Color(0xFFFF007F), // Electric hot magenta
    cellarWood = Color(0xFF110320),
    cellarMetal = Color(0xFF22093D),
    backgroundBase = Color(0xFF06010D),
    onBackgroundNeon = Color(0xFF00FFFF) // Cyberpunk neon cyan
)

private val AceroCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFF00F0FF), // Ultra laser cyan
    secondaryAccent = Color(0xFF0066FF), // Electric plasma blue
    cellarWood = Color(0xFF0F151C),
    cellarMetal = Color(0xFF1B2836),
    backgroundBase = Color(0xFF06090D),
    onBackgroundNeon = Color(0xFFE2EAFC)
)

private val SangreCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFFFF002A), // Deep neon laser red
    secondaryAccent = Color(0xFF800000), // Rich dark crimson
    cellarWood = Color(0xFF1A0202),
    cellarMetal = Color(0xFF330505),
    backgroundBase = Color(0xFF080101),
    onBackgroundNeon = Color(0xFFFF4D4D)
)

private val CyberpunkCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFFFCEE09), // Hyper yellow
    secondaryAccent = Color(0xFFFF007F), // Cyber magenta
    cellarWood = Color(0xFF120224),
    cellarMetal = Color(0xFF2B0947),
    backgroundBase = Color(0xFF080112),
    onBackgroundNeon = Color(0xFF00FFCC)
)

private val FantasmaCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFF00FFD5), // Neon ghost specter
    secondaryAccent = Color(0xFF2BFF00), // Eerie bright green
    cellarWood = Color(0xFF041014),
    cellarMetal = Color(0xFF0C242B),
    backgroundBase = Color(0xFF02070A),
    onBackgroundNeon = Color(0xFFDFFFFF)
)

private val SolarCellarColors = CyberCellarColors(
    primaryNeon = Color(0xFFFF4000), // Supercharged solar orange
    secondaryAccent = Color(0xFFFFC000), // Radiant solar golden yellow
    cellarWood = Color(0xFF200500),
    cellarMetal = Color(0xFF400F00),
    backgroundBase = Color(0xFF0D0200),
    onBackgroundNeon = Color(0xFFFFDD00)
)

@Composable
fun ZaratePlayerTheme(
    themeType: CellarThemeType = CellarThemeType.COBRE,
    content: @Composable () -> Unit
) {
    val cellarColors = when (themeType) {
        CellarThemeType.COBRE -> CobreCellarColors
        CellarThemeType.TOXICO -> ToxicoCellarColors
        CellarThemeType.NEON -> NeonCellarColors
        CellarThemeType.ACERO -> AceroCellarColors
        CellarThemeType.SANGRE -> SangreCellarColors
        CellarThemeType.CYBERPUNK -> CyberpunkCellarColors
        CellarThemeType.FANTASMA -> FantasmaCellarColors
        CellarThemeType.SOLAR -> SolarCellarColors
    }

    val materialColorScheme = darkColorScheme(
        primary = cellarColors.primaryNeon,
        secondary = cellarColors.secondaryAccent,
        background = cellarColors.backgroundBase,
        surface = cellarColors.cellarMetal,
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )

    CompositionLocalProvider(
        LocalCyberCellarColors provides cellarColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}

