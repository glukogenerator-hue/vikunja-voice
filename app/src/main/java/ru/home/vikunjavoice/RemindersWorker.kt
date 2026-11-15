package ru.home.vikunjavoice

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Проверяет близкие сроки и показывает уведомления (раз в 30 минут). */
class RemindersWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.serverUrl.isBlank() || prefs.token.isBlank()) return Result.success()

        val now = Instant.now()
        val horizon = now.plusSeconds(35 * 60)
        return try {
            val tasks = VikunjaApi(prefs.serverUrl, prefs.token).tasksDueBefore(horizon.toString())
            val projects = ProjectsCache(applicationContext)
                .load(prefs.serverUrl)
                .associateBy { it.id }
            val store = NotifiedStore(applicationContext)
            val format = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

            tasks.forEach { task ->
                val due = runCatching { Instant.parse(task.dueDate) }.getOrNull()
                    ?: return@forEach
                // Старое просроченное не спамим — только то, что наступило недавно.
                if (due.isBefore(now.minusSeconds(2 * 3600))) return@forEach
                if (store.isNotified(task.id, task.dueDate)) return@forEach

                val dueText = due.atZone(ZoneId.systemDefault()).format(format)
                val project = projects[task.projectId]?.title
                val suffix = project?.let { " · $it" }.orEmpty()
                val body = if (due.isBefore(now)) "Просрочено: $dueText$suffix"
                else "Срок $dueText$suffix"

                Notifications.notifyDue(applicationContext, task.id, task.title, body)
                store.mark(task.id, task.dueDate)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
