package org.polisan.crescendo

import SocketConnection
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polisan.crescendo.ui.theme.CrescendoTheme
import java.net.InetSocketAddress
import java.net.Socket

interface ConnectionListener {
    fun onConnectionSuccess()
    fun onConnectionLost()
    fun onNewInfo(info: String)
}


class MainActivity : ComponentActivity(), ConnectionListener {
    val isConnected = mutableStateOf(false)
    var currentIP = mutableStateOf("")
    var ipAddressToConnect = mutableStateOf("")
    var connection: SocketConnection? = null
    var art = mutableStateOf("")
    var length = mutableStateOf("")
    var position = mutableStateOf("")
    var positionPercentage by mutableStateOf(0.0f)
    var title = mutableStateOf("")
    var artist = mutableStateOf("")
    private var volume = mutableStateOf(0f)
    private var isShuffle = mutableStateOf(false)
    private var repeatStatus = mutableStateOf(-1)
    private var isPlaying = mutableStateOf(true)
    var playPauseIcon =
        mutableStateOf(if (isPlaying.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow)
    var shuffleIcon =
        mutableStateOf(if (isShuffle.value) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle)
    var repeatIcon = mutableStateOf(
        when (repeatStatus.value) {
            0 -> Icons.Rounded.Repeat
            1 -> Icons.Rounded.RepeatOn
            2 -> Icons.Rounded.RepeatOneOn
            else -> {
                Icons.Rounded.Repeat
            }
        }
    )
    var volumeIcon = mutableStateOf(
        if (volume.value <= 0) Icons.Rounded.VolumeOff
        else if (volume.value > 0 && volume.value < 0.4) Icons.Rounded.VolumeMute
        else if (volume.value >= 0.4 && volume.value < 0.7) Icons.Rounded.VolumeDown
        else Icons.Rounded.VolumeUp
    )
    var updatePosition = true

    var currentElement = -1
    var elementList = mutableStateOf(emptyList<Pair<String, String>>())
    var isBottomSheetOpen = mutableStateOf(false)
    var isChoosingPlayer =
        mutableStateOf(false) //whether need to spawn bottom sheet for players or for output devices

    private var isScanning = false

    private suspend fun scanLocalNetworkAndConnect(port: Int): SocketConnection? {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var baseIp: String

        while (isScanning) {
            for (ip in connectivityManager.getLinkProperties(connectivityManager.activeNetwork)?.linkAddresses?.indices!!) {
                var testIP =
                    connectivityManager.getLinkProperties(connectivityManager.activeNetwork)?.linkAddresses?.get(
                        ip
                    )?.address.toString()
                testIP = testIP.substring(1)

                if (Patterns.IP_ADDRESS.matcher(testIP).matches()) {
                    Log.d("SOCKET", "Scanning IP base: $testIP =")
                    baseIp = testIP.substringBeforeLast('.')
                    for (i in 1..255) {
                        if (!isScanning) break
                        val ipAddress = "$baseIp.$i"
                        currentIP.value = ipAddress
                        val socket = Socket()
                        try {
                            Log.d("SOCKET", "Trying to connect to $ipAddress:$port")
                            // Connect to the IP address and port
                            withContext(Dispatchers.IO) {
                                socket.connect(InetSocketAddress(ipAddress, port), 50)
                                Log.d("SOCKET", "Found server at $ipAddress:$port")
                                isScanning = false
                            }
                            // If the connection is successful, create a SocketConnection instance and return it
                            return SocketConnection(ipAddress, port, this@MainActivity)
                        } catch (e: Exception) {
                            // Ignore the exception and continue with the next IP address
                        } finally {
                            // Close the socket if it was opened but not connected
                            if (!socket.isConnected) {
                                withContext(Dispatchers.IO) {
                                    socket.close()
                                }
                            }
                        }

                    }
                }
            }
        }
        return null
    }


    @ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startScanning()
        setContent {
            CrescendoTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MediaPlayerScreen(this@MainActivity)
                }
            }
        }
    }

    private fun startScanning() {
        if (!isScanning) {
            isScanning = true
            lifecycleScope.launch {
                connection = scanLocalNetworkAndConnect(4308)
                isConnected.value = connection != null && connection!!.isConnected()
                Log.d("SOCKET", "isConnected ${isConnected.value}")
            }
        }
    }

    fun connectToIP(desiredIP: String) {
        Log.d("SOCKET", "Trying to connect to $desiredIP")
        isScanning = true // Start scanning
        connection = null // Reset the connection

        // Attempt to establish the connection
        connection = SocketConnection(desiredIP, 4308, this@MainActivity)

        lifecycleScope.launch {
            delay(500) // Add the desired delay time in milliseconds if necessary

            isConnected.value = connection?.isConnected() == true
            Log.d("SOCKET", "isConnected ${isConnected.value}")

            if (!isConnected.value) {
                Log.e("SOCKET", "Error while connecting")
                isScanning = false // End scanning
                delay(200)
                startScanning() //Start new
            }
        }
    }

    override fun onConnectionSuccess() {
        isConnected.value = true
        connection?.sendString("4")
    }

    override fun onConnectionLost() {
        isConnected.value = false
        isPlaying.value = false
        Log.e("SOCKET", "CONNECTION LOST!!")
        startScanning()


    }

    override fun onNewInfo(info: String) {
        Log.d("SOCKET", "Starting parsing info")
        try {
            val status = info[0].digitToInt()
            Log.d("PARSE", "$status")
            when (status) {
                8 -> {
                    Log.d("PARSE", "Start parse players info")
                    val dataMap = Utils.parseList(info.substring(3))
                    Log.d("PARSE", "dataMap: $dataMap")

                    dataMap.second.forEach { (playerName, playerInterface) ->
                        Log.d("PARSE", "Player: $playerName, Interface: $playerInterface")
                    }

                    currentElement = dataMap.first
                    elementList.value = dataMap.second
                    isBottomSheetOpen.value = true
                    isChoosingPlayer.value = true
                    Log.d("PARSE", "Playerlist: ${elementList.value}")
                    Log.d("PARSE", "is open: ${isBottomSheetOpen.value}")
                }

                9 -> {
                    Log.d("PARSE", "Start parse devices info")
                    val dataMap = Utils.parseList(info.substring(3))
                    Log.d("PARSE", "dataMap: $dataMap")

                    dataMap.second.forEach { (deviceName, deviceSinkID) ->
                        Log.d("PARSE", "Device: $deviceName, Sink id: $deviceSinkID")
                    }

                    currentElement = dataMap.first
                    elementList.value = dataMap.second
                    isBottomSheetOpen.value = true
                    isChoosingPlayer.value = false
                    Log.d("PARSE", "DeviceList: ${elementList.value}")
                    Log.d("PARSE", "is open: ${isBottomSheetOpen.value}")
                }
            }
        } catch (ex: java.lang.IllegalArgumentException) { //If passed not with status

            val dataMap = Utils.parseData(info)

            for ((key, value) in dataMap) {
                println("$key: $value")
                when (key) {
                    "art" -> art.value = value
                    "artist" -> artist.value = value
                    "title" -> title.value = value
                    "length" -> length.value = value
                    "pos" -> position.value = value
                    "shuffle" -> isShuffle.value = value == "1"
                    "repeat" -> repeatStatus.value = value.toInt()
                    "playing" -> isPlaying.value = value == "1"
                    "volume" -> volume.value = value.toFloat()
                }
            }

            if (artist.value == "-") artist.value = ""
            if (title.value == "-") title.value = ""

            val positionSeconds = Utils.convertTimeStringToSeconds(position.value)
            val lengthSeconds = Utils.convertTimeStringToSeconds(length.value)
            playPauseIcon.value =
                if (isPlaying.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
            shuffleIcon.value =
                if (isShuffle.value) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle
            repeatIcon.value = when (repeatStatus.value) {
                0 -> Icons.Rounded.Repeat
                1 -> Icons.Rounded.RepeatOn
                2 -> Icons.Rounded.RepeatOneOn
                else -> {
                    Icons.Rounded.Repeat
                }
            }
            volumeIcon.value =
                if (volume.value <= 0) Icons.Rounded.VolumeOff
                else if (volume.value > 0 && volume.value < 0.4) Icons.Rounded.VolumeMute
                else if (volume.value >= 0.4 && volume.value < 0.7) Icons.Rounded.VolumeDown
                else Icons.Rounded.VolumeUp


            if (updatePosition)
                positionPercentage = Utils.calculatePositionPercentage(positionSeconds, lengthSeconds)

        } catch (ex: Exception) {
            Log.e("PARSE", "Error: $ex")
        }

    }
}

@Composable
fun ConnectScreen(lifecycleOwner: LifecycleOwner, onConnect: () -> Unit, currentIP: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
    ) {
        Text(
            text = "Scanning subnet...  $currentIP",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(15.dp))
        Text(
            text = "OR",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(15.dp))
        Text(
            text = "Input IP manually",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(5.dp))
        Row {
            // TextInput for IP address
            val mainActivity = remember(lifecycleOwner) {
                lifecycleOwner as MainActivity
            }
            TextField(
                value = mainActivity.ipAddressToConnect.value,
                onValueChange = { mainActivity.ipAddressToConnect.value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(0.5f)
                    .padding(end = 16.dp)
                    .height(50.dp),
                placeholder = { Text("192.168.0.190") }
            )
            Button(onClick = {
                Log.d("CONNECTION", "Calling on connect")
                onConnect()
            }) {


                Text(text = "Connect")
            }
        }


    }
}


