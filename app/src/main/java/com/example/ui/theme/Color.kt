package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// Global mutable theme index state
val currentThemeIndex = mutableStateOf(0)

// Vibrant Brand Accent Palette (Cybernetic Neon)
val NeonCyan = Color(0xFFFF6B00) // Electric Flame Orange (Primary Accent)
val NeonPurple = Color(0xFFFF8800) // Ember Orange
val NeonMagenta = Color(0xFFFF3D00) // Flame Red-Orange
val FlameGold = Color(0xFFFFB300) // Amber Gold Accent

// Dynamic System-Aware Color Accessors based on selected Theme
val SpaceBlack: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFF090214) else Color(0xFFF9F5FC) // Hyper Plasma
            2 -> if (isDark) Color(0xFF030D1A) else Color(0xFFF0F6FC) // Deep Ocean
            3 -> if (isDark) Color(0xFF000000) else Color(0xFFF8F9FA) // Obsidian Dark
            else -> if (isDark) Color(0xFF0F0D0B) else Color(0xFFF8FAFC) // Cybernetic Neon (Default)
        }
    }

val DeepSlate: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFF16092B) else Color(0xFFFFFFFF) // Hyper Plasma
            2 -> if (isDark) Color(0xFF0A1B2E) else Color(0xFFFFFFFF) // Deep Ocean
            3 -> if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF) // Obsidian Dark
            else -> if (isDark) Color(0xFF1E1813) else Color(0xFFFFFFFF) // Cybernetic Neon (Default)
        }
    }

val CyberGray: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFF2A154D) else Color(0xFFF1F5F9)
            2 -> if (isDark) Color(0xFF132F4C) else Color(0xFFF1F5F9)
            3 -> if (isDark) Color(0xFF222222) else Color(0xFFF1F5F9)
            else -> if (isDark) Color(0xFF282019) else Color(0xFFF1F5F9)
        }
    }

val TextPrimary: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFFFFF0FE) else Color(0xFF210033)
            2 -> if (isDark) Color(0xFFE1F5FE) else Color(0xFF011627)
            3 -> if (isDark) Color(0xFFFFFFFF) else Color(0xFF111111)
            else -> if (isDark) Color(0xFFFFF7ED) else Color(0xFF0F172A)
        }
    }

val TextSecondary: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFFA58CBD) else Color(0xFF6E568A)
            2 -> if (isDark) Color(0xFF748C9E) else Color(0xFF4A5C6A)
            3 -> if (isDark) Color(0xFF9E9E9E) else Color(0xFF666666)
            else -> if (isDark) Color(0xFFB8A89A) else Color(0xFF64748B)
        }
    }

val BorderColor: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFF381B57) else Color(0xFFEADDF2)
            2 -> if (isDark) Color(0xFF1B3D5E) else Color(0xFFD0E1F0)
            3 -> if (isDark) Color(0xFF262626) else Color(0xFFE5E5E5)
            else -> if (isDark) Color(0xFF3D3126) else Color(0xFFE2E8F0)
        }
    }

val LightAccent: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xFF3B1540) else Color(0xFFFBE4FF)
            2 -> if (isDark) Color(0xFF0A2E4E) else Color(0xFFE0F2F1)
            3 -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)
            else -> if (isDark) Color(0xFF381F0B) else Color(0xFFFFEDD5)
        }
    }

val GlassBg: Color
    @Composable get() {
        val isDark = isSystemInDarkTheme()
        return when (currentThemeIndex.value) {
            1 -> if (isDark) Color(0xDD16092B) else Color(0xF2FFFFFF)
            2 -> if (isDark) Color(0xDD0A1B2E) else Color(0xF2FFFFFF)
            3 -> if (isDark) Color(0xDD121212) else Color(0xF2FFFFFF)
            else -> if (isDark) Color(0xDD1E1813) else Color(0xF2FFFFFF)
        }
    }



