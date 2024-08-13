package com.example.heartratemonitor

import android.annotation.SuppressLint
import android.app.Service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
import kotlin.io.path.inputStream

class BluetoothSWService : Service() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothServerSocket: BluetoothServerSocket? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectionJob: Job? = null

    companion object {
        private const val TAG = "BluetoothSWService"
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Replace with your app's UUID
        const val ACTION_HEART_RATE_DATA = "com.example.yourapppackage.ACTION_HEART_RATE_DATA"
        const val EXTRA_HEART_RATE_DATA = "com.example.yourapppackage.EXTRA_HEART_RATE_DATA"
        const val ACTION_CONNECTION_STATUS = "com.example.yourapppackage.ACTION_CONNECTION_STATUS"
        const val EXTRA_CONNECTION_STATUS = "com.example.yourapppackage.EXTRA_CONNECTION_STATUS"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acceptConnection()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun acceptConnection() {
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("HeartRateApp", MY_UUID)
                Log.d(TAG, "Waiting for connection...")
                bluetoothSocket = bluetoothServerSocket?.accept()
                Log.d(TAG, "Connection accepted")

                updateConnectionStatus(true)

                bluetoothServerSocket?.close() // Close server socket after accepting connectionreceiveHeartRateData()
                receiveHeartRateData()
            } catch (e: IOException) {
                Log.e(TAG, "Error accepting connection: ${e.message}")
                // Handle connection error (e.g., retry)
            }
        }
    }

    private fun receiveHeartRateData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ByteArray(1024)
                while (true) {
                    Log.d(TAG, "Receiving data...")
                    val bytesRead = bluetoothSocket?.inputStream?.read(buffer)
                    if (bytesRead != null && bytesRead > 0) {
                        val heartRateData = String(buffer, 0, bytesRead).toFloatOrNull()
                        if (heartRateData != null) {
                            sendHeartRateDataToActivity(heartRateData)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error receiving data: ${e.message}")
                closeConnection()
            } finally {
                closeConnection()
            }
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        val intent = Intent(ACTION_CONNECTION_STATUS).apply {
            putExtra(EXTRA_CONNECTION_STATUS, isConnected)
        }
        // Use a handler to send the broadcast on the main thread
        Handler(Looper.getMainLooper()).post {
            applicationContext.sendBroadcast(intent)
        }
    }

    private fun sendHeartRateDataToActivity(heartRate: Float) {
        Log.d(TAG, "Received heart rate data: $heartRate")
        val intent = Intent(ACTION_HEART_RATE_DATA).apply {
            putExtra(EXTRA_HEART_RATE_DATA, heartRate)
        }
        // Use a handler to send the broadcast on the main thread
        Handler(Looper.getMainLooper()).post {
            applicationContext.sendBroadcast(intent)
        }
    }

    private fun closeConnection() {
        try {
            bluetoothSocket?.close()
            Log.d(TAG, "Connection closed")
            updateConnectionStatus(false)
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionJob?.cancel()
        closeConnection()
    }
}