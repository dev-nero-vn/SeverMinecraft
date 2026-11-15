package vn.nero.remote.host
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import org.json.JSONObject

class RemoteAccessibilityService:AccessibilityService(){
 override fun onServiceConnected(){current=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){}
 override fun onInterrupt(){}
 override fun onDestroy(){if(current===this)current=null;super.onDestroy()}
 private fun perform(command:JSONObject){
  val metrics:DisplayMetrics=resources.displayMetrics;val w=metrics.widthPixels.toFloat();val h=metrics.heightPixels.toFloat()
  when(command.optString("action")){
   "tap"->{val x=command.optDouble("x",0.5).toFloat()*w;val y=command.optDouble("y",0.5).toFloat()*h;val p=Path().apply{moveTo(x,y);lineTo(x+1,y+1)};dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,90)).build(),null,null)}
   "swipe"->{val x1=command.optDouble("x1",0.5).toFloat()*w;val y1=command.optDouble("y1",0.5).toFloat()*h;val x2=command.optDouble("x2",0.5).toFloat()*w;val y2=command.optDouble("y2",0.5).toFloat()*h;val p=Path().apply{moveTo(x1,y1);lineTo(x2,y2)};dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,command.optLong("duration",300))).build(),null,null)}
   "home"->performGlobalAction(GLOBAL_ACTION_HOME)
   "back"->performGlobalAction(GLOBAL_ACTION_BACK)
   "recent"->performGlobalAction(GLOBAL_ACTION_RECENTS)
   "text"->{val node=rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);val args=Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,command.optString("value"))};node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)}
  }
 }
 companion object{@Volatile private var current:RemoteAccessibilityService?=null;fun dispatch(command:JSONObject){current?.perform(command)}}
}