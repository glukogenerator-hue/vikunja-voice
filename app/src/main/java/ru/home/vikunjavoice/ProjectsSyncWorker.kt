package ru.home.vikunjavoice

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Фоновое обновление кэша проектов (WorkManager). */
class ProjectsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.serverUrl.isBlank() || prefs.token.isBlank()) {
            return Result.success() // ещё не настроено — нечего обновлять
        }
        return try {
            val projects = VikunjaApi(prefs.serverUrl, prefs.token).projects()
            ProjectsCache(applicationContext).save(prefs.serverUrl, projects)
            Result.success()
        } catch (e: Exception) {
            // Пара повторов с бэкоффом, потом сдаёмся до следующего периода.
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
