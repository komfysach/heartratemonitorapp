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

    companion object {
        const val ACTION_CLOSE_CONNECTION = "com.example.yourapppackage.ACTION_CLOSE_CONNECTION"
    }

    private lateinit var tflite: Interpreter

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("HeartRateReceiver", "Received intent: ${intent.action}")
            if (intent.action == BluetoothSWService.ACTION_HEART_RATE_DATA) {
                val heartRate = intent.getFloatExtra(BluetoothSWService.EXTRA_HEART_RATE_DATA, 0f)
                HeartRateState.heartRateData.add(heartRate)
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
        val modelPath = "ECG_Classification.tflite"
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
                HeartRateDisplay()

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

                LaunchedEffect(key1 =
                    HeartRateState.heartRateData
                ) {
                    snapshotFlow { HeartRateState.heartRateData.size }
                        .collect { size ->
                            if (size >= 187) {
                                runInference(HeartRateState.heartRateData)
                                val intent = Intent(ACTION_CLOSE_CONNECTION)
                                sendBroadcast(intent)
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
        val meanHR = floatArrayOf(
            9.76709313e-01f, 7.21773545e-01f, 4.03607687e-01f, 2.43468916e-01f, 2.07935118e-01f, 2.17154768e-01f, 2.22301824e-01f, 2.24856052e-01f, 2.27526330e-01f, 2.29929592e-01f, 2.32454338e-01f, 2.34139199e-01f, 2.37237920e-01f, 2.40512679e-01f, 2.42992291e-01f, 2.46530473e-01f, 2.49254235e-01f, 2.52729631e-01f, 2.56866320e-01f, 2.60375769e-01f, 2.64322537e-01f, 2.68635860e-01f, 2.76258754e-01f, 2.86480875e-01f, 2.97049048e-01f, 3.08385759e-01f, 3.18271485e-01f, 3.27586508e-01f, 3.37251630e-01f, 3.45145782e-01f, 3.51467787e-01f, 3.54516651e-01f, 3.55296590e-01f, 3.52960789e-01f, 3.46503440e-01f, 3.38560262e-01f, 3.27201341e-01f, 3.15784513e-01f, 3.03959834e-01f, 2.92748459e-01f, 2.84176750e-01f, 2.76526264e-01f, 2.70664026e-01f, 2.65732115e-01f, 2.62208027e-01f, 2.61706284e-01f, 2.61611023e-01f, 2.62257752e-01f, 2.63417658e-01f, 2.62835566e-01f, 2.63271778e-01f, 2.62072052e-01f, 2.60372309e-01f, 2.59385195e-01f, 2.57037523e-01f, 2.56304152e-01f, 2.53654625e-01f, 2.52203381e-01f, 2.51925157e-01f, 2.52171868e-01f, 2.53542423e-01f, 2.52560260e-01f, 2.52177187e-01f, 2.51968099e-01f, 2.50610329e-01f, 2.51251294e-01f, 2.52542344e-01f, 2.57327158e-01f, 2.58053228e-01f, 2.53892519e-01f, 2.54536630e-01f, 2.55809767e-01f, 2.51530309e-01f, 2.43182021e-01f, 2.37629359e-01f, 2.37440119e-01f, 2.36760107e-01f, 2.38927273e-01f, 2.43350060e-01f, 2.49292070e-01f, 2.52594428e-01f, 2.48894228e-01f, 2.47252666e-01f, 2.48292392e-01f, 2.47504025e-01f, 2.47438674e-01f, 2.40435505e-01f, 2.32719488e-01f, 2.30150212e-01f, 2.28811186e-01f, 2.23405760e-01f, 2.18646740e-01f, 2.17416596e-01f, 2.14818811e-01f, 2.12610111e-01f, 2.14422779e-01f, 2.10876853e-01f, 2.09433497e-01f, 2.07891373e-01f, 2.04827730e-01f, 2.03942382e-01f, 2.04107762e-01f, 1.98899727e-01f, 1.93084776e-01f, 1.85573133e-01f, 1.74905428e-01f, 1.65068644e-01f, 1.61968840e-01f, 1.56403877e-01f, 1.51550079e-01f, 1.47253143e-01f, 1.42722733e-01f, 1.39941472e-01f, 1.39246523e-01f, 1.35055137e-01f, 1.31293844e-01f, 1.27271577e-01f, 1.22353246e-01f, 1.17241686e-01f, 1.15867637e-01f, 1.11736767e-01f, 1.06533526e-01f, 1.01348943e-01f, 9.66805869e-02f, 9.21233357e-02f, 9.10111966e-02f, 8.55901957e-02f, 8.10680895e-02f, 7.88230418e-02f, 7.76029510e-02f, 7.52987103e-02f, 7.47718187e-02f, 7.04562955e-02f, 6.46526037e-02f, 6.12008261e-02f, 5.80604728e-02f, 5.51477246e-02f, 5.49680936e-02f, 5.19371188e-02f, 4.79157863e-02f, 4.48233777e-02f, 4.11498969e-02f, 3.93726685e-02f, 3.90959812e-02f, 3.64301979e-02f, 3.36487828e-02f, 3.07922547e-02f, 2.84101096e-02f, 2.56702054e-02f, 2.53498252e-02f, 2.28751957e-02f, 2.15156733e-02f, 2.13153777e-02f, 2.03036153e-02f, 1.86733100e-02f, 1.86970401e-02f, 1.63805275e-02f, 1.47451367e-02f, 1.40434355e-02f, 1.27471037e-02f, 1.14719970e-02f, 1.14555560e-02f, 1.05156368e-02f, 9.59951137e-03f, 8.95724687e-03f, 8.50914044e-03f, 7.83735448e-03f, 7.85484812e-03f, 7.01752681e-03f, 6.43763765e-03f, 5.39697792e-03f, 4.12210530e-03f, 3.04610835e-03f, 2.91685306e-03f, 2.49708447e-03f, 2.08888342e-03f, 1.91694253e-03f, 1.40836575e-03f, 1.19338588e-03f, 1.15748213e-03f, 9.03484886e-04f, 7.47482734e-04f, 6.45644421e-04f, 4.54894285e-04f, 1.89125968e-04f, 1.99420790e-04f, 0.00000000e+00f
        )
        val stdHR = floatArrayOf(0.03470816f, 0.19504876f, 0.2497802f, 0.24994205f, 0.21841895f, 0.19250993f, 0.18082838f, 0.17698515f, 0.17672716f, 0.17666019f, 0.17732674f, 0.17720084f, 0.17850313f, 0.18036457f, 0.18088844f, 0.18227541f, 0.18393003f, 0.18318047f, 0.1828837f, 0.18297135f, 0.18023342f, 0.17954865f, 0.17873282f, 0.17983376f, 0.18217227f, 0.1849819f, 0.18661587f, 0.18842134f, 0.19128046f, 0.19429248f, 0.19713109f, 0.19959197f, 0.20056418f, 0.19943193f, 0.19594134f, 0.19120432f, 0.18604573f, 0.18181638f, 0.17855975f, 0.17613725f, 0.1749665f, 0.17398211f, 0.17382035f, 0.17353637f, 0.17376369f, 0.17513054f, 0.17724277f, 0.17967817f, 0.18208839f, 0.18172102f, 0.1810081f, 0.17975898f, 0.17737881f, 0.17729957f, 0.17538411f, 0.17509242f, 0.17550848f, 0.17450505f, 0.17425649f, 0.17500272f, 0.17740732f, 0.17871513f, 0.1792368f, 0.18090901f, 0.18156537f, 0.18325753f, 0.18714796f, 0.19517488f, 0.20014505f, 0.19636303f, 0.19839752f, 0.2064702f, 0.20095161f, 0.19184446f, 0.1922309f, 0.19560095f, 0.19652306f, 0.1993908f, 0.2039514f, 0.21336547f, 0.21854512f, 0.21861528f, 0.22260744f, 0.22810922f, 0.22801818f, 0.235506f, 0.23046488f, 0.22480168f, 0.22809397f, 0.22789465f, 0.22318111f, 0.22030023f, 0.21962494f, 0.21891358f, 0.2200046f, 0.22132072f, 0.22111781f, 0.22488817f, 0.22875245f, 0.23122168f, 0.23708693f, 0.24017461f, 0.24089467f, 0.23800142f, 0.23074383f, 0.22377817f, 0.21627367f, 0.21465908f, 0.21200475f, 0.21116447f, 0.20900001f, 0.20509954f, 0.20514614f, 0.20435138f, 0.20080601f, 0.20033506f, 0.19855952f, 0.19565187f, 0.19283058f, 0.18986657f, 0.18805376f, 0.18580577f, 0.18436543f, 0.1801348f, 0.1773213f, 0.17553953f, 0.16972504f, 0.1638857f, 0.16337163f, 0.16608051f, 0.16741105f, 0.16705357f, 0.16203139f, 0.15228989f, 0.14792225f, 0.14459673f, 0.14138174f, 0.14054076f, 0.13809759f, 0.13252349f, 0.12836797f, 0.12298895f, 0.12261607f, 0.12337312f, 0.11854009f, 0.11014987f, 0.10272493f, 0.09812245f, 0.09453914f, 0.09357027f, 0.08746402f, 0.08620795f, 0.08646996f, 0.08519962f, 0.08334349f, 0.08319964f, 0.07708501f, 0.07385158f, 0.07381849f, 0.0695571f, 0.0666935f, 0.06672824f, 0.06508841f, 0.06315753f, 0.06093021f, 0.05978846f, 0.05894161f, 0.05950028f, 0.05636724f, 0.05419789f, 0.04855755f, 0.04102693f, 0.0343037f, 0.03394685f, 0.03154536f, 0.02787456f, 0.02736808f, 0.02265396f, 0.02173926f, 0.02156282f, 0.01725052f, 0.01479679f, 0.01400152f, 0.0120879f, 0.00645669f, 0.00679844f, 1.0f)

            // 1. Preprocess the data (StandardScaler)
        val input = heartRateData.mapIndexed { index, hr ->
            (hr - meanHR[index]) / stdHR[index]
        }.toFloatArray()
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
        IsNormalHeartRateState.isNormalHeartRate.value = isNormal
        // You'll need to determine how to interpret the output and update your state
        // For example, you might have a threshold to determine normal/abnormal
    }

    @Composable
    fun HeartRateDisplay() {
        val connected = ConnectionState.isConnected.value
        val filteredHeartRateData = HeartRateState.heartRateData.filterNot { it == 0.0f }
        val heartRateData = HeartRateState.heartRateData
        val currentHeartRate = filteredHeartRateData.lastOrNull() ?: 0f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center

        ) {
            Text(text = "Is Normal Heart Rate: ${IsNormalHeartRateState.isNormalHeartRate.value}", color = if (IsNormalHeartRateState.isNormalHeartRate.value) Color.Green else Color.Red)
            Text(text = if (connected) "Connected" else "Disconnected", color = if (connected) Color.Green else Color.Red)
            Text(
                text = "Current Heart Rate: $currentHeartRate BPM",
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
        val filteredHeartRateData = HeartRateState.heartRateData.filterNot { it == 0.0f }
        val context = LocalContext.current
        val entries = filteredHeartRateData.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        Log.d("HeartRateChart", "Entries: $entries")
        val min = heartRateData.minOrNull() ?: 40f
        val max = heartRateData.maxOrNull() ?: 150f
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

    object HeartRateState {
        val heartRateData = mutableStateListOf<Float>()
    }

    object IsNormalHeartRateState {
        var isNormalHeartRate: MutableState<Boolean> = mutableStateOf(false)
    }

    @Preview(showBackground = true)
    @Composable
    fun HeartRateDisplayPreview() {
        HeartRateMonitorTheme {
            HeartRateDisplay(

            )
        }
    }
}
