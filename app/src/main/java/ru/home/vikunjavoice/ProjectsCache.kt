package ru.home.vikunjavoice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Кэш списка проектов: показываем мгновенно, даже без сети.
 * Привязан к адресу сервера, чтобы смена сервера не показывала чужие проекты.
 */
class ProjectsCache(context: Context) {
    private val sp = context.getSharedPreferences("vikunja_projects", Context.MODE_PRIVATE)

    fun load(serverUrl: String): List<VikunjaProject> {
        if (sp.getString("server", "") != normalize(serverUrl)) return emptyList()
        val raw = sp.getString("projects", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(VikunjaProject(item.getLong("id"), item.getString("title")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(serverUrl: String, projects: List<VikunjaProject>) {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(JSONObject().put("id", project.id).put("title", project.title))
        }
        sp.edit()
            .putString("server", normalize(serverUrl))
            .putString("projects", array.toString())
            .putLong("saved_at", System.currentTimeMillis())
            .apply()
    }

    fun savedAt(): Long = sp.getLong("saved_at", 0L)

    fun isStale(maxAgeMs: Long): Boolean =
        System.currentTimeMillis() - savedAt() > maxAgeMs

    private fun normalize(url: String): String = url.trim().trimEnd('/')
}
