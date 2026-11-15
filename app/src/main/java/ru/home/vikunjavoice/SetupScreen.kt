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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Шаг 1: адрес сервера + API-токен → список проектов из Vikunja → выбор проекта.
 * Только после этого открывается экран записи.
 */
@Composable
fun SetupScreen(prefs: Prefs, onDone: () -> Unit) {
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var projects by remember { mutableStateOf<List<VikunjaProject>>(emptyList()) }
    var selected by remember { mutableStateOf<VikunjaProject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadProjects() {
        if (serverUrl.isBlank() || token.isBlank()) {
            error = "Заполни адрес сервера и токен"
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val list = VikunjaApi(serverUrl, token).projects()
                projects = list
                selected = list.firstOrNull { it.id == prefs.projectId } ?: list.firstOrNull()
                if (list.isEmpty()) error = "На сервере нет проектов"
            } catch (e: Exception) {
                error = e.message ?: "Не удалось получить проекты"
            } finally {
                loading = false
            }
        }
    }

    fun save() {
        val project = selected ?: return
        prefs.serverUrl = serverUrl.trim()
        prefs.token = token.trim()
        prefs.projectId = project.id
        prefs.projectTitle = project.title
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom)))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(56.dp))
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
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "Подключение",
            fontSize = 24.sp,
            color = Palette.TextMain,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Сначала выбери проект — в него будут падать задачи",
            fontSize = 13.sp,
            color = Palette.TextSub,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Адрес сервера") },
            placeholder = { Text("http://192.168.1.10:3456") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API-токен") },
            placeholder = { Text("tk_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { loadProjects() },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.VioletLo),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Palette.TextMain,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = if (loading) "Получаю проекты…" else "Получить список проектов",
                color = Palette.TextMain,
            )
        }

        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = it, fontSize = 13.sp, color = Palette.Error, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (projects.isNotEmpty()) {
            Text(
                text = "Проект",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = Palette.TextDim,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(projects) { project ->
                    ProjectRow(
                        project = project,
                        selected = project.id == selected?.id,
                        onClick = { selected = project },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { save() },
                enabled = selected != null,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.VioletHi),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Готово — перейти к записи",
                    color = Palette.BgTop,
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ProjectRow(project: VikunjaProject, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.CardBg else Palette.CardBg.copy(alpha = 0.4f))
            .border(
                1.dp,
                if (selected) Palette.VioletHi else Palette.CardBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (selected) Palette.VioletHi else Palette.TextDim,
                    CircleShape,
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = project.title,
            fontSize = 16.sp,
            color = if (selected) Palette.TextMain else Palette.TextSub,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.VioletHi,
    unfocusedBorderColor = Palette.CardBorder,
    focusedLabelColor = Palette.VioletHi,
    unfocusedLabelColor = Palette.TextSub,
    cursorColor = Palette.VioletHi,
    focusedTextColor = Palette.TextMain,
    unfocusedTextColor = Palette.TextMain,
    focusedPlaceholderColor = Palette.TextDim,
    unfocusedPlaceholderColor = Palette.TextDim,
    focusedContainerColor = Palette.CardBg,
    unfocusedContainerColor = Palette.CardBg,
)
