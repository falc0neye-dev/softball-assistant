package com.keithfalcon.softball

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.keithfalcon.softball.ui.SoftballNavHost
import com.keithfalcon.softball.ui.theme.SoftballTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SoftballTheme {
                SoftballNavHost()
            }
        }
    }
}
