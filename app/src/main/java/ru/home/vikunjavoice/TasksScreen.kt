package ru.home.vikunjavoice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DUE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

/**
 * Второй экран: задачи выбранного проекта.
 * Тап по строке — «выполнено/не выполнено», «×» — удалить с подтверждением.
 */
@Composable
fun TasksScreen(
    prefs: Prefs,
    onOpenSettings: () -> Unit,
    settingsRevision: Int = 0,
) {
    var projectKey by remember { mutableStateOf(prefs.projectId) }
    var projectLabel by remember { mutableStateOf(prefs.projectTitle) }
    var showPicker by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf<List<VikunjaTask>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<VikunjaTask?>(null) }
    var showCompleted by remember(settingsRevision) { mutableStateOf(prefs.showCompleted) }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    val scope = rememberCoroutineScope()
    val api = remember { VikunjaApi(prefs.serverUrl, prefs.token) }

    LaunchedEffect(projectKey, reloadKey, settingsRevision) {
        loading = true
        error = null
        try {
            tasks = api.tasks(projectKey)
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить задачи"
        } finally {
            loading = false
        }
    }

    fun toggle(task: VikunjaTask) {
        val target = !task.done
        // Сразу показываем новое состояние, при ошибке откатываем.
        tasks = tasks.map { if (it.id == task.id) it.copy(done = target) else it }
        scope.launch {
            try {
                api.setDone(task.id, target)
            } catch (e: Exception) {
                tasks = tasks.map { if (it.id == task.id) it.copy(done = !target) else it }
                error = e.message ?: "Не удалось обновить задачу"
            }
        }
    }

    fun delete(task: VikunjaTask) {
        tasks = tasks.filterNot { it.id == task.id }
        scope.launch {
            try {
                api.deleteTask(task.id)
            } catch (e: Exception) {
                error = e.message ?: "Не удалось удалить задачу"
                reloadKey++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Palette.VioletHi, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ЗАДАЧИ",
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                    color = Palette.TextDim,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "⚙",
                    fontSize = 18.sp,
                    color = Palette.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = "Обновить",
                    fontSize = 13.sp,
                    color = Palette.VioletHi,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { reloadKey++ }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.CardBg)
                    .border(1.dp, Palette.CardBorder, RoundedCornerShape(18.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Проект: ", fontSize = 12.sp, color = Palette.TextSub)
                Text(
                    text = projectLabel.ifBlank { "выбрать" },
                    fontSize = 12.sp,
                    color = Palette.VioletHi,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "▾", fontSize = 11.sp, color = Palette.TextSub)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Все", filter == TaskFilter.ALL) { filter = TaskFilter.ALL }
                FilterChip("Просроченные", filter == TaskFilter.OVERDUE) {
                    filter = TaskFilter.OVERDUE
                }
                FilterChip("Сегодня", filter == TaskFilter.TODAY) { filter = TaskFilter.TODAY }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val nowInstant = Instant.now()
            val today = LocalDate.now()
            val byFilter = when (filter) {
                TaskFilter.ALL -> tasks
                TaskFilter.OVERDUE -> tasks.filter { task ->
                    !task.done && task.dueDate?.let { raw ->
                        runCatching { Instant.parse(raw) }.getOrNull()?.isBefore(nowInstant)
                    } == true
                }
                TaskFilter.TODAY -> tasks.filter { task ->
                    task.dueDate?.let { raw ->
                        runCatching { Instant.parse(raw) }.getOrNull()
                            ?.atZone(ZoneId.systemDefault())?.toLocalDate() == today
                    } == true
                }
            }
            val visibleTasks = if (showCompleted) byFilter else byFilter.filterNot { it.done }

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Palette.VioletHi,
                        strokeWidth = 2.dp,
                    )
                }

                visibleTasks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                error != null -> "Ошибка: $error"
                                tasks.isEmpty() -> "В проекте пока нет задач"
                                filter == TaskFilter.OVERDUE -> "Просроченных задач нет"
                                filter == TaskFilter.TODAY -> "На сегодня задач нет"
                                else -> "Все задачи выполнены"
                            },
                            fontSize = 14.sp,
                            color = if (error != null) Palette.Error else Palette.TextSub,
                            textAlign = TextAlign.Center,
                        )
                        if (error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { reloadKey++ }) {
                                Text(text = "Повторить", color = Palette.VioletHi)
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { toggle(task) },
                            onDelete = { toDelete = task },
                        )
                    }
                    if (error != null) {
                        item {
                            Text(
                                text = "Ошибка: $error",
                                fontSize = 12.sp,
                                color = Palette.Error,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        if (showPicker) {
            ProjectPickerDialog(
                prefs = prefs,
                onDismiss = { showPicker = false },
                onPick = { project ->
                    prefs.projectId = project.id
                    prefs.projectTitle = project.title
                    projectKey = project.id
                    projectLabel = project.title
                    showPicker = false
                },
                onOpenSettings = {
                    showPicker = false
                    onOpenSettings()
                },
            )
        }

        toDelete?.let { task ->
            AlertDialog(
                onDismissRequest = { toDelete = null },
                containerColor = Palette.CardBg,
                titleContentColor = Palette.TextMain,
                textContentColor = Palette.TextSub,
                title = { Text(text = "Удалить задачу?", fontSize = 18.sp) },
                text = { Text(text = task.title, fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = { delete(task); toDelete = null }) {
                        Text(text = "Удалить", color = Palette.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { toDelete = null }) {
                        Text(text = "Отмена", color = Palette.TextSub)
                    }
                },
            )
        }
    }
}

@Composable
private fun TaskRow(task: VikunjaTask, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (task.done) Palette.VioletHi else Palette.CardBg)
                    .border(
                        width = 1.5.dp,
                        color = if (task.done) Palette.VioletHi else Palette.TextDim,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (task.done) {
                    Text(text = "✓", fontSize = 13.sp, color = Palette.BgTop)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    color = if (task.done) Palette.TextDim else Palette.TextMain,
                    textDecoration = if (task.done) TextDecoration.LineThrough
                    else TextDecoration.None,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                dueLine(task)?.let { (text, overdue) ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        color = when {
                            task.done -> Palette.TextDim
                            overdue -> Palette.Amber
                            else -> Palette.VioletHi
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "×",
                fontSize = 22.sp,
                color = Palette.TextDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Строка срока: текст и признак «просрочено». */
private fun dueLine(task: VikunjaTask): Pair<String, Boolean>? {
    val iso = task.dueDate ?: return null
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val text = instant.atZone(ZoneId.systemDefault()).format(DUE_FORMAT)
    return text to instant.isBefore(Instant.now())
}

private enum class TaskFilter { ALL, OVERDUE, TODAY }

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Palette.VioletHi.copy(alpha = 0.16f) else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) Palette.VioletHi else Palette.CardBorder,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (selected) Palette.VioletHi else Palette.TextSub,
        )
    }
}
