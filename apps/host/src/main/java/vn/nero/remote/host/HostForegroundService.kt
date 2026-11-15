package vn.nero.remote.host
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import org.json.JSONObject
import vn.nero.remote.shared.RealtimeSignalClient
import vn.nero.remote.shared.RtcSession

class HostForegroundService : Service() {
    private var signal: RealtimeSignalClient? = null
    private var rtc: RtcSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastRemoteActivity = SystemClock.elapsedRealtime()
    private var streamPaused = false
    private val idleCheck = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() - lastRemoteActivity >= IDLE_TIMEOUT && !streamPaused) {
                rtc?.setScreenEnabled(false)
                signal?.send("host_idle", JSONObject().put("timeoutSeconds", 300))
                streamPaused = true
                updateNotification("Đã tạm ngừng truyền hình sau 5 phút không thao tác")
            }
            handler.postDelayed(this, 5_000L)
        }
    }
    override fun onCreate() { super.onCreate(); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getStringExtra(EXTRA_CODE) ?: return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val permissionData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || permissionData == null) return START_NOT_STICKY

        val notification = buildNotification("Đang khởi động Host và chia sẻ màn hình")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        try {
            val localSignal = createSignal(code)
            val localRtc = RtcSession(this, localSignal, true, onControl = { command ->
                lastRemoteActivity = SystemClock.elapsedRealtime()
                if (streamPaused) { rtc?.setScreenEnabled(true); streamPaused = false }
                RemoteAccessibilityService.dispatch(command)
                updateNotification("Đang kết nối Controller")
            })
            rtc = localRtc
            signal = localSignal
            localRtc.startHostCapture(permissionData)
            localSignal.connect()
            lastRemoteActivity = SystemClock.elapsedRealtime()
            handler.removeCallbacks(idleCheck)
            handler.postDelayed(idleCheck, 5_000L)
            updateNotification("Host đang trực tuyến")
        } catch (error: Exception) {
            updateNotification("Không thể khởi động chia sẻ: " + (error.message ?: "lỗi không xác định"))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createSignal(code: String): RealtimeSignalClient {
        lateinit var client: RealtimeSignalClient
        client = RealtimeSignalClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, code,
            onReady = { client.send("host_ready", JSONObject().put("device", "nero-host")) },
            onEvent = { event, payload ->
                if (event == "resume") {
                    lastRemoteActivity = SystemClock.elapsedRealtime()
                    rtc?.setScreenEnabled(true)
                    streamPaused = false
                } else rtc?.handleSignal(event, payload)
            },
            onStatus = { message -> updateNotification(message) })
        return client
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nero Remote Host", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun buildNotification(text: String): Notification {
        val stop = Intent(this, HostForegroundService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 10, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause)
        return Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Nero Remote Host").setContentText(text).setOngoing(true)
            .addAction(Notification.Action.Builder(icon, "Dừng Host", pending).build()).build()
    }
    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }
    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        signal?.close(); signal = null
        rtc?.dispose(); rtc = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null

    companion object {
        const val EXTRA_CODE = "extra_code"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "vn.nero.remote.host.STOP"
        private const val CHANNEL_ID = "nero_remote_host"
        private const val NOTIFICATION_ID = 4817
        private const val IDLE_TIMEOUT = 5 * 60 * 1000L
    }
}
