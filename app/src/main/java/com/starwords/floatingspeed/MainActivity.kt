package com.starwords.floatingspeed

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starwords.floatingspeed.console.ConsoleEvent
import com.starwords.floatingspeed.console.ConsoleScreen
import com.starwords.floatingspeed.console.ConsoleViewModel
import com.starwords.floatingspeed.console.ConsoleViewModelFactory
import com.starwords.floatingspeed.console.PermissionScreen
import com.starwords.floatingspeed.console.PermissionState
import com.starwords.floatingspeed.console.theme.FloatSpeedTheme
import com.starwords.floatingspeed.overlay.OverlayPermission
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        setContent {
            FloatSpeedTheme {
                val vm: ConsoleViewModel = viewModel(factory = ConsoleViewModelFactory(app))
                val state by vm.ui.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    vm.onEvent(ConsoleEvent.PermissionRecheck)
                }

                val backgroundLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    vm.onEvent(ConsoleEvent.PermissionRecheck)
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> app.session.retain()
                            Lifecycle.Event.ON_RESUME -> vm.onEvent(ConsoleEvent.Resume)
                            Lifecycle.Event.ON_STOP -> app.session.release()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        app.session.release()
                    }
                }

                DisposableEffect(state.keepScreenOn) {
                    if (state.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {}
                }

                var requested by remember { mutableStateOf(false) }
                LaunchedEffect(state.permission) {
                    if (state.permission == PermissionState.NotGranted && !requested) {
                        requested = true
                        permissionLauncher.launch(locationPermissions())
                    }
                }

                var requestedBackground by remember { mutableStateOf(false) }
                LaunchedEffect(state.permission) {
                    if (state.permission == PermissionState.Granted && !requestedBackground) {
                        requestedBackground = true
                        delay(BACKGROUND_REQUEST_DELAY_MS)
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }

                val openSettings: () -> Unit = remember(context) {
                    {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        runCatching { context.startActivity(intent) }
                        Unit
                    }
                }

                val openLocationSettings: () -> Unit = remember(context) {
                    {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                        Unit
                    }
                }

                val openOverlaySettings: () -> Unit = remember(context) {
                    {
                        runCatching { context.startActivity(OverlayPermission.intent(context)) }
                        Unit
                    }
                }

                when (state.permission) {
                    PermissionState.NotGranted -> PermissionScreen(
                        onAllow = { permissionLauncher.launch(locationPermissions()) },
                        onOpenSettings = openSettings,
                    )
                    else -> ConsoleScreen(
                        state = state,
                        onEvent = vm::onEvent,
                        onOpenAppSettings = openSettings,
                        onOpenLocationSettings = openLocationSettings,
                        onOpenOverlaySettings = openOverlaySettings,
                    )
                }
            }
        }
    }

    private fun locationPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    private companion object {
        private const val BACKGROUND_REQUEST_DELAY_MS = 150L
    }
}
