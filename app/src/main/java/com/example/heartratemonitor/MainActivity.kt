package com.example.heartratemonitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.heartratemonitor.ui.theme.HeartRateMonitorTheme

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeService: BluetoothLeService? = null
    private var deviceAddress: String? = null
    private var isBound = false

    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothLeService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                    finish()
                }
                // Perform device connection
                deviceAddress?.let { bluetooth.connect(it) }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
        }
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    Log.i(TAG, "GATT connected.")
                    Toast.makeText(context, "Device connected", Toast.LENGTH_SHORT).show()
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected.")
                    Toast.makeText(context, "Device disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Bind to the service
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Start BLE scan
        startBleScan()

        setContent {
            HeartRateMonitorTheme {
                var heartRate by remember { mutableStateOf(0f) }
                HeartRateDisplay(heartRate = heartRate)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun startBleScan() {
        if (checkPermissions()) {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
        }
    }



    private fun stopBleScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
    }

    private val leScanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            Log.d(TAG, "Device found: ${device.name}, ${device.address}")
            if (device.name == "TicWatch Pro 3 GPS") { // Replace with your smartwatch's name
                deviceAddress = device.address
                Log.d(TAG, "Target device found: $deviceAddress")
                stopBleScan()
                bluetoothLeService?.connect(deviceAddress!!)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        stopBleScan()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun HeartRateDisplay(heartRate: Float) {
    Text(
        text = "Current Heart Rate: $heartRate BPM",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
    )
}

@Preview(showBackground = true)
@Composable
fun HeartRateDisplayPreview() {
    HeartRateMonitorTheme {
        HeartRateDisplay(heartRate = 72.5f)
    }
}
