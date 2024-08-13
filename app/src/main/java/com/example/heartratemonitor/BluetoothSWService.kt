package com.example.yourapppackageimport

import android.app.Service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

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
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acceptConnection()
        return START_STICKY
    }

    private fun acceptConnection() {
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("HeartRateApp", MY_UUID)
                Log.d(TAG, "Waiting for connection...")
                bluetoothSocket = bluetoothServerSocket?.accept()
                Log.d(TAG, "Connection accepted")

                bluetoothServerSocket?.close() // Close server socket after accepting connectionreceiveHeartRateData()
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
                // Handle error (e.g., close connection and retry)
            } finally {
                closeConnection()
            }
        }
    }

    private fun sendHeartRateDataToActivity(heartRate: Float) {
        val intent = Intent(ACTION_HEART_RATE_DATA)
        intent.putExtra(EXTRA_HEART_RATE_DATA, heartRate)
        sendBroadcast(intent)
    }

    private fun closeConnection() {
        try {
            bluetoothSocket?.close()
            Log.d(TAG, "Connection closed")
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