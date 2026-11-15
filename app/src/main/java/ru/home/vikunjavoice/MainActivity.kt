package ru.home.vikunjavoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIC_SIZE_DP = 180

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Palette.VioletHi,
                    background = Palette.BgTop,
                    surface = Palette.CardBg,
                    error = Palette.Error,
                )
            ) {
                val prefs = remember { Prefs(appContext) }
                var configured by remember { mutableStateOf(prefs.configured) }
                if (configured) {
                    VoiceScreen(
                        prefs = prefs,
                        onChangeProject = { configured = false },
                    )
                } else {
                    SetupScreen(prefs = prefs, onDone = { configured = true })
                }
            }
        }
    }
}

/**
 * Шаг 2: запись голоса → текст → задача уходит в выбранный проект Vikunja.
 */
@Composable
fun VoiceScreen(prefs: Prefs, onChangeProject: () -> Unit) {
    var recognizedText by remember { mutableStateOf("") }
    var dueText by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Нажми и скажи задачу") }
    var statusColor by remember { mutableStateOf(Palette.TextSub) }
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val wave = remember {
        mutableStateListOf<Float>().apply { repeat(26) { add(0f) } }
    }
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()

    fun submit(text: String) {
        // Разбор полностью локальный: дата/время из фразы, чистый заголовок.
        val parsed = RussianDateParser.parse(text)
        recognizedText = parsed.title
        val due = parsed.due
        val dueLabel = due?.format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru")))
        dueText = dueLabel
        val dueIso = due
            ?.withZoneSameInstant(ZoneOffset.UTC)
            ?.format(DateTimeFormatter.ISO_INSTANT)
        sending = true
        status = if (dueLabel != null) "Отправляю в Vikunja · $dueLabel…" else "Отправляю в Vikunja…"
        statusColor = Palette.TextSub
        scope.launch {
            try {
                VikunjaApi(prefs.serverUrl, prefs.token)
                    .createTask(prefs.projectId, parsed.title, dueIso)
                status = "Задача создана ✓"
                statusColor = Palette.VioletHi
            } catch (e: Exception) {
                status = "Не отправлено: ${e.message}"
                statusColor = Palette.Error
            } finally {
                sending = false
            }
        }
    }

    val onStatus: (String) -> Unit = { status = it; statusColor = Palette.TextSub }
    val onPartial: (String) -> Unit = { recognizedText = it }
    val onFinal: (String) -> Unit = {
        recognizedText = it
        submit(it)
    }
    val onRms: (Float) -> Unit = { rms ->
        for (i in 0 until wave.size - 1) wave[i] = wave[i + 1]
        wave[wave.size - 1] = rms
    }
    val onDone: () -> Unit = {
        listening = false
        for (i in wave.indices) wave[i] = 0f
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            listening = true
            startListening(activity, onStatus, onPartial, onFinal, onRms, onDone)
        } else {
            status = "Нужно разрешение на микрофон"
            statusColor = Palette.Error
        }
    }

    fun go() {
        recognizedText = ""
        dueText = null
        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            listening = true
            startListening(activity, onStatus, onPartial, onFinal, onRms, onDone)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.CardBg)
                    .border(1.dp, Palette.CardBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onChangeProject)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Проект: ", fontSize = 13.sp, color = Palette.TextSub)
                Text(
                    text = prefs.projectTitle.ifBlank { "выбрать" },
                    fontSize = 13.sp,
                    color = Palette.VioletHi,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TaskCard(recognizedText, dueText, status, statusColor)
            Spacer(modifier = Modifier.weight(1.1f))
            MicArea(
                listening = listening,
                enabled = !sending,
                wave = wave,
                onClick = { if (!sending) go() },
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(text = "M2 · голос → задача в Vikunja", fontSize = 12.sp, color = Palette.TextDim)
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TaskCard(text: String, dueText: String?, status: String, statusColor: Color) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text.ifBlank { "Здесь появится твоя задача" },
                fontSize = 22.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                color = if (text.isBlank()) Palette.TextSub else Palette.TextMain,
            )
            if (dueText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dueText,
                    fontSize = 17.sp,
                    color = Palette.VioletHi,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = status,
                fontSize = 13.sp,
                color = statusColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MicArea(
    listening: Boolean,
    enabled: Boolean,
    wave: SnapshotStateList<Float>,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "rings")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ring",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (listening) {
                Ring(progress = ring)
                Ring(progress = (ring + 0.5f) % 1f)
            }
            Box(
                modifier = Modifier
                    .size(MIC_SIZE_DP.dp)
                    .shadow(24.dp, CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(Palette.VioletHi, Palette.VioletLo)),
                        CircleShape
                    )
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                MicIcon(tint = Palette.BgTop, modifier = Modifier.size(72.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.height(90.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                WaveRow(wave = wave)
            } else {
                Text(
                    text = "Нажми и скажи задачу",
                    fontSize = 14.sp,
                    color = Palette.TextSub,
                )
            }
        }
    }
}

@Composable
private fun Ring(progress: Float) {
    val diameter = MIC_SIZE_DP.dp + 130.dp * progress
    Box(
        modifier = Modifier
            .size(diameter)
            .border(2.dp, Palette.VioletHi.copy(alpha = (1f - progress) * 0.35f), CircleShape)
    )
}

@Composable
private fun WaveRow(wave: SnapshotStateList<Float>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        wave.forEach { rms ->
            val h = 10.dp + (rms.coerceIn(0f, 30f) * 2.5f).dp
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Palette.VioletHi)
            )
        }
    }
}

/** Иконка микрофона: капсула + дуга-держатель + ножка с основанием. */
@Composable
private fun MicIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val cx = center.x
        val cy = center.y - s * 0.06f
        val mw = s * 0.12f
        val mh = s * 0.20f
        drawRoundRect(
            color = tint,
            topLeft = Offset(cx - mw, cy - mh),
            size = Size(mw * 2f, mh * 2f),
            cornerRadius = CornerRadius(mw, mw),
        )
        val arcR = s * 0.28f
        drawArc(
            color = tint,
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            style = Stroke(width = s * 0.055f),
            topLeft = Offset(cx - arcR, cy - arcR),
            size = Size(arcR * 2f, arcR * 2f),
        )
        val stemBottom = cy + arcR + s * 0.10f
        drawLine(
            color = tint,
            start = Offset(cx, cy + mh * 0.5f),
            end = Offset(cx, stemBottom),
            strokeWidth = s * 0.055f,
        )
        drawLine(
            color = tint,
            start = Offset(cx - s * 0.10f, stemBottom),
            end = Offset(cx + s * 0.10f, stemBottom),
            strokeWidth = s * 0.045f,
        )
    }
}

/** Запускает системный SpeechRecognizer с русским языком. */
fun startListening(
    activity: Activity,
    onStatus: (String) -> Unit,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onRms: (Float) -> Unit,
    onDone: () -> Unit,
) {
    if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
        onStatus("Распознавание недоступно: на телефоне нет голосового ввода (приложение Google)")
        return
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onStatus("Слушаю…")
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = onRms(rmsdB)
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = onStatus("Обрабатываю…")
        override fun onError(error: Int) {
            onStatus(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Речи не услышал, попробуй ещё раз"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Тишина — попробуй ещё раз"
                    SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети (распознавание идёт через Google)"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят, подожди секунду"
                    else -> "Ошибка распознавания (код $error)"
                }
            )
            onDone()
            recognizer.destroy()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                onStatus("Речь не распознана, попробуй ещё раз")
            } else {
                onFinal(text)
            }
            onDone()
            recognizer.destroy()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    recognizer.startListening(intent)
}
