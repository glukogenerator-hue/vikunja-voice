package ru.home.vikunjavoice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Экран для текста, которым поделились из другого приложения:
 * показываем разобранный заголовок и срок, проект можно сменить.
 */
@Composable
fun ShareScreen(prefs: Prefs, sharedText: String, onDone: () -> Unit) {
    val parsed = remember(sharedText) { RussianDateParser.parse(sharedText) }
    var title by remember(sharedText) { mutableStateOf(parsed.title) }
    var projectLabel by remember { mutableStateOf(prefs.projectTitle) }
    var showPicker by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val dueLabel = parsed.due?.format(
        DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
    )
    val dueIso = parsed.due
        ?.withZoneSameInstant(ZoneOffset.UTC)
        ?.format(DateTimeFormatter.ISO_INSTANT)

    fun send() {
        if (title.isBlank()) {
            error = "Название пустое"
            return
        }
        sending = true
        error = null
        scope.launch {
            try {
                VikunjaApi(prefs.serverUrl, prefs.token)
                    .createTask(prefs.projectId, title.trim(), dueIso)
                status = "Задача создана ✓"
                delay(700)
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Не удалось создать задачу"
            } finally {
                sending = false
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Palette.VioletHi, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "VIKUNJA VOICE",
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                    color = Palette.TextDim,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Задача из другого приложения",
                fontSize = 13.sp,
                color = Palette.TextSub,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.VioletHi,
                    unfocusedBorderColor = Palette.CardBorder,
                    cursorColor = Palette.VioletHi,
                    focusedTextColor = Palette.TextMain,
                    unfocusedTextColor = Palette.TextMain,
                    focusedContainerColor = Palette.CardBg,
                    unfocusedContainerColor = Palette.CardBg,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (dueLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = dueLabel, fontSize = 16.sp, color = Palette.VioletHi)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.CardBg.copy(alpha = 0.55f))
                    .border(1.dp, Palette.CardBorder, RoundedCornerShape(20.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Проект: ", fontSize = 13.sp, color = Palette.TextSub)
                Text(
                    text = projectLabel.ifBlank { "выбрать" },
                    fontSize = 13.sp,
                    color = Palette.VioletHi,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "▾", fontSize = 14.sp, color = Palette.TextSub)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { send() },
                enabled = !sending && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.VioletHi),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (sending) "Отправляю…" else "Создать задачу",
                    color = Palette.BgTop,
                )
            }
            TextButton(onClick = onDone) {
                Text(text = "Отмена", color = Palette.TextSub)
            }
            status?.let {
                Text(text = it, fontSize = 13.sp, color = Palette.VioletHi)
            }
            error?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = Palette.Error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        if (showPicker) {
            ProjectPickerDialog(
                prefs = prefs,
                onDismiss = { showPicker = false },
                onPick = { project ->
                    prefs.projectId = project.id
                    prefs.projectTitle = project.title
                    projectLabel = project.title
                    showPicker = false
                },
                onOpenSettings = {
                    showPicker = false
                    onDone()
                },
            )
        }
    }
}
