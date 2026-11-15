@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.home.vikunjavoice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val MIC_SIZE_DP = 180

/** Постоянная область под кольца: layout не меняется, кнопка не прыгает. */
private val RING_AREA_DP = 250.dp

class MainActivity : ComponentActivity() {

    private companion object {
        const val EXTRA_START_LISTENING = "start_listening"
    }

    private var sharedText by mutableStateOf<String?>(null)
    private var autoListen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        Notifications.ensureChannel(appContext)
        scheduleProjectsSync(appContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        sharedText = extractShared(intent)
        autoListen = intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true
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
                if (!configured) {
                    SetupScreen(prefs = prefs, onDone = { configured = true })
                } else {
                    val pendingShare = sharedText
                    if (pendingShare != null) {
                        ShareScreen(
                            prefs = prefs,
                            sharedText = pendingShare,
                            onDone = { sharedText = null },
                        )
                    } else {
                        val pagerState = rememberPagerState(pageCount = { 2 })
                        val scope = rememberCoroutineScope()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Palette.BgTop)
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f),
                            ) { page ->
                                if (page == 0) {
                                    VoiceScreen(
                                        prefs = prefs,
                                        onChangeProject = { configured = false },
                                        autoStart = autoListen,
                                        onAutoStartConsumed = { autoListen = false },
                                    )
                                } else {
                                    TasksScreen(
                                        prefs = prefs,
                                        onOpenSettings = { configured = false },
                                    )
                                }
                            }
                            BottomTabs(
                                selected = pagerState.currentPage,
                                onSelect = { index ->
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractShared(intent)?.let { sharedText = it }
        if (intent.getBooleanExtra(EXTRA_START_LISTENING, false)) autoListen = true
    }

    /** Текст, которым поделились из другого приложения. */
    private fun extractShared(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    }
}

/** Кэш проектов обновляем в фоне: периодически и сразу, если он устарел. */
private fun scheduleProjectsSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val periodic = PeriodicWorkRequestBuilder<ProjectsSyncWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "projects-sync",
        ExistingPeriodicWorkPolicy.KEEP,
        periodic,
    )

    val reminders = PeriodicWorkRequestBuilder<RemindersWorker>(30, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "due-reminders",
        ExistingPeriodicWorkPolicy.KEEP,
        reminders,
    )

    if (ProjectsCache(context).isStale(3 * 60 * 60 * 1000L)) {
        val once = OneTimeWorkRequestBuilder<ProjectsSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "projects-refresh",
            ExistingWorkPolicy.KEEP,
            once,
        )
    }
}

/** Нижние вкладки: текст + плавно проявляющийся индикатор по ширине текста. */
@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.BgBottom)
            .padding(horizontal = 24.dp),
    ) {
        TabItem(
            text = "Голос",
            selected = selected == 0,
            modifier = Modifier.weight(1f),
        ) { onSelect(0) }
        TabItem(
            text = "Задачи",
            selected = selected == 1,
            modifier = Modifier.weight(1f),
        ) { onSelect(1) }
    }
}

@Composable
private fun TabItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) Palette.TextMain else Palette.TextSub,
        label = "tabColor",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
        label = "tabIndicator",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(top = 5.dp, bottom = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = text, fontSize = 14.sp, color = textColor)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.VioletHi.copy(alpha = indicatorAlpha))
            )
        }
    }
}

