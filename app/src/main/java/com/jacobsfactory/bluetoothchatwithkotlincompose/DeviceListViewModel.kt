package com.jacobsfactory.bluetoothchatwithkotlincompose

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel

@SuppressLint("MissingPermission")
class DeviceListViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>().applicationContext
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val mBluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }

    val pairedDevices by lazy {
        mBluetoothAdapter.bondedDevices.toMutableStateList()
    }
    val discoveredDevices by lazy { mutableStateListOf<BluetoothDevice>() }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                        }
                    if (device != null) {
                        discoveredDevices.add(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.v("jacob", "jacob: discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.v("jacob", "jacob: discovery finished")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun discover() {
        if (mBluetoothAdapter.isDiscovering) {
            mBluetoothAdapter.cancelDiscovery()
        }
        mBluetoothAdapter.startDiscovery()
    }

}
