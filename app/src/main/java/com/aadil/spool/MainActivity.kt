package com.aadil.spool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aadil.shared.SharedApp
import com.aadil.spool.notifications.scheduleLowStockChecks

class MainActivity : ComponentActivity() {

    private val deepLinkSpoolId = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        scheduleLowStockChecks(this)
        setContent {
            SharedApp(initialSpoolId = deepLinkSpoolId.value)
        }
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data?.takeIf { it.scheme == "spool" } ?: return
        // spool://23 puts the id in the host, not the path.
        val id = data.host?.toIntOrNull() ?: data.lastPathSegment?.toIntOrNull() ?: return
        deepLinkSpoolId.value = id
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