@Composable
fun VoiceScreen(
    prefs: Prefs,
    onChangeProject: () -> Unit,
    autoStart: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
) {
    var recognizedText by remember { mutableStateOf("") }
    var dueText by remember { mutableStateOf<String?>(null) }
    var projectLabel by remember { mutableStateOf(prefs.projectTitle) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Готов") }
    var statusColor by remember { mutableStateOf(Palette.TextSub) }
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var lastCreatedId by remember { mutableStateOf<Long?>(null) }
    var undoVisible by remember { mutableStateOf(false) }
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
                val id = VikunjaApi(prefs.serverUrl, prefs.token)
                    .createTask(prefs.projectId, parsed.title, dueIso)
                lastCreatedId = id
                undoVisible = true
                status = "Задача создана ✓"
                statusColor = Palette.VioletHi
                // Через несколько секунд возможность отмены пропадает.
                scope.launch {
                    delay(6000)
                    if (lastCreatedId == id) undoVisible = false
                }
            } catch (e: Exception) {
                status = "Не отправлено: ${e.message}"
                statusColor = Palette.Error
            } finally {
                sending = false
            }
        }
    }

    /** Удаляет только что созданную задачу (если распозналось не то). */
    fun undoLastCreation() {
        val id = lastCreatedId ?: return
        lastCreatedId = null
        undoVisible = false
        scope.launch {
            try {
                VikunjaApi(prefs.serverUrl, prefs.token).deleteTask(id)
                status = "Создание отменено"
                statusColor = Palette.TextSub
            } catch (e: Exception) {
                status = "Не удалось отменить: ${e.message}"
                statusColor = Palette.Error
            }
        }
    }

    val onStatus: (String) -> Unit = { status = it; statusColor = Palette.TextSub }
    val onPartial: (String) -> Unit = { recognizedText = it }
    val onFinal: (String) -> Unit = {
        recognizedText = it
        submit(it)
    }
    var smoothAmp by remember { mutableStateOf(0f) }
    var lastShift by remember { mutableStateOf(0L) }
    val onRms: (Float) -> Unit = { rms ->
        // Сырой RMS дёргается — сглаживаем и двигаем волну не чаще ~14 раз/с.
        val target = ((rms + 2f) / 12f).coerceIn(0f, 1f)
        smoothAmp = smoothAmp * 0.7f + target * 0.3f
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastShift >= 70L) {
            lastShift = nowMs
            for (i in 0 until wave.size - 1) wave[i] = wave[i + 1]
            wave[wave.size - 1] = smoothAmp
        }
    }
    val onDone: () -> Unit = {
        listening = false
        recognizer = null
        for (i in wave.indices) wave[i] = 0f
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            status = "Разрешение выдано — держи кнопку"
        } else {
            status = "Нужно разрешение на микрофон"
            statusColor = Palette.Error
        }
    }

    /** Нажали и держим — начинаем слушать. */
    fun beginRecording() {
        if (listening || sending) return
        recognizedText = ""
        dueText = null
        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        listening = true
        status = "Слушаю…"
        statusColor = Palette.TextSub
        // Короткая вибрация подтверждает начало записи.
        (activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        recognizer = startListening(activity, onStatus, onPartial, onFinal, onRms, onDone)
    }

    /** Отпустили — останавливаем запись, движок отдаёт распознанное. */
    fun endRecording() {
        val current = recognizer
        if (current == null) {
            onDone()
            return
        }
        runCatching { current.stopListening() }
        // Страховка: если движок не ответит на stopListening, не залипнем в «Слушаю…».
        scope.launch {
            delay(2500)
            if (listening) {
                runCatching { current.destroy() }
                onDone()
                status = "Речь не распознана, попробуй ещё раз"
                statusColor = Palette.TextSub
            }
        }
    }

    // Запуск записи сразу, если пришли из ярлыка или виджета.
    LaunchedEffect(autoStart) {
        if (autoStart) {
            onAutoStartConsumed()
            delay(300)
            beginRecording()
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
            Spacer(modifier = Modifier.height(28.dp))
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
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.CardBg)
                    .border(1.dp, Palette.CardBorder, RoundedCornerShape(20.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                Text(text = "▾", fontSize = 12.sp, color = Palette.TextSub)
            }
            Spacer(modifier = Modifier.weight(1f))
            TaskCard(
                text = recognizedText,
                dueText = dueText,
                status = status,
                statusColor = statusColor,
                undoVisible = undoVisible,
                onUndo = { undoLastCreation() },
            )
            Spacer(modifier = Modifier.weight(1f))
            MicArea(
                listening = listening,
                enabled = !sending,
                wave = wave,
                onHoldStart = { beginRecording() },
                onHoldEnd = { endRecording() },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Голос → задача в Vikunja", fontSize = 12.sp, color = Palette.TextDim)
            Spacer(modifier = Modifier.height(12.dp))
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
                    status = "Проект: ${project.title}"
                    statusColor = Palette.TextSub
                },
                onOpenSettings = {
                    showPicker = false
                    onChangeProject()
                },
            )
        }
    }
}

