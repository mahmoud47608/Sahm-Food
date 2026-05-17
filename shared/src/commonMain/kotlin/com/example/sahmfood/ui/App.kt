package com.example.sahmfood.ui

import androidx.compose.runtime.Composable
import com.example.sahmfood.ui.pos.PosScreen
import com.example.sahmfood.ui.theme.SahmTheme

/**
 * Root entry point used by Android `MainActivity` and iOS `MainViewController`.
 *
 * Wires the global theme around the single POS screen.
 */
@Composable
fun App() = SahmTheme { PosScreen() }
