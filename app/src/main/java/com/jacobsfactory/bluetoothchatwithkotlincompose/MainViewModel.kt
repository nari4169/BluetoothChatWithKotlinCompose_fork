package com.jacobsfactory.bluetoothchatwithkotlincompose

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val mBluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    var connectionState by mutableStateOf(ConnectionState.STATE_PERMISSION)
    var mConnectedDeviceName = mutableStateOf("")

    val TAG = "BluetoothChatService"
    private val NAME_SECURE = "BluetoothChatSecure"
    private val MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")

    val conversations = mutableStateListOf<String>()

    val stateString = derivedStateOf {
        when (connectionState) {
            ConnectionState.STATE_NONE -> application.getString(R.string.title_not_connected)
            ConnectionState.STATE_CONNECTING -> application.getString(R.string.title_connecting)
            ConnectionState.STATE_LISTEN -> application.getString(R.string.title_listening)
            ConnectionState.STATE_CONNECTED -> application.getString(
                R.string.title_connected_to,
                mConnectedDeviceName.value
            )
            else -> ""
        }
    }

    private var mSecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    @Synchronized
    fun start() {
        Log.d(TAG, "start")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = AcceptThread(true)
            mSecureAcceptThread!!.start()
        }
    }

    @Synchronized
    fun connect(address: String) {
        val device = mBluetoothAdapter.getRemoteDevice(address)
        Log.d(TAG, "connect to: $device")
        if (connectionState == ConnectionState.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice) {
        Log.d(TAG, "connected")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        mConnectedThread = ConnectedThread(socket!!)
        mConnectedThread!!.start()

        mConnectedDeviceName.value = device.name
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        connectionState = ConnectionState.STATE_NONE
    }

    fun write(out: ByteArray) {
        var r: ConnectedThread
        synchronized(this) {
            if (connectionState != ConnectionState.STATE_CONNECTED) return
            r = mConnectedThread!!
        }
        r.write(out)
    }

    private fun connectionFailed() {
        start()
    }

    private fun connectionLost() {
        start()
    }

    @SuppressLint("MissingPermission")
    private inner class AcceptThread(secure: Boolean) : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                NAME_SECURE,
                MY_UUID_SECURE
            )
        }

        init {
            connectionState = ConnectionState.STATE_LISTEN
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            while (connectionState != ConnectionState.STATE_CONNECTED) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    break
                }
                socket?.also {
                    when (connectionState) {
                        ConnectionState.STATE_LISTEN, ConnectionState.STATE_CONNECTING ->
                            connected(
                                socket, socket.remoteDevice
                            )
                        ConnectionState.STATE_NONE, ConnectionState.STATE_CONNECTED ->                                 // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close()
                            } catch (e: IOException) {
                                Log.e(TAG, "Could not close unwanted socket", e)
                            }
                        else -> {}
                    }
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) :
        Thread() {
        init {
            connectionState = ConnectionState.STATE_CONNECTING
        }

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            mmDevice.createRfcommSocketToServiceRecord(MY_UUID_SECURE)
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery()

            mmSocket?.let { socket ->
                try {
                    socket.connect()
                } catch (e: IOException) {
                    try {
                        socket.close()
                    } catch (e2: IOException) {
                        Log.e(
                            TAG,
                            "unable to close() socket during connection failure",
                            e2
                        )
                    }
                    connectionFailed()
                    return
                }
                synchronized(this) { mConnectThread = null }
                connected(mmSocket, mmDevice)
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }

    }

    private inner class ConnectedThread(val socket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = socket.inputStream
        private val mmOutStream: OutputStream = socket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        init {
            connectionState = ConnectionState.STATE_CONNECTED
        }

        override fun run() {
            var bytes: Int
            // Keep listening to the InputStream until an exception occurs.
            while (connectionState == ConnectionState.STATE_CONNECTED) {
                try {
                    bytes = mmInStream.read(mmBuffer)
                    val readMessage = String(mmBuffer, 0, bytes)
                    conversations.add("${mConnectedDeviceName.value}  :    $readMessage")
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
                val writeMessage = String(bytes)
                conversations.add("Me:  $writeMessage")
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }
}
