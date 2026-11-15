package ru.home.vikunjavoice

import android.content.Context

/** Настройки подключения: адрес Vikunja, API-токен и выбранный проект. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vikunja_voice", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "").orEmpty()
        set(value) = sp.edit().putString("server_url", value).apply()

    var token: String
        get() = sp.getString("token", "").orEmpty()
        set(value) = sp.edit().putString("token", value).apply()

    var projectId: Long
        get() = sp.getLong("project_id", -1L)
        set(value) = sp.edit().putLong("project_id", value).apply()

    var projectTitle: String
        get() = sp.getString("project_title", "").orEmpty()
        set(value) = sp.edit().putString("project_title", value).apply()

    /** Показывать выполненные задачи в списке (по умолчанию — нет). */
    var showCompleted: Boolean
        get() = sp.getBoolean("show_completed", false)
        set(value) = sp.edit().putBoolean("show_completed", value).apply()

    val configured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank() && projectId > 0
}
