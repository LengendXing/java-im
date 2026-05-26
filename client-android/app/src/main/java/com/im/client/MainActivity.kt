package com.im.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.im.client.ui.navigation.ImNavGraph
import com.im.client.ui.theme.IMClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IMClientTheme {
                ImNavGraph()
            }
        }
    }
}
