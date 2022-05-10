package com.jacobsfactory.bluetoothchatwithkotlincompose

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val mBluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    val connectionState = mutableStateOf(ConnectionState.STATE_PERMISSION)
    var mConnectedDeviceName = mutableStateOf("")

    val TAG = "BluetoothChatService"
    private val NAME_SECURE = "BluetoothChatSecure"
    private val NAME_INSECURE = "BluetoothChatInsecure"
    private val MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    val conversations = mutableStateListOf<String>()

    val stateString = derivedStateOf {
        when (connectionState.value) {
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
    private var mInsecureAcceptThread: AcceptThread? = null
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
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread(false)
            mInsecureAcceptThread!!.start()
        }
    }

    @Synchronized
    fun connect(address: String, secure: Boolean) {
        val device = mBluetoothAdapter.getRemoteDevice(address)
        Log.d(TAG, "connect to: $device")
        if (connectionState.value == ConnectionState.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        mConnectThread = ConnectThread(device, secure)
        mConnectThread!!.start()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice, socketType: String) {
        Log.d(TAG, "connected, Socket Type:$socketType")
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
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }
        mConnectedThread = ConnectedThread(socket!!, socketType)
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
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }
        connectionState.value = ConnectionState.STATE_NONE
    }

    fun write(out: ByteArray) {
        var r: ConnectedThread
        synchronized(this) {
            if (connectionState.value != ConnectionState.STATE_CONNECTED) return
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
        private val mmServerSocket: BluetoothServerSocket?
        private val mSocketType: String

        init {
            var tmp: BluetoothServerSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"
            try {
                tmp = if (secure) {
                    mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        NAME_SECURE,
                        MY_UUID_SECURE
                    )
                } else {
                    mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        NAME_INSECURE,
                        MY_UUID_INSECURE
                    )
                }
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Socket Type: " + mSocketType + "listen() failed",
                    e
                )
            }
            mmServerSocket = tmp
            connectionState.value = ConnectionState.STATE_LISTEN
        }

        override fun run() {
            Log.d(
                TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this
            )
            name = "AcceptThread$mSocketType"
            var socket: BluetoothSocket?

            while (connectionState.value != ConnectionState.STATE_CONNECTED) {
                socket = try {
                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e)
                    break
                }
                if (socket != null) {
                    synchronized(this) {
                        when (connectionState.value) {
                            ConnectionState.STATE_LISTEN, ConnectionState.STATE_CONNECTING ->
                                connected(
                                    socket, socket.remoteDevice,
                                    mSocketType
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
        }

        fun cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this)
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Socket Type" + mSocketType + "close() of server failed",
                    e
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val mmDevice: BluetoothDevice, secure: Boolean) :
        Thread() {
        private val mmSocket: BluetoothSocket?
        private val mSocketType: String
        override fun run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:$mSocketType")
            name = "ConnectThread$mSocketType"
            mBluetoothAdapter.cancelDiscovery()
            try {
                mmSocket!!.connect()
            } catch (e: IOException) {
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                    Log.e(
                        TAG,
                        "unable to close() $mSocketType socket during connection failure",
                        e2
                    )
                }
                connectionFailed()
                return
            }

            synchronized(this) { mConnectThread = null }

            connected(mmSocket, mmDevice, mSocketType)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect $mSocketType socket failed", e)
            }
        }

        init {
            var tmp: BluetoothSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            try {
                tmp = if (secure) {
                    mmDevice.createRfcommSocketToServiceRecord(MY_UUID_SECURE)
                } else {
                    mmDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e)
            }
            mmSocket = tmp
            connectionState.value = ConnectionState.STATE_CONNECTING
        }
    }

    private inner class ConnectedThread(socket: BluetoothSocket, socketType: String) : Thread() {
        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
            connectionState.value = ConnectionState.STATE_CONNECTED
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (connectionState.value == ConnectionState.STATE_CONNECTED) {
                try {
                    bytes = mmInStream!!.read(buffer)
                    val readMessage = String(buffer, 0, bytes)
                    conversations.add("${mConnectedDeviceName.value}  :    $readMessage")
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                mmOutStream!!.write(buffer)
                val writeMessage = String(buffer)
                conversations.add("Me:  $writeMessage")
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }

}
