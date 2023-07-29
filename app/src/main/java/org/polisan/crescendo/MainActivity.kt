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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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

private fun parseList(input: String): Pair<Int, List<Pair<String, String>>> {
    val elements = mutableListOf<Pair<String, String>>()
    Log.d("PARSE", "Starting parsing player info. input: $input")
    val items = input.split("||")
    var index = -1
    Log.d("PARSE", "Items: $items")
    if (items.size >= 3) {
        index = items[0].toInt()
        for (i in 1 until items.size - 1 step 2) {
            val element = items[i]
            val id = items[i + 1]
            elements.add(element to id)
        }
    }
    Log.d("PARSE", "Index fetched: $index")
    Log.d("PARSE", "Elements fetched: $elements")
    return index to elements
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
                    val dataMap = parseList(info.substring(3))
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
                    val dataMap = parseList(info.substring(3))
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
                    "volume" -> volume.value = value.toFloat()
                }
            }

            if (artist.value == "-") artist.value = ""
            if (title.value == "-") title.value = ""

            val positionSeconds = convertTimeStringToSeconds(position.value)
            val lengthSeconds = convertTimeStringToSeconds(length.value)
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
            SongInfo(mainActivity.title.value, mainActivity.artist.value, mainActivity.art.value)
            Spacer(modifier = Modifier.height(20.dp))
            PlayerControls(
                mainActivity,
                mainActivity.position.value,
                mainActivity.positionPercentage,
                mainActivity.length.value,
                mainActivity.playPauseIcon.value,
                mainActivity.shuffleIcon.value,
                mainActivity.repeatIcon.value,
                mainActivity.volumeIcon.value
            )

            if (mainActivity.isBottomSheetOpen.value && mainActivity.isChoosingPlayer.value) {
                val bottomSheetState = rememberModalBottomSheetState()
                SelectionBottomSheet(
                    elements = mainActivity.elementList.value,
                    onSelected = { playerID ->
                        Log.d("PARSE", "SELECTED PLAYER: $playerID")
                        scope.launch {
                            bottomSheetState.hide()
                        }.invokeOnCompletion {
                            mainActivity.isBottomSheetOpen.value =
                                false
                        }
                        mainActivity.connection?.sendString("9||$playerID")
                        scope.launch {
                            delay(500)
                            mainActivity.position.value = "0"
                            mainActivity.positionPercentage = 0f
                            mainActivity.length.value = "0"
                            mainActivity.art.value = ""
                            mainActivity.artist.value = ""
                            mainActivity.title.value = ""

                            mainActivity.connection?.sendString("4")
                        }

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
                    chosenIndex = mainActivity.currentElement,
                    selectText = "Choose Player"
                )
            } else if (mainActivity.isBottomSheetOpen.value) {
                val bottomSheetState = rememberModalBottomSheetState()
                SelectionBottomSheet(
                    elements = mainActivity.elementList.value,
                    onSelected = { sinkID ->
                        Log.d("PARSE", "SELECTED DEVICE: $sinkID")
                        scope.launch {
                            bottomSheetState.hide()
                        }.invokeOnCompletion {
                            mainActivity.isBottomSheetOpen.value =
                                false
                        }
                        mainActivity.connection?.sendString("11||$sinkID")
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
                    chosenIndex = mainActivity.currentElement,
                    selectText = "Choose Device"
                )
            }

        } else {
            ConnectScreen(
                lifecycleOwner = mainActivity,
                onConnect = { mainActivity.connectToIP(mainActivity.ipAddressToConnect.value) },
                currentIP = mainActivity.currentIP.value
            )
        }
    }
}

