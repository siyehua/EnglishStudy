package com.siyehua.egnlishstudy.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.siyehua.egnlishstudy.data.ManualFavorite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CaptureTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = extractText(intent)?.trim().orEmpty()
        if (text.isEmpty()) {
            finish()
            return
        }

        Toast.makeText(this, "已添加到单词本", Toast.LENGTH_SHORT).show()
        val appContext = applicationContext
        CaptureWork.scope.launch {
            runCatching { ManualFavorite.add(appContext, text) }
        }
        finish()
    }

    private fun extractText(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }
    }
}

private object CaptureWork {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
