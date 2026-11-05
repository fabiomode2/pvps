package dev.pvps

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import dev.pvps.ui.components.PvpsMark
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.pvps.service.AppNotifications
import dev.pvps.ui.AppRoot
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.AppViewModelFactory
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.PvpsTheme
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory((application as PvpsApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val appearance = viewModel.settings.collectAsStateValue()
            PvpsTheme(
                themeId = appearance.uiTheme,
                fontScale = appearance.fontScale,
                uiScale = appearance.uiScale
            ) {
                AppShell(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val sessionId = intent?.getStringExtra(AppNotifications.EXTRA_SESSION_ID) ?: return
        viewModel.requestOpenSession(sessionId)
        intent.removeExtra(AppNotifications.EXTRA_SESSION_ID)
    }

    override fun onResume() {
        super.onResume()
        AppNotifications.appVisible = true
        viewModel.onResume()
    }

    override fun onStart() {
        super.onStart()
        AppNotifications.appVisible = true
    }

    override fun onStop() {
        AppNotifications.appVisible = false
        super.onStop()
    }
}

@Composable
private fun AppShell(viewModel: AppViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = (view.context as? Activity) ?: (context as? Activity)
    val settings = viewModel.settings.collectAsStateValue()
    var unlocked by remember { mutableStateOf(!settings.biometricLock) }
    var keyguardUnavailable by remember { mutableStateOf(false) }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) unlocked = true
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(settings.biometricLock) {
        if (!settings.biometricLock) unlocked = true
    }
    LaunchedEffect(settings.blockScreenshots) {
        if (settings.blockScreenshots) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestUnlock() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val intent: Intent? = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock PVPS",
            "Authenticate to access your SSH hosts and keys"
        )
        if (intent != null) {
            credentialLauncher.launch(intent)
        } else {
            keyguardUnavailable = true
        }
    }

    if (settings.biometricLock && !unlocked) {
        Surface(color = DeepBackground, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PvpsMark(64.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "PVPS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Locked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { requestUnlock() }) { Text("Unlock with device credentials") }
                if (keyguardUnavailable) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No device PIN/pattern is configured. Set one in Android settings or disable Device lock in PVPS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    } else {
        AppRoot(viewModel)
    }
}