@Composable
fun SongInfo(title: String, artist: String, artImage: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (artImage != "") {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artImage)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    loading = {
                        CircularProgressIndicator()
                    }
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun PlayerControls(
    lifecycleOwner: LifecycleOwner,
    position: String,
    positionPercentage: Float,
    length: String,
    playPauseIcon: ImageVector,
    shuffleIcon: ImageVector,
    repeatIcon: ImageVector,
    volumeIcon: ImageVector
) {
    val mainActivity = remember(lifecycleOwner) {
        lifecycleOwner as MainActivity
    }

    val animatedProgress by animateFloatAsState(
        targetValue = positionPercentage.absoluteValue,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec, label = ""
    )

    // State to track whether the volume dialog is open or closed
    val (isVolumeDialogOpen, setVolumeDialogOpen) = remember { mutableStateOf(false) }

    // State to track the current volume level (0.0 to 1.0)
    val (currentVolume, setCurrentVolume) = remember { mutableStateOf(0.5f) }


    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        // First line: Progress slider
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
                val toSend = "7||$newPos"
                Log.e("SOCKET", "SENDING $toSend")
                mainActivity.connection?.sendString(toSend)
                mainActivity.updatePosition = true
            }
        )

        // Second line: Playback control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("5") //toggle shuffle
            }) {
                Icon(
                    shuffleIcon,
                    contentDescription = "Shuffle"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("1")
            }) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Previous"
                )
            }
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("2")
            }) {
                Icon(
                    playPauseIcon,
                    contentDescription = "Play/Pause",
                )
            }
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("3")
            }) {
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = "Next"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("6") //toggle repeat
            }) {
                Icon(
                    repeatIcon,
                    contentDescription = "Repeat"
                )
            }
        }

        // Third line: Buttons for changing player and output device, and change sound button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("8") //get and choose player
            }) {
                Icon(
                    Icons.Rounded.Speaker,
                    contentDescription = "ChoosePlayer"
                )
            }
            IconButton(onClick = {
                if (mainActivity.connection?.isConnected() == true)
                    mainActivity.connection?.sendString("10") //Choose output device
            }) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = "ChooseOutputDevice"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                setVolumeDialogOpen(true)
            }) {
                Icon(
                    volumeIcon,
                    contentDescription = "Play/Pause",
                )
            }
            // Show the VolumeControlDialog when the volume button is clicked
            if (isVolumeDialogOpen) {
                VolumeControlDialog(
                    volume = currentVolume,
                    onVolumeChanged = { newVolume ->
                        setCurrentVolume(newVolume)
                        mainActivity.connection?.sendString("12||$currentVolume")
                    },
                    onClose = {
                        setVolumeDialogOpen(false)
                    }
                )
            }
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


@Composable
@ExperimentalMaterial3Api
fun SelectionBottomSheet(
    elements: List<Pair<String, String>>,
    onSelected: (Int) -> Unit,
    onDismissClick: () -> Unit,
    state: SheetState,
    chosenIndex: Int,
    selectText: String
) {
    var selectedElement by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedIndex by remember { mutableStateOf(chosenIndex) }

    ModalBottomSheet(
        sheetState = state,
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = selectText,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Column {
                    elements.forEachIndexed { index, (playerName, _) ->
                        val sinkID: Int = try {
                            elements[index].second.toInt()
                        } catch (ex: NumberFormatException) {
                            -1 //means indexing players, not devices
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .selectable(
                                    index == selectedIndex || selectedIndex == sinkID
                                ) {
                                    selectedElement = elements[index]
                                    selectedIndex = index
                                }
                        ) {
                            RadioButton(
                                selected = index == selectedIndex || selectedIndex == sinkID,
                                onClick = {
                                    selectedElement = elements[index]
                                    selectedIndex = index
                                },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Text(
                                text = playerName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (selectText == "Choose Player")
                                selectedElement?.let { onSelected(selectedIndex) }
                            else selectedElement?.let { onSelected(elements[selectedIndex].second.toInt()) }
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

@Composable
fun VolumeControlDialog(
    volume: Float, // Current volume value (0.0 to 1.0)
    onVolumeChanged: (Float) -> Unit, // Callback when the volume is changed
    onClose: () -> Unit // Callback when the dialog is closed
) {
    Dialog(
        onDismissRequest = { onClose() }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title
                Text(
                    text = "Change Volume",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally)
                )
                // Volume Slider
                Slider(
                    value = volume,
                    onValueChange = onVolumeChanged,
                    valueRange = 0f..1f,
                    steps = 100,
                    modifier = Modifier.padding(16.dp)
                )

                // Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onClose() }) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }
}

