package org.polisan.crescendo

import SocketConnection
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.absoluteValue


interface ConnectionListener {
    fun onConnectionSuccess()
    fun onConnectionLost()
    fun onNewInfo(info: String)
}

private fun parseData(input: String): Map<String, String> {
    val dataMap: MutableMap<String, String> = HashMap()

    val pairs = input.split("||")
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

private fun parsePlayerInfo(input: String): Pair<Int, List<Pair<String, String>>> {
    val players = mutableListOf<Pair<String, String>>()
    Log.d("PARSE", "Starting parsing player info. input: $input")
    val items = input.split("||")
    var index = -1
    // Ensure that the number of items is at least 3 (index + player name + player interface)
    Log.d("PARSE", "Items: $items")
    if (items.size >= 3) {
        index = items[0].toInt()
        for (i in 1 until items.size - 1 step 2) {
            val playerName = items[i]
            val playerInterface = items[i + 1]
            players.add(playerName to playerInterface)
        }
    }
    Log.d("PARSE", "Index fetched: $index")
    Log.d("PARSE", "Players fetched: $players")
    return index to players
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
    var updatePosition = true

    var currentPlayer = -1
    var playersList = mutableStateOf(emptyList<Pair<String, String>>())
    var isBottomSheetOpen = mutableStateOf(false)


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


    @ExperimentalMaterial3Api
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
        try {
            val status = info[0].digitToInt()
            Log.d("PARSE", "$status")
            when (status) {
                8 -> {
                    Log.d("PARSE", "Start parse player info")
                    val dataMap = parsePlayerInfo(info.substring(3))
                    Log.d("PARSE", "dataMap: $dataMap")

                    dataMap.second.forEach { (playerName, playerInterface) ->
                        Log.d("PARSE", "Player: $playerName, Interface: $playerInterface")
                    }
                    //spawn bottom sheet with radiobuttons with players names and confirm button

                    currentPlayer = dataMap.first
                    playersList.value = dataMap.second
                    isBottomSheetOpen.value = true
                    Log.d("PARSE", "Playerlist: ${playersList.value}")
                    Log.d("PARSE", "is open: ${isBottomSheetOpen.value}")


                }
            }
        } catch (ex: java.lang.IllegalArgumentException) { //If passed not with status

            val dataMap = parseData(info)

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
            Log.e("SOCKET", "isPlaying: ${isPlaying.value}")
            var positionSeconds = convertTimeStringToSeconds(position.value)
            var lengthSeconds = convertTimeStringToSeconds(length.value)
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
            if (updatePosition)
                positionPercentage = calculatePositionPercentage(positionSeconds, lengthSeconds)
        } catch (ex: Exception) {
            Log.e("PARSE", "Error: $ex")
        }

    }
}

@Composable
@ExperimentalMaterial3Api
fun MediaPlayerScreen(lifecycleOwner: LifecycleOwner) {
    val mainActivity = remember(lifecycleOwner) {
        lifecycleOwner as MainActivity
    }

    val scope = rememberCoroutineScope()

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

            if (mainActivity.isBottomSheetOpen.value) {
                var bottomSheetState = rememberModalBottomSheetState()
                PlayerSelectionBottomSheet(
                    players = mainActivity.playersList.value,
                    onPlayerSelected = { playerID ->
                        Log.d("PARSE", "SELECTED PLAYER: $playerID")
                        scope.launch {
                            bottomSheetState.hide()
                        }.invokeOnCompletion {
                            mainActivity.isBottomSheetOpen.value =
                                false
                        }
                        mainActivity.connection?.sendInt(("9$playerID").toInt())
                    },
                    onDismissClick = {
                        scope.launch {
                            bottomSheetState.hide()
                        }.invokeOnCompletion {
                            mainActivity.isBottomSheetOpen.value =
                                false
                        }
                    },
                    state = bottomSheetState,
                    currentPlayer = mainActivity.currentPlayer
                )
            }

        } else {
            ConnectButton(onConnect = { mainActivity.connectToSocket() })
        }
    }
}

@Composable
fun PlayerControls(
    lifecycleOwner: LifecycleOwner,
    artist: String,
    title: String,
    position: String,
    positionPercentage: Float,
    length: String,
    playPauseIcon: ImageVector,
    shuffleIcon: ImageVector,
    repeatIcon: ImageVector
) {
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
                    val newPos =
                        (convertTimeStringToSeconds(length) * mainActivity.positionPercentage).toInt()
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
                        mainActivity.connection?.sendInt(8) //get and choose player

                }) {
                    Icon(
                        Icons.Rounded.Speaker,
                        contentDescription = "ChoosePlayer"
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(5) //toggle shuffle
                }) {
                    Icon(
                        shuffleIcon,
                        contentDescription = "Shuffle"
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(1)
                }) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Previous"
                    )
                }
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(2)
                }) {
                    Icon(
                        playPauseIcon,
                        contentDescription = "Play/Pause"
                    )
                }
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(3)
                }) {
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = "Next"
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(6) //toggle repeat
                }) {
                    Icon(
                        repeatIcon,
                        contentDescription = "Repeat"
                    )
                }
                IconButton(onClick = {
                    if (mainActivity.connection?.isConnected() == true)
                        mainActivity.connection?.sendInt(9) //Choose output device
                }) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = "ChooseOutputDevice"
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

@Composable
@ExperimentalMaterial3Api
fun PlayerSelectionBottomSheet(
    players: List<Pair<String, String>>,
    onPlayerSelected: (Int) -> Unit,
    onDismissClick: () -> Unit,
    state: SheetState,
    currentPlayer: Int
) {
    var selectedPlayer by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedIndex by remember { mutableStateOf(currentPlayer) }

    ModalBottomSheet(
        sheetState = state,
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Player",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Column {
                    players.forEachIndexed { index, (playerName, _) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .selectable(index == selectedIndex, onClick = {
                                    selectedPlayer = players[index]
                                    selectedIndex = index
                                })
                        ) {
                            RadioButton(
                                selected = index == selectedIndex,
                                onClick = {
                                    selectedPlayer = players[index]
                                    selectedIndex = index
                                },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Text(
                                text = playerName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            selectedPlayer?.let { onPlayerSelected(selectedIndex) }
                        },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(text = "Confirm")
                    }
                }
            }
        },
        onDismissRequest = {
            onDismissClick()
        },
    )
}

