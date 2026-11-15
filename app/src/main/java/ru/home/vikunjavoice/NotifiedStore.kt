package ru.home.vikunjavoice

import android.content.Context

/** Помнит, о каких задачах уже уведомляли (ключ — id + срок). */
class NotifiedStore(context: Context) {
    private val sp = context.getSharedPreferences("vikunja_notified", Context.MODE_PRIVATE)

    fun isNotified(taskId: Long, due: String): Boolean =
        sp.getStringSet("keys", emptySet()).orEmpty().contains(key(taskId, due))

    fun mark(taskId: Long, due: String) {
        val keys = sp.getStringSet("keys", emptySet()).orEmpty().toMutableSet()
        keys.add(key(taskId, due))
        // держим только последние 300 записей
        val trimmed = keys.toList().takeLast(300).toSet()
        sp.edit().putStringSet("keys", trimmed).apply()
    }

    private fun key(taskId: Long, due: String) = "$taskId@$due"
}
