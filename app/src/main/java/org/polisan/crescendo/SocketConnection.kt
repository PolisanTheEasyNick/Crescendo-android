import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polisan.crescendo.ConnectionListener
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketConnection(
    private val ipAddress: String,
    private val port: Int,
    private val connectionListener: ConnectionListener?
) {
    private val socket: Socket = Socket()
    private var reader: BufferedReader? = null

    private var isConnectionClosed = false
    private var serverAlive = true



    init {
        GlobalScope.launch(Dispatchers.IO) {

            connect()
        }
    }

    private suspend fun connect() {
        try {
            try {
                withContext(Dispatchers.IO) {
                    socket.connect(InetSocketAddress(ipAddress, port), 1000)
                }
                reader = BufferedReader(InputStreamReader(withContext(Dispatchers.IO) {
                    socket.getInputStream()
                }))

                Log.d("SOCKET", "Connected to $ipAddress:$port")
                isConnectionClosed = false

            } catch(e: ConnectException) {
                Log.d("SOCKET", "Can't connect to $ipAddress:$port")
                isConnectionClosed = true
                return
            }

            // Notify the connection success
            connectionListener?.onConnectionSuccess()

            // Launch the connection checker coroutine
            GlobalScope.launch(Dispatchers.IO) {
                while (!socket.isClosed) {
                    if (socket.isClosed || !socket.isConnected) {
                        Log.e("SOCKET", "Connection to $ipAddress:$port lost.")
                        withContext(Dispatchers.Main) {
                            connectionListener?.onConnectionLost()
                        }
                        break
                    }

                    try {
                        sendInt(0)
                    } catch (e: Exception) {
                        Log.e("SOCKET", "Exception: ${e.message}")
                        e.printStackTrace()
                    }

                    delay(1000)
                }
            }
// Launch the response waiting coroutine
            GlobalScope.launch(Dispatchers.IO) {
                val buffer = CharArray(1024)
                val responseBuilder = StringBuilder()

                while (!socket.isClosed) {
                    try {

                        if (reader?.ready() == true) {
                            val bytesRead = reader?.read(buffer)
                            if (bytesRead != null && bytesRead != -1) {
                                val receivedData = buffer.sliceArray(0 until bytesRead)
                                responseBuilder.append(receivedData)
                            }

                            val response = responseBuilder.toString()
                            if (response.isNotEmpty()) {
                                Log.d("SOCKET", "Received response: $response")
                                connectionListener?.onNewInfo(response)
                                responseBuilder.clear()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SOCKET", "Exception: ${e.message}")
                        e.printStackTrace()
                    }

                    delay(100)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendString(message: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val dOut = DataOutputStream(socket.getOutputStream())
                Log.d("SOCKET", "Sending message $message")
                dOut.writeUTF(message)
                dOut.flush()
            } catch (e: SocketException) {
                Log.e("SOCKET", "Exception: ${e.message}")
                e.printStackTrace()
                socket.close()
                connectionListener?.onConnectionLost()
            }
        }
    }

    fun sendInt(int: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val dOut = DataOutputStream(socket.getOutputStream())
                if(int != 0)
                  Log.d("SOCKET", "Sending int $int")
                val valueBytes = ByteArray(4)
                val buffer = ByteBuffer.allocate(4)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.putInt(int)
                System.arraycopy(buffer.array(), 0, valueBytes, 0, 4)
                dOut.write(valueBytes)
                dOut.flush()
            } catch (e: SocketException) {
                Log.e("SOCKET", "Exception: ${e.message}")
                e.printStackTrace()
                socket.close()
                connectionListener?.onConnectionLost()
            }
        }
    }

    fun receive(): String? {
        return runCatching {
            reader?.readLine()
        }.getOrElse {
            null
        }
    }

    fun disconnect() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                reader?.close()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isConnected(): Boolean {
        return socket.isConnected
    }

    fun isServerAlive(): Boolean {
        return serverAlive
    }


}
