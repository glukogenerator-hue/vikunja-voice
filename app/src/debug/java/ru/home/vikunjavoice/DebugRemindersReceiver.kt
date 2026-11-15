package ru.home.vikunjavoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** Только для debug-сборки: запускает проверку напоминаний вручную. */
class DebugRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<RemindersWorker>().build())
    }
}
