package com.example.gesturelock

import com.example.gesturelock.ui.theme.GestureLockTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview


import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sqrt

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.runtime.collectAsState
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState

// Home Directory/Routes
private object Routes {
    const val HOME = "home"
    const val BINARY = "binary"
    const val ACCEL = "accelerometer"
    const val GYRO = "gyro"
    const val PROX = "proximity"

    const val BLE = "BLE"
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GestureLockTheme {
                EnsureBluetoothConnectPermission()
                GestureLockApp()
            }
        }
    }
}


@Composable
fun GestureLockApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val context = LocalContext.current
    val bt = remember { BluetoothManager(context.applicationContext) }

    Scaffold { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = modifier.padding(padding)

        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onBinaryClick = { navController.navigate(Routes.BINARY) },
                    onAccelClick = { navController.navigate(Routes.ACCEL) },
                    onGyroClick = { navController.navigate(Routes.GYRO) },
                    onProxClick = { navController.navigate(Routes.PROX) },
                    onBLEClick = { navController.navigate(Routes.BLE) }
                )
            }
            composable(Routes.BINARY) {
                // Uses your existing code exactly
                DisplayBinary()
            }
            composable(Routes.ACCEL) {
                AccelerometerScreen(bt = bt)
            }
            composable(Routes.GYRO) {
                GyroScreen(bt = bt)
            }
            composable(Routes.PROX) {
                ProxScreen(bt = bt)
            }
            composable(Routes.BLE) {
                BLEScreen(bt = bt)
            }
        }
    }
}

// Home screen with buttons
@Composable
fun HomeScreen(
    onBinaryClick: () -> Unit,
    onAccelClick: () -> Unit,
    onGyroClick: () -> Unit,
    onProxClick: () -> Unit,
    onBLEClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Select a mode")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBinaryClick, modifier = Modifier.fillMaxWidth()) {
            Text("Binary LEDs")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onAccelClick, modifier = Modifier.fillMaxWidth()) {
            Text("Accelerometer")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onGyroClick, modifier = Modifier.fillMaxWidth()) {
            Text("Gyro")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onProxClick, modifier = Modifier.fillMaxWidth()) {
            Text("Prox")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onBLEClick, modifier = Modifier.fillMaxWidth()) {
            Text("BLE")
        }
    }
}

//Binary (LAB1)
@Composable
fun DisplayBinary(modifier: Modifier = Modifier) {
    var count by remember { mutableStateOf(0) }

    val value = count % 8

    val bit2 = value and 4 != 0  // MSB
    val bit1 = value and 2 != 0
    val bit0 = value and 1 != 0  // LSB

    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Binary LEDs")

        Row {
            RadioButton(selected = bit2, onClick = {})
            RadioButton(selected = bit1, onClick = {})
            RadioButton(selected = bit0, onClick = {})
        }

        Text(text = "Decimal: $value")
        Text(text = "Binary: ${value.toString(2).padStart(3, '0')}")


        Row {
            Button(onClick = { count++ }) {
                Text(text = "Count")
            }

            Button(onClick = { count = 0 }) {
                Text(text = "Clear")
            }
        }

    }
}

