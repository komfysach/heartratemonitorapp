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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.delay

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
                val chart = remember { createLineChart(this) }
                var heartRate by remember { mutableStateOf(0f) }
                val heartRateData = remember { mutableStateListOf<Float>() }
                HeartRateDisplay(heartRate = heartRate, heartRateData = heartRateData)

                // Update heartRateData somewhere in your code
                LaunchedEffect(key1 = heartRateData) { // Example using LaunchedEffect
                    while (true) {
                        delay(1000) // Simulate getting new data every second
                        heartRate = (70..100).random().toFloat()
                        heartRateData.add(heartRate)
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                    }
                }
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
fun HeartRateDisplay(heartRate: Float, heartRateData: List<Float>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center

    ) {
        Text(
            text = "Current Heart Rate: $heartRate BPM",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White
            )
        )
        Log.d("HeartRateDisplay", "Heart Rate Data: $heartRateData")
        HeartRateChart(heartRateData = heartRateData) // Assuming a HeartRateChart component
    }
}

@Composable
fun HeartRateChart(heartRateData: List<Float>) {
    val context = LocalContext.current
    val entries = heartRateData.mapIndexed { index, value -> Entry(index.toFloat(), value) }
    Log.d("HeartRateChart", "Entries: $entries")
    val min = heartRateData.minOrNull() ?: 0f
    val max = heartRateData.maxOrNull() ?: 0f
    Log.d("HeartRateData", "Min: $min, Max: $max")
    val dataSet = LineDataSet(entries, "Heart Rate").apply {
        setColors(Color.Cyan.toArgb(), Color.Blue.toArgb())
        lineWidth = 2f
        setDrawCircles(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
        setDrawFilled(true)
    }
    Log.d("HeartRateChart", "DataSet: $dataSet")
    val lineData = LineData(dataSet)
    val chart = remember { createLineChart(context) } // Assuming you're using Jetpack Compose

    chart.isAutoScaleMinMaxEnabled = false

    chart.apply {
        data = lineData
        description.isEnabled = false
        xAxis.isEnabled = false
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = heartRateData.size.toFloat() + 1
        axisLeft.axisMinimum =  0f
        axisLeft.axisMaximum =  150f
        axisLeft.textColor = Color.White.toArgb()
        axisLeft.isEnabled = true
        axisRight.isEnabled = false
        legend.isEnabled = false
        setTouchEnabled(false)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(false)
        notifyDataSetChanged()
        invalidate()
    }

    // Add the chart to your Compose UI using AndroidView
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        factory = { chart },
        update = {
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    )
}

private fun createLineChart(context: Context): LineChart {
    val chart = LineChart(context)
    // ... apply your chart configurations here ...
    return chart
}

@Preview(showBackground = true)
@Composable
fun HeartRateDisplayPreview() {
    HeartRateMonitorTheme {
        HeartRateDisplay(heartRate = 72.5f,
            heartRateData = listOf(
                72.5f, 73.0f, 73.5f, 74.0f, 74.5f, 75.0f, 75.5f, 76.0f
            )
            )
    }
}
