package com.jacobsfactory.bluetoothchatwithkotlincompose

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.jacobsfactory.bluetoothchatwithkotlincompose.ui.theme.BluetoothChatWithKotlinComposeTheme
import com.jacobsfactory.bluetoothchatwithkotlincompose.ui.theme.divider_color

var EXTRA_DEVICE_ADDRESS = "device_address"
var EXTRA_SECURE = "secure"

class DeviceListActivity : ComponentActivity() {

    private val viewModel by lazy { ViewModelProvider(this)[DeviceListViewModel::class.java] }
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
        } else {
        }
    }
    private var secure = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        secure = intent.getBooleanExtra(EXTRA_SECURE, true)
        registerReceiver(viewModel.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(
            viewModel.receiver,
            IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        )
        registerReceiver(
            viewModel.receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        )
        if (!viewModel.mBluetoothAdapter.isEnabled) {
            requestEnableBluetooth()
        }
        setContent {
            BluetoothChatWithKotlinComposeTheme {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.title_paired_devices),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    LazyList(
                        devices = viewModel.pairedDevices,
                        onItemClicked = { onItemClicked(it) })
                    Text(
                        text = stringResource(id = R.string.title_other_devices),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    LazyList(
                        devices = viewModel.discoveredDevices,
                        onItemClicked = { onItemClicked(it) })
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.discover() }) {
                        Text("Scan")
                    }
                }
            }

        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        viewModel.mBluetoothAdapter.cancelDiscovery()
        unregisterReceiver(viewModel.receiver)
    }

    private fun requestEnableBluetooth() {
        val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activityResultLauncher.launch(enableBluetoothIntent)
    }

    @SuppressLint("MissingPermission")
    private fun onItemClicked(device: BluetoothDevice) {
        viewModel.mBluetoothAdapter.cancelDiscovery()
        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS, device.address)
        intent.putExtra(EXTRA_SECURE, secure)
        setResult(RESULT_OK, intent)
        finish()
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LazyList(devices: List<BluetoothDevice>, onItemClicked: (BluetoothDevice) -> Unit) {
    val scrollState = rememberLazyListState()

    LazyColumn(state = scrollState, modifier = Modifier.height(150.dp)) {
        items(devices, key = { device -> device.hashCode() }) { device ->
            Column(Modifier.fillParentMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onItemClicked(device) }) {
                    Text(
                        "${device.name}\n${device.address}",
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                Divider(color = divider_color)
            }
        }
    }
}