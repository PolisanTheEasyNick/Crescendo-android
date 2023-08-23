package org.polisan.crescendo

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleOwner
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
                    (Utils.convertTimeStringToSeconds(length) * mainActivity.positionPercentage).toInt()
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