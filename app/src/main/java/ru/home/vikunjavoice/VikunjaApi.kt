package ru.home.vikunjavoice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class VikunjaProject(val id: Long, val title: String)

data class VikunjaTask(
    val id: Long,
    val title: String,
    val done: Boolean,
    val dueDate: String?,
)

class ApiException(message: String) : Exception(message)

/** Минимальный клиент Vikunja API: список проектов и создание задачи. */
class VikunjaApi(serverUrl: String, private val token: String) {
    private val base = serverUrl.trim().trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun projects(): List<VikunjaProject> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/api/v1/projects")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(describe(response.code, body))
            val array = JSONArray(body)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.getLong("id")
                    // id <= 0 — виртуальные «проекты» (Favorites, Приоритетные задачи):
                    // в них нельзя создавать задачи, поэтому не показываем.
                    if (id > 0) {
                        add(VikunjaProject(id, item.optString("title")))
                    }
                }
            }
        }
    }

    suspend fun createTask(projectId: Long, title: String, dueIso: String? = null): Long =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("title", title)
            if (dueIso != null) payload.put("due_date", dueIso)
            val request = Request.Builder()
                .url("$base/api/v1/projects/$projectId/tasks")
                .header("Authorization", "Bearer $token")
                // Vikunja создаёт задачу методом PUT (POST отвечает 405).
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(describe(response.code, body))
            JSONObject(body).optLong("id")
        }
    }

    suspend fun tasks(projectId: Long): List<VikunjaTask> = withContext(Dispatchers.IO) {
        val all = mutableListOf<VikunjaTask>()
        var page = 1
        // Сервер отдаёт не больше 50 на страницу — добираем страницы, пока есть что.
        while (page <= 5) {
            val request = Request.Builder()
                .url(
                    "$base/api/v1/projects/$projectId/tasks" +
                        "?per_page=50&page=$page&sort_by=due_date&order_by=asc"
                )
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val batch = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(describe(response.code, body))
                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val rawDue = item.optString("due_date")
                        // Vikunja отдаёт «0001-01-01T00:00:00Z», когда срока нет.
                        val due = rawDue.takeIf {
                            it.isNotBlank() && !it.startsWith("0001-01-01")
                        }
                        add(
                            VikunjaTask(
                                id = item.getLong("id"),
                                title = item.optString("title"),
                                done = item.optBoolean("done"),
                                dueDate = due,
                            )
                        )
                    }
                }
            }
            all += batch
            if (batch.size < 50) break
            page++
        }
        // Незавершённые сверху, внутри — по сроку (без срока в конце).
        all.sortedWith(
            compareBy<VikunjaTask> { it.done }
                .thenBy { it.dueDate == null }
                .thenBy { it.dueDate ?: "" }
        )
    }

    /** Отметить задачу выполненной/невыполненной. */
    suspend fun setDone(taskId: Long, done: Boolean): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("done", done).toString()
        val request = Request.Builder()
            .url("$base/api/v1/tasks/$taskId")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(describe(response.code, body))
        }
    }

    suspend fun deleteTask(taskId: Long): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/api/v1/tasks/$taskId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(describe(response.code, body))
        }
    }

    private fun describe(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return when {
            !message.isNullOrBlank() -> "$message (HTTP $code)"
            code == 401 -> "Токен не принят (HTTP 401)"
            code == 403 -> "Токен без прав на задачи (HTTP 403)"
            else -> "HTTP $code"
        }
    }
}
