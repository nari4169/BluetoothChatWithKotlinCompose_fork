package com.jacobsfactory.bluetoothchatwithkotlincompose

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@Composable
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalPermissionsApi::class)
fun FeatureThatRequiresPermissions(viewModel: MainViewModel, content: @Composable () -> Unit) {
    val state = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )
    when {
        state.allPermissionsGranted -> {
            viewModel.connectionState = ConnectionState.STATE_NONE
            viewModel.start()
            content()
        }
        else -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                viewModel.connectionState = ConnectionState.STATE_NONE
                viewModel.start()
                content()
            } else {
                val textToShow = if (state.shouldShowRationale)
                    "ShowRationale" else "No permission"
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(textToShow)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { state.launchMultiplePermissionRequest() }) {
                        Text("Request")
                    }
                }
            }
        }
    }
}


