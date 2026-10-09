package com.cielfit.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dark-first palette. Background near-black so the gradient rings carry the color.
val Bg = Color(0xFF07070C)
val Surface = Color(0xFF12121A)
val SurfaceHigh = Color(0xFF1C1C27)
val Outline = Color(0xFF2A2A38)

// Macro identity colors
val CalorieOrange = Color(0xFFFF6B35)
val CalorieAmber = Color(0xFFFFB020)
val ProteinBlue = Color(0xFF4D8DFF)
val CarbsTeal = Color(0xFF00D9C0)
val FatPurple = Color(0xFFB06BFF)
val WaterBlue = Color(0xFF3DC5F0)
val SuccessGreen = Color(0xFF00E5A0)
val DangerRed = Color(0xFFFF4D6D)

// Gradient ring stops
val RingColors = listOf(CalorieAmber, CalorieOrange, Color(0xFFFF3D71))
val ProteinRing = listOf(Color(0xFF7AB0FF), ProteinBlue)
val CarbsRing = listOf(Color(0xFF6BFFE0), CarbsTeal)
val FatRing = listOf(Color(0xFFD3A3FF), FatPurple)
val WaterRing = listOf(Color(0xFF7FDFFF), WaterBlue)