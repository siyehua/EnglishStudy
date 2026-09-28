package com.siyehua.egnlishstudy.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLog {

    private const val MAX_BYTES = 512 * 1024L
    private const val FILE_NAME = "app.log"
    private const val OLD_FILE_NAME = "app.log.1"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "app-log").apply { isDaemon = true }
    }
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        logFile = File(dir, FILE_NAME)
        write("---- session start sdk=${Build.VERSION.SDK_INT} ----")
    }

    fun log(tag: String, message: String) {
        write("[$tag] $message")
    }

    fun file(context: Context): File? {
        init(context)
        return logFile
    }

    fun text(context: Context): String {
        val file = file(context) ?: return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun clear(context: Context) {
        init(context)
        logFile?.let { runCatching { it.writeText("") } }
    }

    private fun write(line: String) {
        val target = logFile ?: return
        val stamped = "${timeFormat.format(Date())} $line"
        executor.execute {
            runCatching {
                if (target.length() > MAX_BYTES) {
                    val old = File(target.parentFile, OLD_FILE_NAME)
                    if (old.exists()) old.delete()
                    target.renameTo(old)
                    target.createNewFile()
                }
                target.appendText(stamped + "\n")
            }
        }
    }
}
