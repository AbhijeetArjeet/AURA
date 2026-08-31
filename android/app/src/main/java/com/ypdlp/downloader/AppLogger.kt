package com.ypdlp.downloader

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel { INFO, DEBUG, WARN, ERROR, COMMAND }

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "App",
    val message: String
)

object AppLogger {
    private const val MAX_LOGS = 500
    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(
            LogEntry(
                level = LogLevel.INFO,
                tag = "8MAN",
                message = "★ 8MAN Dev Console initialized. 'Youth is a lie. It is evil.' — Hachiman Hikigaya"
            ),
            LogEntry(
                level = LogLevel.DEBUG,
                tag = "System",
                message = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
            )
        )
    )
    val logs = _logs.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)
        when (level) {
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
            LogLevel.COMMAND -> Log.d(tag, "> $message")
        }
        _logs.update { current ->
            val updated = current + entry
            if (updated.size > MAX_LOGS) updated.takeLast(MAX_LOGS) else updated
        }
    }

    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(LogLevel.ERROR, tag, msg)
    fun cmd(tag: String, msg: String) = log(LogLevel.COMMAND, tag, msg)

    fun clear() {
        _logs.value = listOf(
            LogEntry(level = LogLevel.INFO, tag = "8MAN", message = "Console logs cleared.")
        )
    }

    fun getDiagnosticReport(context: Context): String {
        val sb = StringBuilder()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.appendLine("==========================================")
        sb.appendLine("   YPDlp / 8MAN System Diagnostic Report  ")
        sb.appendLine("==========================================")
        sb.appendLine("Generated At : ${df.format(Date())}")
        sb.appendLine("Device Model : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
        sb.appendLine("Android OS   : Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABIs         : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("Engine Ready : ${YPDlpApp.isStandaloneEngineReady}")

        val downloadDir = DownloadService.getDownloadDirectory(context)
        sb.appendLine("Download Path: ${downloadDir.absolutePath}")
        sb.appendLine("Storage Free : ${formatBytes(downloadDir.freeSpace)} / ${formatBytes(downloadDir.totalSpace)}")
        sb.appendLine("------------------------------------------")
        sb.appendLine("RECENT CONSOLE LOGS (${_logs.value.size} entries):")
        sb.appendLine("------------------------------------------")
        _logs.value.forEach { entry ->
            sb.appendLine("[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}")
        }
        sb.appendLine("==========================================")
        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            Locale.US,
            "%.2f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
}