// accelerometer logic
@Composable
fun AccelerometerScreen(bt: BluetoothManager, modifier: Modifier = Modifier) {
    val (ax, ay, az) = rememberAccelerometer().value

    val magnitude = sqrt(ax * ax + ay * ay + az * az) - 9.8f

    val index = when {
        magnitude < 1f  -> 0
        magnitude < 3f  -> 1
        magnitude < 5f  -> 2
        magnitude < 7f  -> 3
        magnitude < 9f  -> 4
        magnitude < 11f -> 5
        else -> 6
    }

    val pattern = buildSingleLedPattern(index)
    var lastPattern by remember { mutableStateOf("") }

    LaunchedEffect(pattern) {
        if (pattern != lastPattern) {
            sendLedState(bt, pattern)
            lastPattern = pattern
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("x=$ax y=$ay z=$az mag=${"%.2f".format(magnitude)}")
        Text("Accelerometer LEDs")

        Row {
            for (i in 0..6) {
                RadioButton(selected = (i == index), onClick = {})
            }
        }
    }
}


// Gyro logic
@Composable
fun GyroScreen(bt: BluetoothManager, modifier: Modifier = Modifier) {
    val angles = rememberRotationVectorAngles().value
    val rollDeg = radToDeg(angles.rollRad)

    val index = when {
        rollDeg > 40f  -> 6
        rollDeg > 20f  -> 5
        rollDeg > 0f   -> 4
        rollDeg > -20f -> 3
        rollDeg > -40f -> 2
        rollDeg > -60f -> 1
        else -> 0
    }

    val pattern = buildSingleLedPattern(index)

    var lastPattern by remember { mutableStateOf("") }
    LaunchedEffect(pattern) {
        if (pattern != lastPattern) {
            sendLedState(bt, pattern)
            lastPattern = pattern
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "roll=${"%.1f".format(rollDeg)}°")
        Text(text = "Gyro LED's")

        Row {
            for (i in 0..6) {
                RadioButton(selected = (i == index), onClick = {})
            }
        }
    }
}

// Prox logic
@Composable
fun ProxScreen(bt: BluetoothManager, modifier: Modifier = Modifier) {
    val prox = rememberProximity().value

    // Many proximity sensors return 0/near and max/far.
    val isNear = prox <= 1f

    val index = if (isNear) 0 else 6
    val pattern = buildSingleLedPattern(index)

    var lastPattern by remember { mutableStateOf("") }
    LaunchedEffect(pattern) {
        if (pattern != lastPattern) {
            sendLedState(bt, pattern)
            lastPattern = pattern
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("proximity=${"%.2f".format(prox)}")
        Text("Proximity LEDs (NEAR/FAR)")

        Row {
            RadioButton(selected = isNear, onClick = {})
            RadioButton(selected = !isNear, onClick = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEScreen(bt: BluetoothManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bt = remember { bt }
    val status = bt.status.collectAsState().value

    // Permission gate (Android 12+)
    val hasPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    var expanded by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(listOf<Pair<String, String>>()) } // name, mac
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Load paired devices once permission is available
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val bonded = adapter?.bondedDevices.orEmpty()

        pairedDevices = bonded
            .map { d -> (d.name ?: "Unknown") to d.address }
            .sortedBy { it.first }

        if (selected == null && pairedDevices.isNotEmpty()) {
            selected = pairedDevices.first()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bluetooth (Paired Devices)")

        Spacer(Modifier.height(12.dp))

        // Show permission message
        if (!hasPermission) {
            Text("Bluetooth permission not granted yet. Go back and allow it when prompted.")
            return@Column
        }

        // If no paired devices found
        if (pairedDevices.isEmpty()) {
            Text("No paired devices found.\nPair ESP32-BT-Slave in phone Bluetooth settings first.")
            return@Column
        }

        // Dropdown (paired devices)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selected?.first?.let { "${it} (${selected?.second})" } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select paired device") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                pairedDevices.forEach { item ->
                    DropdownMenuItem(
                        text = { Text("${item.first} (${item.second})") },
                        onClick = {
                            selected = item
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Connect button
        Button(
            onClick = {
                val mac = selected?.second ?: return@Button
                bt.connect(mac)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }

        Spacer(Modifier.height(12.dp))

        // Status display
        Text(
            when (status) {
                is BluetoothManager.Status.IDLE -> "Status: IDLE"
                is BluetoothManager.Status.CONNECTING -> "Status: CONNECTING..."
                is BluetoothManager.Status.CONNECTED -> {
                    val s = status as BluetoothManager.Status.CONNECTED
                    "Status: CONNECTED to ${s.deviceName ?: s.address}"
                }
                is BluetoothManager.Status.ERROR -> {
                    val s = status as BluetoothManager.Status.ERROR
                    "Status: ERROR - ${s.message}"
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // test buttons
//        Row(modifier = Modifier.fillMaxWidth()) {
//            Button(
//                onClick = { bt.send("1") },
//                modifier = Modifier.weight(1f)
//            ) { Text("LED ON") }
//
//            Spacer(Modifier.width(12.dp))
//
//            Button(
//                onClick = { bt.send("0") },
//                modifier = Modifier.weight(1f)
//            ) { Text("LED OFF") }
//        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    HomeScreen(onBinaryClick = {}, onAccelClick = {}, onGyroClick = {}, onProxClick = {}, onBLEClick = {} )
}


@Composable
fun rememberAccelerometer(): State<Triple<Float, Float, Float>> {
    val context = LocalContext.current

    // latest reading
    val accelState = remember { mutableStateOf(Triple(0f, 0f, 0f)) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accel == null) {
            // device has no accelerometer
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // values[0]=x, values[1]=y, values[2]=z
                    accelState.value = Triple(event.values[0], event.values[1], event.values[2])
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return accelState
}

data class OrientationAngles(
    val azimuthRad: Float,
    val pitchRad: Float,
    val rollRad: Float
)

fun radToDeg(rad: Float): Float = (rad * (180f / PI.toFloat()))


@Composable
fun rememberRotationVectorAngles(): State<OrientationAngles> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(OrientationAngles(0f, 0f, 0f)) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (sensor == null) {
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // event.values is the rotation vector
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)

                    state.value = OrientationAngles(
                        azimuthRad = orientation[0],
                        pitchRad = orientation[1],
                        rollRad = orientation[2]
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    return state
}

@Composable
fun rememberProximity(): State<Float> {
    val context = LocalContext.current
    val proximityState = remember { mutableStateOf(Float.MAX_VALUE) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (sensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    proximityState.value = event.values[0]
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return proximityState
}

//BLE Below

@Composable
fun EnsureBluetoothConnectPermission() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    LaunchedEffect(Unit) {

        val connectGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        val scanGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

        if (!connectGranted || !scanGranted) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }
}

private fun buildSingleLedPattern(index: Int): String {
    val clamped = index.coerceIn(0, 6)

    return buildString {
        for (i in 0..6) {
            append(if (i == clamped) '1' else '0')
        }
    }
}

private fun sendLedState(bt: BluetoothManager, pattern: String) {
    bt.send("L$pattern\n")
}
