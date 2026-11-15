package vn.nero.remote.shared
import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RealtimeSignalClient(
    private val projectUrl: String,
    private val publishableKey: String,
    pairingCode: String,
    private val onReady: () -> Unit,
    private val onEvent: (String, JSONObject) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val topic = PairingCode.channelName(pairingCode)
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private val refs = AtomicInteger(1)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var socket: WebSocket? = null
    @Volatile private var joined = false
    @Volatile private var closed = false
    @Volatile private var joinRef: String? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            if (closed) return
            socket?.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                .put("payload", JSONObject()).put("ref", refs.getAndIncrement().toString()).toString())
            handler.postDelayed(this, 25_000L)
        }
    }

    fun connect() {
        closed = false
        val key = URLEncoder.encode(publishableKey, "UTF-8")
        val endpoint = projectUrl.trimEnd('/') + "/realtime/v1/websocket?apikey=" + key + "&vsn=1.0.0"
        val request = Request.Builder().url(endpoint).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("Đã nối máy chủ báo hiệu")
                val ref = refs.getAndIncrement().toString()
                joinRef = ref
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("enabled", false))
                    .put("private", false)
                webSocket.send(JSONObject().put("topic", topic).put("event", "phx_join")
                    .put("payload", JSONObject().put("config", config))
                    .put("ref", ref).put("join_ref", ref).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    when (message.optString("event")) {
                        "phx_reply" -> {
                            val payload = message.optJSONObject("payload")
                            if (payload?.optString("status") == "ok" && !joined) {
                                joined = true
                                handler.post { onStatus("Sẵn sàng kết nối"); onReady() }
                                handler.removeCallbacks(heartbeat)
                                handler.postDelayed(heartbeat, 25_000L)
                            }
                        }
                        "broadcast" -> {
                            val wrapper = message.optJSONObject("payload") ?: return
                            val eventName = wrapper.optString("event")
                            val data = wrapper.optJSONObject("payload") ?: JSONObject()
                            handler.post { onEvent(eventName, data) }
                        }
                        "phx_error", "phx_close" -> handler.post { onStatus("Máy chủ báo hiệu ngắt kết nối") }
                    }
                } catch (_: Exception) {
                    handler.post { onStatus("Không đọc được gói tin báo hiệu") }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                joined = false
                joinRef = null
                handler.post { onStatus("Mất kết nối: " + (t.message ?: "lỗi mạng")) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                joined = false
                joinRef = null
                handler.post { onStatus("Đã ngắt kết nối máy chủ") }
            }
        })
    }

    fun send(event: String, payload: JSONObject = JSONObject()): Boolean {
        if (!joined || closed) return false
        val activeJoinRef = joinRef ?: return false
        val wrapper = JSONObject().put("type", "broadcast").put("event", event).put("payload", payload)
        return socket?.send(JSONObject().put("topic", topic).put("event", "broadcast")
            .put("payload", wrapper).put("ref", refs.getAndIncrement().toString())
            .put("join_ref", activeJoinRef).toString()) == true
    }

    fun close() {
        closed = true
        joined = false
        joinRef = null
        handler.removeCallbacks(heartbeat)
        socket?.close(1000, "Host/Controller stopped")
        socket = null
        http.dispatcher.executorService.shutdown()
    }
}
