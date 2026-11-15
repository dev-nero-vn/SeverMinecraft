package vn.nero.remote.controller
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import vn.nero.remote.shared.*

class ControllerActivity : AppCompatActivity() {
 private lateinit var status: TextView
 private lateinit var codeInput: EditText
 private lateinit var video: SurfaceViewRenderer
 private var rendererReady = false
 private var signal: RealtimeSignalClient? = null
 private var rtc: RtcSession? = null
 private var downX = 0f
 private var downY = 0f

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,16); setBackgroundColor(0xFF09111F.toInt()) }
  val title = TextView(this).apply { text="NERO REMOTE · CONTROLLER"; textSize=20f; setTextColor(0xFFF4F7FF.toInt()); gravity=Gravity.CENTER_VERTICAL }
  codeInput = EditText(this).apply { hint="Nhập mã Host (12 ký tự trở lên)"; singleLine=true; setTextColor(-1); setHintTextColor(0xFF93A5BF.toInt()) }
  val connect = Button(this).apply { text="Kết nối"; setOnClickListener { connectToHost() } }
  val resume = Button(this).apply { text="Tiếp tục chia sẻ màn hình"; setOnClickListener { rtc?.sendControl(JSONObject().put("action","resume")); signal?.send("resume") } }
  val navigation = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  navigation.addView(Button(this).apply { text="Home"; setOnClickListener { rtc?.sendControl(JSONObject().put("action","home")) } }, LinearLayout.LayoutParams(0,-2,1f))
  navigation.addView(Button(this).apply { text="Back"; setOnClickListener { rtc?.sendControl(JSONObject().put("action","back")) } }, LinearLayout.LayoutParams(0,-2,1f))
  status=TextView(this).apply { text="Nhập mã thiết bị ở nhà để kết nối."; setTextColor(0xFF9BB0CC.toInt()); textSize=13f }
  video=SurfaceViewRenderer(this)
  root.addView(title); root.addView(codeInput,LinearLayout.LayoutParams(-1,58)); root.addView(connect); root.addView(resume); root.addView(navigation); root.addView(status,LinearLayout.LayoutParams(-1,36)); root.addView(video,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  video.setOnTouchListener { _, event -> handleTouch(event); true }
 }

 private fun connectToHost() {
  val code=PairingCode.normalize(codeInput.text.toString())
  if(!PairingCode.isValid(code)){ status.text="Mã cần ít nhất 12 ký tự hợp lệ."; return }
  cleanup()
  status.text="Đang kết nối máy chủ..."
  lateinit var client: RealtimeSignalClient
  client=RealtimeSignalClient(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_KEY,code,
   onReady={ client.send("connect_request",JSONObject().put("client","nero-controller")); rtc?.startAsController() },
   onEvent={ event,payload -> rtc?.handleSignal(event,payload) },
   onStatus={ message -> runOnUiThread { status.text=message } })
  signal=client
  val session=RtcSession(this,client,false,onRemoteVideo={ track:VideoTrack ->
   runOnUiThread {
    if(!rendererReady){ val ctx=rtc?.eglContext ?: return@runOnUiThread; video.init(ctx,null); rendererReady=true }
    track.addSink(video); status.text="Đang nhận màn hình Host"
   }
  })
  rtc=session
  client.connect()
 }
 private fun handleTouch(event:MotionEvent){
  val w=video.width.coerceAtLeast(1); val h=video.height.coerceAtLeast(1)
  when(event.actionMasked){
   MotionEvent.ACTION_DOWN -> { downX=event.x/w; downY=event.y/h }
   MotionEvent.ACTION_UP -> {
    val x=(event.x/w).coerceIn(0f,1f); val y=(event.y/h).coerceIn(0f,1f)
    if(kotlin.math.abs(x-downX)<0.025f && kotlin.math.abs(y-downY)<0.025f) rtc?.sendControl(JSONObject().put("action","tap").put("x",x).put("y",y))
    else rtc?.sendControl(JSONObject().put("action","swipe").put("x1",downX).put("y1",downY).put("x2",x).put("y2",y).put("duration",300))
   }
  }
 }
 private fun cleanup(){ signal?.close(); signal=null; rtc?.dispose(); rtc=null }
 override fun onDestroy(){ if(::video.isInitialized && rendererReady) video.release(); cleanup(); super.onDestroy() }
}