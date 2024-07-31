package com.example.heartratemonitor

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.heartratemonitor.ui.theme.HeartRateMonitorTheme
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {

    private lateinit var tflite: Interpreter
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var deviceAddress: String? = null

    private var bluetoothLeService: BluetoothLeService? = null
    private var isBound = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                try {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!

                    if (checkBluetoothPermissions()) {
                        // Check if this is the device we want to connect to
                        if (device.name == "TicWatch Pro 3 GPS") { // Replace with your smartwatch's name
                            deviceAddress = device.address
                            Log.e("MainActivity", "Device is true")
                            // Stop discovery once the device is found
                            bluetoothAdapter.cancelDiscovery()
                            startBluetoothOperations()
                        }
                    } else {
                        Log.e("MainActivity", "Bluetooth permissions are not granted")
                    }
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Permission error: $e")
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            val binder = service as BluetoothLeService.LocalBinder
            bluetoothLeService = binder.getService()
            isBound = true
            bluetoothLeService?.let { service ->
                if (checkBluetoothPermissions() && deviceAddress != null) {
                    service.connect(deviceAddress!!)
                } else {
                    Toast.makeText(this@MainActivity, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BluetoothManager and BluetoothAdapter
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initialize TensorFlow Lite model
        val modelPath = "ECG_classification.tflite"
        try {
            tflite = Interpreter(loadModelFile(modelPath))
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load model: $e")
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show()
            return
        }

        // Request necessary permissions
        if (checkPermissions()) {
            // Check if a device is already connected
            if (checkBluetoothPermissions()) {
                val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                Log.d("MainActivity", "Device connected $connectedDevices")

                if (connectedDevices.isEmpty()) {
                    // No devices connected, start device discovery
                    startDeviceDiscovery()
                } else {
                    Log.d("MainActivity", "A device is already connected")
                }
            }
        } else {
            // Request permissions
            requestBluetoothPermissions()
        }

        setContent {
            HeartRateMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HeartRateDisplay(0f) // Placeholder value for initial display
                }
            }
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun preprocessData(rawData: FloatArray): Array<FloatArray> {
        // Ensure the data is the correct shape [1, 187]
        return arrayOf(rawData)
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

    private fun checkBluetoothPermissions(): Boolean {
        return checkPermissions()
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            REQUEST_BLUETOOTH_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startDeviceDiscovery()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDeviceDiscovery() {
        if (checkBluetoothPermissions()) {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }

            bluetoothAdapter.startDiscovery()
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(bluetoothReceiver, filter)

            // Show paired devices
            showPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPairedDevices() {
        if (checkBluetoothPermissions()) {
            val pairedDevices = bluetoothAdapter.bondedDevices
            val deviceNames = pairedDevices.map { it.name }.toTypedArray()
            val deviceAddresses = pairedDevices.associateBy { it.name }

            bluetoothLeService?.initialize()
            // Show the paired devices in a dialog
            AlertDialog.Builder(this)
                .setTitle("Select a Device")
                .setItems(deviceNames) { _, which ->
                    val selectedDeviceName = deviceNames[which]
                    deviceAddress = deviceAddresses[selectedDeviceName]?.address
                    startBluetoothOperations()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBluetoothOperations() {
        // Bind to BluetoothLeService
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        Toast.makeText(this, "Bluetooth service started", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        if (isBound) {
            unbindService(serviceConnection)
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }
}

@Composable
fun HeartRateDisplay(heartRate: Float) {
    Text(
        text = "Current Heart Rate: $heartRate BPM",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun HeartRateDisplayPreview() {
    HeartRateMonitorTheme {
        HeartRateDisplay(72f)
    }
}
