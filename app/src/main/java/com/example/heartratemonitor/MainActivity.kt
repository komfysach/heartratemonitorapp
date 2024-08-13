package com.example.heartratemonitor

import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.heartratemonitor.ui.theme.HeartRateMonitorTheme

import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {


    private lateinit var tflite: Interpreter
    private val heartRateData = mutableStateListOf<Float>()
    private val isNormalHeartRate = mutableStateOf(false)

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("HeartRateReceiver", "Received intent: ${intent.action}")
            if (intent.action == BluetoothSWService.ACTION_HEART_RATE_DATA) {
                val heartRate = intent.getFloatExtra(BluetoothSWService.EXTRA_HEART_RATE_DATA, 0f)
                heartRateData.add(heartRate)
            }
        }
    }

    private val connectionStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("ConnectionStatusReceiver", "Received intent: ${intent.action}")
            if (intent.action == BluetoothSWService.ACTION_CONNECTION_STATUS) {
                ConnectionState.isConnected.value = intent.getBooleanExtra(BluetoothSWService.EXTRA_CONNECTION_STATUS, false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TensorFlow Lite model
        val modelPath = "ECG_classification.tflite"
        tflite = Interpreter(loadModelFile(modelPath))

        Intent(this, BluetoothSWService::class.java).also { intent ->
            startService(intent)
        }

        val connectionStatusFilter = IntentFilter(BluetoothSWService.ACTION_CONNECTION_STATUS)
        registerReceiver(connectionStatusReceiver, connectionStatusFilter)

        val heartRateFilter = IntentFilter(BluetoothSWService.ACTION_HEART_RATE_DATA)
        registerReceiver(heartRateReceiver, heartRateFilter)

        setContent {
            HeartRateMonitorTheme {
                val chart = remember { createLineChart(this) }
                var heartRate by remember { mutableStateOf(0f) }
                val heartRateData = remember { mutableStateListOf<Float>() }
                var isNormalHeartRate = remember { mutableStateOf(false) }

                HeartRateDisplay(heartRate = heartRate, heartRateData = heartRateData, isNormalHeartRate = isNormalHeartRate.value)

                // Update heartRateData somewhere in your code
//                LaunchedEffect(key1 = heartRateData) { // Example using LaunchedEffect
//                    while (heartRateData.size < 187) {
//                        delay(1000) // Simulate getting new data every second
//                        heartRate = (52..54).random().toFloat()
//                        heartRateData.add(heartRate)
//                        chart.notifyDataSetChanged()
//                        chart.invalidate()
//                    }
//                }

//                LaunchedEffect(key1 = heartRateData) { // Example using LaunchedEffect
//                    while (heartRateData.size < 187) {
//                        delay(1000) // Simulate getting new data every second
//                        heartRate = (110..150).random().toFloat()
//                        heartRateData.add(heartRate)
//                        chart.notifyDataSetChanged()
//                        chart.invalidate()
//                    }
//                }

                LaunchedEffect(key1 = heartRateData) {
                    snapshotFlow { heartRateData.size }
                        .collect { size ->
                            if (size >= 187) {
                                runInference(heartRateData)
                            }
                        }
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

    private fun runInference(heartRateData: List<Float>) {
        val minHR = 0f // Replace with the actual minimum heart rate from your training data
        val maxHR = 200f // Replace with the actual maximum heart rate from your training data

        // 1. Preprocess the data
        val input = heartRateData.map { (it - minHR) / (maxHR - minHR) }.toFloatArray()
        if (input.size != 187) {
            // Handle case where input size is not 187
            Log.e("Inference", "Invalid input size: ${input.size}")
            return
        }
        val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 187),
            org.tensorflow.lite.DataType.FLOAT32)
        inputBuffer.loadArray(input)

        // 2. Run inference
        val outputs = HashMap<Int, Any>()
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1),
            org.tensorflow.lite.DataType.FLOAT32)
        tflite.run(inputBuffer.buffer, outputBuffer.buffer)

        val output = outputBuffer.floatArray
        val result = output[0]
        val isNormal = result <= 0.5 // Apply the threshold

        // 4. Log the output and update state
        Log.d("Inference", "Output: $result, isNormal: $isNormal")
        isNormalHeartRate.value = isNormal
        // You'll need to determine how to interpret the output and update your state
        // For example, you might have a threshold to determine normal/abnormal
    }

    @Composable
    fun HeartRateDisplay(heartRate: Float, heartRateData: List<Float>, isNormalHeartRate: Boolean) {
        val connected = ConnectionState.isConnected.value
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
            Text(text = "Is Normal Heart Rate: $isNormalHeartRate", color = if (isNormalHeartRate) Color.Green else Color.Red)
            Text(text = if (connected) "Connected" else "Disconnected", color = if (connected) Color.Green else Color.Red)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        val heartRateReceiverFilter = IntentFilter(BluetoothSWService.ACTION_HEART_RATE_DATA)
        registerReceiver(heartRateReceiver, heartRateReceiverFilter, RECEIVER_NOT_EXPORTED)
        val connectionFilter = IntentFilter(BluetoothSWService.ACTION_CONNECTION_STATUS)
        registerReceiver(connectionStatusReceiver, connectionFilter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(heartRateReceiver)
        unregisterReceiver(connectionStatusReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(heartRateReceiver) // Unregister receiver when activity is destroyed
        unregisterReceiver(connectionStatusReceiver)
    }

    object ConnectionState {
        var isConnected: MutableState<Boolean> = mutableStateOf(false)
    }

    @Preview(showBackground = true)
    @Composable
    fun HeartRateDisplayPreview() {
        HeartRateMonitorTheme {
            HeartRateDisplay(
                heartRate = 72.5f,
                heartRateData = listOf(
                    72.5f, 73.0f, 73.5f, 74.0f, 74.5f, 75.0f, 75.5f, 76.0f
                ),
                isNormalHeartRate = true
            )
        }
    }
}
