package org.polisan.crescendo

import SocketConnection
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polisan.crescendo.ui.theme.CrescendoTheme
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.absoluteValue


interface ConnectionListener {
    fun onConnectionSuccess()
    fun onConnectionLost()
    fun onNewInfo(info: String)
}

private fun parseData(input: String): Map<String, String>? {
    val dataMap: MutableMap<String, String> = HashMap()

    val pairs = input.split("||")
        .filter { it.isNotBlank() }
        .toTypedArray()

    var i = 0
    while (i < pairs.size - 1) {
        val key = pairs[i]
        val value = pairs[i + 1]
        dataMap[key] = value
        i += 2
    }
    return dataMap
}

private fun convertTimeStringToSeconds(timeString: String): Int {
    val parts = timeString.split(":")
    val seconds = parts.last().toInt()

    val minutes = if (parts.size > 1) {
        parts[parts.size - 2].toInt()
    } else {
        0
    }

    val hours = if (parts.size > 2) {
        parts[parts.size - 3].toInt()
    } else {
        0
    }

    return hours * 3600 + minutes * 60 + seconds
}

private fun calculatePositionPercentage(positionSeconds: Int, lengthSeconds: Int): Float {
    val positionFloat = positionSeconds.toFloat()
    val lengthFloat = lengthSeconds.toFloat()
    return if (lengthFloat > 0f) {
        positionFloat / lengthFloat
    } else {
        0f
    }
}



class MainActivity : ComponentActivity(), ConnectionListener {
    val isConnected = mutableStateOf(false)
    var connection: SocketConnection? = null
    var art = mutableStateOf("")
    var length = mutableStateOf("")
    var position = mutableStateOf("")
    var positionPercentage by mutableStateOf(0.0f)
    var title = mutableStateOf("")
    var artist = mutableStateOf("")
    var isShuffle = mutableStateOf(false)
    var repeatStatus = mutableStateOf(-1)
    var isPlaying = mutableStateOf(true)
    var playPauseIcon = mutableStateOf(if (isPlaying.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow)
    var shuffleIcon = mutableStateOf(if (isShuffle.value) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle)
    var repeatIcon = mutableStateOf(
        when(repeatStatus.value) {
            0 -> Icons.Rounded.Repeat
            1 -> Icons.Rounded.RepeatOn
            2 -> Icons.Rounded.RepeatOneOn
            else -> {Icons.Rounded.Repeat}
        }
    )
    var updatePosition = true

    suspend fun scanLocalNetworkAndConnect(port: Int): SocketConnection? {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var baseIp: String
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
                    val ipAddress = "$baseIp.$i"
                    val socket = Socket()
                    try {
                        Log.d("SOCKET", "Trying to connect to $ipAddress:$port")
                        // Connect to the IP address and port
                        withContext(Dispatchers.IO) {
                            socket.connect(InetSocketAddress(ipAddress, port), 50)
                            Log.d("SOCKET", "Found server at $ipAddress:$port")
                        }
                        // If the connection is successful, create a SocketConnection instance and return it
                        return SocketConnection(ipAddress, port, this@MainActivity)
                    } catch (e: Exception) {
                        // Ignore the exception and continue with the next IP address
                    } finally {
                        // Close the socket if it was opened but not connected
                        if (!socket.isConnected) {
                            socket.close()
                        }
                    }

                }
            }
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectToSocket()
        setContent {
            CrescendoTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MediaPlayerScreen(this@MainActivity)
                }
            }
        }
    }

    fun connectToSocket() {
        lifecycleScope.launch {
            connection = scanLocalNetworkAndConnect(4308)
            isConnected.value = connection != null && connection!!.isConnected()
            Log.d("SOCKET", "isConnected ${isConnected.value}")
        }
    }

    override fun onConnectionSuccess() {
        isConnected.value = true
        connection?.sendInt(4)
    }

    override fun onConnectionLost() {
        isConnected.value = false
        isPlaying.value = false
        Log.e("SOCKET", "CONNECTION LOST!!")
    }

    override fun onNewInfo(info: String) {
        Log.d("SOCKET", "Starting parsing info")
        val dataMap = parseData(info)

        if (dataMap != null) {
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
                }
            }
        } else {
            Log.d("SOCKET", "Datamap is null")
        }
        Log.e("SOCKET", "isPlaying: ${isPlaying.value}")
        var positionSeconds = convertTimeStringToSeconds(position.value)
        var lengthSeconds = convertTimeStringToSeconds(length.value)
        playPauseIcon.value = if (isPlaying.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
        shuffleIcon.value = if (isShuffle.value) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle
        repeatIcon.value = when(repeatStatus.value) {
                0 -> Icons.Rounded.Repeat
                1 -> Icons.Rounded.RepeatOn
                2 -> Icons.Rounded.RepeatOneOn
                else -> {Icons.Rounded.Repeat}
            }
        if(updatePosition)
            positionPercentage = calculatePositionPercentage(positionSeconds, lengthSeconds)
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun MediaPlayerScreen(lifecycleOwner: LifecycleOwner) {
    val mainActivity = remember(lifecycleOwner) {
        lifecycleOwner as MainActivity
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (mainActivity.isConnected.value) {
            PlayerControls(
                mainActivity,
                mainActivity.artist.value,
                mainActivity.title.value,
                mainActivity.position.value,
                mainActivity.positionPercentage,
                mainActivity.length.value,
                mainActivity.playPauseIcon.value,
                mainActivity.shuffleIcon.value,
                mainActivity.repeatIcon.value
            )
        } else {
            ConnectButton(onConnect = { mainActivity.connectToSocket() })
        }
    }
}

@Composable
fun PlayerControls(lifecycleOwner: LifecycleOwner, artist: String, title: String, position: String, positionPercentage: Float, length: String, playPauseIcon: ImageVector, shuffleIcon: ImageVector, repeatIcon: ImageVector) {
    var mainActivity = remember(lifecycleOwner) {
        lifecycleOwner as MainActivity
    }

    val animatedProgress by animateFloatAsState(
        targetValue = positionPercentage.absoluteValue,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxHeight(),

    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "$artist - $title")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = position)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = length)
            }
            Slider(
                value = animatedProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(),
                onValueChange = {
                    mainActivity.updatePosition = false
                    mainActivity.positionPercentage = it
                                },
                onValueChangeFinished = {
                    val newPos = (convertTimeStringToSeconds(length) * mainActivity.positionPercentage).toInt()
                    val toSend = "7$newPos".toInt()
                    Log.e("SOCKET", "SENDING $toSend")
                    mainActivity.connection?.sendInt(toSend)
                    mainActivity.updatePosition = true
                                        },
                //color = MaterialTheme.colorScheme.primary,
                //trackColor = MaterialTheme.colorScheme.inversePrimary

            )
            Row(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(5) //toggle shuffle
                }) {
                    Icon(
                        shuffleIcon,
                        contentDescription = "Shuffle"
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(1)
                }) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Previous"
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(2)
                }) {
                    Icon(
                        playPauseIcon,
                        contentDescription = "Play/Pause"
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(3)
                }) {
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = "Next"
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(6) //toggle repeat
                }) {
                    Icon(
                        repeatIcon,
                        contentDescription = "Repeat"
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectButton(onConnect: () -> Unit) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize(),
        ) {
        Button(onClick = onConnect) {
            Text(text = "Connect")
        }
    }
}