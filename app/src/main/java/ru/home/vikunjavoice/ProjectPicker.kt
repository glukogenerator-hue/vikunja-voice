package ru.home.vikunjavoice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Быстрая смена проекта: список приходит с сервера по сохранённым адресу и токену.
 * Полные настройки (адрес/токен) — кнопка «Настройки».
 */
@Composable
fun ProjectPickerDialog(
    prefs: Prefs,
    onDismiss: () -> Unit,
    onPick: (VikunjaProject) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val cache = remember { ProjectsCache(context.applicationContext) }
    var projects by remember { mutableStateOf(cache.load(prefs.serverUrl)) }
    var loading by remember { mutableStateOf(projects.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Сначала показываем кэш (без ожидания), затем quietly обновляем его.
        try {
            val fresh = VikunjaApi(prefs.serverUrl, prefs.token).projects()
            projects = fresh
            cache.save(prefs.serverUrl, fresh)
            error = null
        } catch (e: Exception) {
            if (projects.isEmpty()) {
                error = e.message ?: "Не удалось получить проекты"
            }
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.CardBg,
        titleContentColor = Palette.TextMain,
        textContentColor = Palette.TextSub,
        title = { Text(text = "Выбери проект", fontSize = 20.sp) },
        text = {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Palette.VioletHi,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Получаю проекты…", fontSize = 14.sp, color = Palette.TextSub)
                }

                error != null -> Text(
                    text = "Ошибка: $error",
                    fontSize = 14.sp,
                    color = Palette.Error,
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(projects) { project ->
                        val selected = project.id == prefs.projectId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) Palette.CardBorder.copy(alpha = 0.55f)
                                    else Color.Transparent
                                )
                                .clickable { onPick(project) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (selected) Palette.VioletHi else Palette.TextDim,
                                        CircleShape,
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = project.title,
                                fontSize = 16.sp,
                                color = if (selected) Palette.TextMain else Palette.TextSub,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = "Настройки", fontSize = 14.sp, color = Palette.TextSub)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Отмена", fontSize = 14.sp, color = Palette.VioletHi)
            }
        },
    )
}
