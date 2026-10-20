package com.britishcoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.britishcoach.app.ui.CoachApp
import com.britishcoach.app.ui.theme.CoachTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CoachApplication).container
        setContent {
            CoachTheme {
                CoachApp(container)
            }
        }
    }
}
