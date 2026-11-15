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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Настройки: сервер, API-токен, показывать ли выполненные задачи. */
@Composable
fun SettingsScreen(prefs: Prefs, onClose: () -> Unit) {
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var showCompleted by remember { mutableStateOf(prefs.showCompleted) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun save() {
        if (serverUrl.isBlank() || token.isBlank()) {
            message = "Заполни адрес сервера и токен"
            isError = true
            return
        }
        saving = true
        isError = false
        scope.launch {
            prefs.serverUrl = serverUrl.trim()
            prefs.token = token.trim()
            prefs.showCompleted = showCompleted
            try {
                val list = VikunjaApi(prefs.serverUrl, prefs.token).projects()
                ProjectsCache(context.applicationContext).save(prefs.serverUrl, list)
                message = "Сохранено"
                delay(500)
                onClose()
            } catch (e: Exception) {
                message = "Сохранено, но проекты не загрузились: ${e.message}"
                isError = true
            } finally {
                saving = false
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
            Spacer(modifier = Modifier.height(28.dp))
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
                    text = "НАСТРОЙКИ",
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                    color = Palette.TextDim,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Закрыть",
                    fontSize = 13.sp,
                    color = Palette.VioletHi,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Адрес сервера") },
                placeholder = { Text("https://task.example.ru") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Показывать выполненные",
                    fontSize = 13.sp,
                    color = Palette.TextSub,
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = showCompleted,
                    onCheckedChange = { showCompleted = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.BgTop,
                        checkedTrackColor = Palette.VioletHi,
                        uncheckedThumbColor = Palette.TextSub,
                        uncheckedTrackColor = Palette.CardBorder,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { save() },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.VioletHi),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (saving) "Проверяю…" else "Сохранить",
                    color = Palette.BgTop,
                )
            }
            message?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = if (isError) Palette.Error else Palette.VioletHi,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
