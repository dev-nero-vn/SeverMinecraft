package vn.nero.remote.host
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import vn.nero.remote.shared.PairingCode

class HostActivity:AppCompatActivity(){
 private lateinit var codeInput:EditText
 private lateinit var state:TextView
 private val prefs by lazy { getSharedPreferences("nero-host",MODE_PRIVATE) }
 private val projection=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result->
  if(result.resultCode==Activity.RESULT_OK && result.data!=null){
   val code=PairingCode.normalize(codeInput.text.toString()); prefs.edit().putString("pairing_code",code).apply()
   startForegroundService(Intent(this,HostForegroundService::class.java).putExtra(HostForegroundService.EXTRA_CODE,code)
    .putExtra(HostForegroundService.EXTRA_RESULT_CODE,result.resultCode).putExtra(HostForegroundService.EXTRA_RESULT_DATA,result.data))
   state.text="Host đang chạy. Có thể đóng giao diện; giữ thông báo dịch vụ."
  } else state.text="Bạn chưa cấp quyền chia sẻ màn hình."
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(24,28,24,24);setBackgroundColor(0xFF09111F.toInt())}
  root.addView(TextView(this).apply{text="NERO REMOTE · HOST";textSize=22f;setTextColor(-1)})
  codeInput=EditText(this).apply{hint="Mã riêng (ít nhất 12 ký tự)";singleLine=true;setTextColor(-1);setHintTextColor(0xFF93A5BF.toInt());setText(prefs.getString("pairing_code",PairingCode.generate()))}
  root.addView(codeInput,LinearLayout.LayoutParams(-1,60))
  root.addView(Button(this).apply{text="Lưu mã riêng";setOnClickListener{val code=PairingCode.normalize(codeInput.text.toString());if(PairingCode.isValid(code)){prefs.edit().putString("pairing_code",code).apply();state.text="Đã lưu mã Host."}else state.text="Mã cần ít nhất 12 ký tự hợp lệ."}})
  root.addView(Button(this).apply{text="Khởi động Host và chia sẻ màn hình";setOnClickListener{val code=PairingCode.normalize(codeInput.text.toString());if(!PairingCode.isValid(code)){state.text="Hãy đặt mã có ít nhất 12 ký tự.";return@setOnClickListener};prefs.edit().putString("pairing_code",code).apply();val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;projection.launch(manager.createScreenCaptureIntent())}})
  root.addView(Button(this).apply{text="Mở cài đặt Trợ năng";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}})
  root.addView(Button(this).apply{text="Dừng Host";setOnClickListener{stopService(Intent(this@HostActivity,HostForegroundService::class.java));state.text="Đã gửi yêu cầu dừng Host."}})
  state=TextView(this).apply{text="Bật Nero Remote trong Trợ năng rồi khởi động Host.";textSize=14f;setTextColor(0xFF9BB0CC.toInt())}
  root.addView(state);setContentView(root)
 }
}