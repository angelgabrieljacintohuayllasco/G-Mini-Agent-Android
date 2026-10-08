package com.gmini.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gmini.android.ui.GminiApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GminiApp(initialPairingLink = pairingLinkFrom(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun pairingLinkFrom(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return if (data.scheme.equals("gmini", ignoreCase = true)) data.toString() else null
    }
}
