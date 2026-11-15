package vn.nero.gameturbo

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import java.io.BufferedReader
import java.io.FileReader
import java.util.Locale

object DeviceStats {
    data class Snapshot(
        val cpuText: String,
        val ramText: String,
        val refreshText: String,
        val batteryTemperature: String
    )

    private var previousTotal = 0L
    private var previousIdle = 0L

    @Synchronized
    fun read(context: Context): Snapshot {
        val cpu = readCpuUsage()?.let { "$it%" } ?: "—"
        val memory = readMemory(context)
        val refresh = readRefreshRate(context)
        val temperature = readBatteryTemperature(context)
        return Snapshot(cpu, memory, refresh, temperature)
    }

    private fun readCpuUsage(): Int? {
        return try {
            BufferedReader(FileReader("/proc/stat")).use { reader ->
                val fields = reader.readLine().trim().split(Regex("\\s+"))
                if (fields.size < 5 || fields[0] != "cpu") return null
                val values = fields.drop(1).take(8).map { it.toLongOrNull() ?: 0L }
                val total = values.sum()
                val idle = values[3] + values.getOrElse(4) { 0L }
                if (previousTotal == 0L) {
                    previousTotal = total
                    previousIdle = idle
                    return null
                }
                val totalDelta = total - previousTotal
                val idleDelta = idle - previousIdle
                previousTotal = total
                previousIdle = idle
                if (totalDelta <= 0L) null
                else (((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0)
                    .toInt().coerceIn(0, 100)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readMemory(context: Context): String {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            if (info.totalMem <= 0L) return "—"
            val used = info.totalMem - info.availMem
            val percent = (used.toDouble() / info.totalMem.toDouble() * 100.0).toInt()
            "$percent%"
        } catch (_: Exception) {
            "—"
        }
    }

    @Suppress("DEPRECATION")
    private fun readRefreshRate(context: Context): String {
        return try {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rate = manager.defaultDisplay.refreshRate
            if (rate <= 0f) "—" else String.format(Locale.getDefault(), "%.0f Hz", rate)
        } catch (_: Exception) {
            "—"
        }
    }

    @Suppress("DEPRECATION")
    private fun readBatteryTemperature(context: Context): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
            if (tenths == Int.MIN_VALUE || tenths <= 0) "—"
            else String.format(Locale.getDefault(), "%.1f °C", tenths / 10f)
        } catch (_: Exception) {
            "—"
        }
    }
}
