package com.sesos.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import com.sesos.app.ui.AppShell

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val search: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT <= 28) {
                    askStorage.launch(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    )
                }
            }
            AppShell(vm, search)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            UrlUtils.extract(i.getStringExtra(Intent.EXTRA_TEXT))?.let { vm.onSharedUrl(it) }
        }
    }
}
