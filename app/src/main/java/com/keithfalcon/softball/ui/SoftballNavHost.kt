package com.keithfalcon.softball.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Placeholder — real navigation lands with the screens in later stages.
@Composable
fun SoftballNavHost() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Keith's Softball Assistant")
    }
}
