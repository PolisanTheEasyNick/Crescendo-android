package org.polisan.crescendo

import SocketConnection
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.polisan.crescendo.ui.theme.CrescendoTheme

interface ConnectionListener {
    fun onConnectionSuccess()
    fun onConnectionLost()
}
class MainActivity : ComponentActivity(), ConnectionListener {
    private val isConnected = mutableStateOf(false)
    var connection: SocketConnection? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectToSocket()
        setContent {
            CrescendoTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (isConnected.value) {
                        ButtonRow(this)
                    } else {
                        ConnectButton(onConnect = { connectToSocket() })
                    }
                }
            }
        }

    }

    private fun connectToSocket() {
        lifecycleScope.launch {
            connection = SocketConnection("10.0.2.2", 4308, this@MainActivity)
            isConnected.value = connection!!.isConnected()
            Log.d("SOCKET", "isConnected ${isConnected.value}")
        }
    }

    override fun onConnectionSuccess() {
        isConnected.value = true
    }

    override fun onConnectionLost() {
        isConnected.value = false
    }
}


@SuppressLint("SuspiciousIndentation")
@Composable
fun ButtonRow(lifecycleOwner: LifecycleOwner) {
    val mainActivity = remember(lifecycleOwner) {
        lifecycleOwner as MainActivity
    }
    Row(Modifier.padding(20.dp)) {
        IconButton(onClick = {
            if(mainActivity.connection?.isConnected() == true)
                //mainActivity.connection?.sendString("Previous")
                mainActivity.connection?.sendByte(1)
        }) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "Previous"
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        IconButton(onClick = {
            if(mainActivity.connection?.isConnected() == true)
            //mainActivity.connection?.sendString("PlayPause")
                mainActivity.connection?.sendByte(2)
        }) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "PlayPause"
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        IconButton(onClick = {
            if(mainActivity.connection?.isConnected() == true)
                //mainActivity.connection?.sendString("Next")
                mainActivity.connection?.sendByte(3)
        }) {
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = "Next"
            )
        }

        Spacer(modifier = Modifier.width(2.dp))
        IconButton(onClick = {
            if(mainActivity.connection?.isConnected() == true)
            //mainActivity.connection?.sendString("Get")
                mainActivity.connection?.sendByte(4)
        }) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Get"
            )
        }
    }
}

@Composable
fun ConnectButton(onConnect: () -> Unit) {
    Button(onClick = onConnect) {
        Text(text = "Connect")
    }
}