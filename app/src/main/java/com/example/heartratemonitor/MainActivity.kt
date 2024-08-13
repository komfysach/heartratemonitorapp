package com.example.heartratemonitor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
import com.example.yourapppackageimport.BluetoothSWService
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.delay
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {


    private lateinit var tflite: Interpreter
    private val heartRateData = mutableStateListOf<Float>()

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothSWService.ACTION_HEART_RATE_DATA) {
                val heartRate = intent.getFloatExtra(BluetoothSWService.EXTRA_HEART_RATE_DATA, 0f)
                // Update heartRate and heartRateData here
                heartRateData.add(heartRate)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TensorFlow Lite model
        val modelPath = "ECG_classification.tflite"
        tflite = Interpreter(loadModelFile(modelPath))

        // Register broadcast receiver
        val filter = IntentFilter(BluetoothSWService.ACTION_HEART_RATE_DATA)
        registerReceiver(heartRateReceiver, filter)

        Intent(this, BluetoothSWService::class.java).also { intent ->
            startService(intent)
        }

        setContent {
            HeartRateMonitorTheme {
                val chart = remember { createLineChart(this) }
                var heartRate by remember { mutableStateOf(0f) }
                val heartRateData = remember { mutableStateListOf<Float>() }
                HeartRateDisplay(heartRate = heartRate, heartRateData = heartRateData)

                // Update heartRateData somewhere in your code
//                LaunchedEffect(key1 = heartRateData) { // Example using LaunchedEffect
//                    while (true) {
//                        delay(1000) // Simulate getting new data every second
//                        heartRate = (70..100).random().toFloat()
//                        heartRateData.add(heartRate)
//                        chart.notifyDataSetChanged()
//                        chart.invalidate()
//                    }
//                }
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
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 150f
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(heartRateReceiver) // Unregister receiver when activity is destroyed
    }

    @Preview(showBackground = true)
    @Composable
    fun HeartRateDisplayPreview() {
        HeartRateMonitorTheme {
            HeartRateDisplay(
                heartRate = 72.5f,
                heartRateData = listOf(
                    72.5f, 73.0f, 73.5f, 74.0f, 74.5f, 75.0f, 75.5f, 76.0f
                )
            )
        }
    }
}