@Composable
private fun TaskCard(
    text: String,
    dueText: String?,
    status: String,
    statusColor: Color,
    undoVisible: Boolean,
    onUndo: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Все строки имеют фиксированные места: частичные результаты распознавания
        // и появление «Отменить» не двигают кнопку с волной.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text.ifBlank { "Здесь появится твоя задача" },
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (text.isBlank()) Palette.TextSub else Palette.TextMain,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dueText != null) {
                        Text(
                            text = dueText,
                            fontSize = 17.sp,
                            color = Palette.VioletHi,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = status,
                        fontSize = 13.sp,
                        color = statusColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (undoVisible) {
                        Text(
                            text = "Отменить",
                            fontSize = 13.sp,
                            color = Palette.Amber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onUndo)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MicArea(
    listening: Boolean,
    enabled: Boolean,
    wave: SnapshotStateList<Float>,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "rings")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ring",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(RING_AREA_DP),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseR = MIC_SIZE_DP.dp.toPx() / 2f
                    val maxR = size.minDimension / 2f - 3.dp.toPx()
                    listOf(ring, (ring + 0.5f) % 1f).forEach { progress ->
                        drawCircle(
                            color = Palette.VioletHi.copy(alpha = (1f - progress) * 0.35f),
                            radius = baseR + (maxR - baseR) * progress,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(MIC_SIZE_DP.dp)
                    .shadow(24.dp, CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(Palette.VioletHi, Palette.VioletLo)),
                        CircleShape
                    )
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                onHoldStart()
                                tryAwaitRelease()
                                onHoldEnd()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                TalkingFaceIcon(tint = Palette.BgTop, modifier = Modifier.size(76.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                WaveRow(wave = wave)
            } else {
                Text(
                    text = "Держи и говори",
                    fontSize = 14.sp,
                    color = Palette.TextSub,
                )
            }
        }
    }
}

@Composable
private fun WaveRow(wave: SnapshotStateList<Float>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        wave.forEach { amp ->
            val h = 8.dp + (amp.coerceIn(0f, 1f) * 64f).dp
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

/** Говорящая мордочка: два глаза и приоткрытый рот. */
@Composable
private fun TalkingFaceIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val cx = center.x
        val cy = center.y
        // глаза
        drawCircle(
            color = tint,
            radius = s * 0.06f,
            center = Offset(cx - s * 0.19f, cy - s * 0.13f),
        )
        drawCircle(
            color = tint,
            radius = s * 0.06f,
            center = Offset(cx + s * 0.19f, cy - s * 0.13f),
        )
        // приоткрытый рот — капсула
        val mouthW = s * 0.24f
        val mouthH = s * 0.26f
        drawRoundRect(
            color = tint,
            topLeft = Offset(cx - mouthW / 2f, cy + s * 0.02f),
            size = Size(mouthW, mouthH),
            cornerRadius = CornerRadius(mouthW / 2f, mouthW / 2f),
        )
    }
}

/** Запускает системный SpeechRecognizer; возвращает его, чтобы можно было остановить по отпусканию. */
fun startListening(
    activity: Activity,
    onStatus: (String) -> Unit,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onRms: (Float) -> Unit,
    onDone: () -> Unit,
): SpeechRecognizer? {
    if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
        onStatus("Распознавание недоступно: на телефоне нет голосового ввода (приложение Google)")
        onDone()
        return null
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Пока палец на кнопке — не обрывать запись на паузах.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000L)
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
                    SpeechRecognizer.ERROR_CLIENT -> "Ничего не поймал — подержи кнопку подольше"
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
    return recognizer
}
