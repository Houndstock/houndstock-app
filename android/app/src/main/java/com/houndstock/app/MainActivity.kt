package com.houndstock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.houndstock.app.ui.screens.schemes.SchemesListScreen
import com.houndstock.app.ui.theme.HoundstockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoundstockTheme {
                SchemesListScreen()
            }
        }
    }
}
