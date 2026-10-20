package com.catinder

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint as NativePaint
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catinder.logging.MiniLogger
import com.catinder.net.CatinderHttp
import com.catinder.net.Route
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private val BrandPink = Color(0xFFFF5864)

// Шрифт pIqaD qolqoS (Daniel Dadap), SIL OFL 1.1 — лицензия: app/piqad-font.LICENSE.
private val piqadFamily = FontFamily(Font(R.font.piqad))

data class Card(
    val num: Int,
    val id: String,
    val name: String,
    val image: String,
    val age: String?,
    val likes: List<String>,
    val dislikes: List<String>,
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        insets.isAppearanceLightStatusBars = false
        insets.isAppearanceLightNavigationBars = false

        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("login", Context.MODE_PRIVATE) }
    var sessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var loggedIn by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf(prefs.getString("who", "") ?: "") }
    var wants by remember { mutableStateOf(prefs.getString("wants", null)) }
    // Автовход — только по сохранённой сессии, проверка один раз при старте:
    // live-выбор на экране логина сюда попасть не должен, только «Продолжить».
    var sessionRestored by remember {
        mutableStateOf(!me.isNullOrEmpty() && prefs.contains("wants"))
    }
    if (loggedIn || sessionRestored) {
        SwipeScreen(
            sessionId = sessionId,
            onLogout = {
                me = ""
                wants = null
                prefs.edit().putString("who", "").remove("wants").apply()
                sessionRestored = false
                loggedIn = false
                // Серверные настройки больше не актуальны — сбрасываем.
                com.catinder.net.Flags.reset()
            },
        )
    } else {
        LoginScreen(
            me = me,
            wants = wants,
            onSelectMe = { value ->
                // Повторный тап по выбранному — отжатие: сбрасывает и wants.
                if (me == value) {
                    me = ""
                    wants = null
                } else {
                    me = value
                    wants = null
                }
            },
            onSelectCat = {
                wants = if (wants == "cat") null else "cat"
            },
            onContinue = {
                // В настройки пишем только по «Продолжить»: до этого выбор —
                // чисто UI-состояние, преждевременное сохранение не нужно.
                prefs.edit().putString("who", me).putString("wants", wants).apply()
                // Логин уходит асинхронно: настройки (flags) подтянутся в синглтон.
                com.catinder.net.Flags.login(me, wants, sessionId)
                loggedIn = true
            },
        )
    }
}

/** Экран входа: кто я и кого ищу. */
@Composable
private fun LoginScreen(
    me: String,
    wants: String?,
    onSelectMe: (String) -> Unit,
    onSelectCat: () -> Unit,
    onContinue: () -> Unit,
) {
    val logoColor = colorResource(R.color.ic_launcher_foreground_fill)

    Column(
        modifier = Modifier.fillMaxSize().background(BrandPink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                colorFilter = ColorFilter.tint(logoColor),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Cat",
                style =
                    TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = logoColor,
                    ),
            )
            Text(
                text = "inder",
                style =
                    TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, color = logoColor),
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Все строки занимают финальные позиции сразу: сначала невидимы
            // (alpha 0), появляются по цепочке без сдвига остального контента.
            // Малые отступы внутри секций, большие — между секциями.
            // Группы появляются/исчезают с лёгким fade: G2 — подпись + «Ищу» + кот,
            // G3 — «котейку.» + «Продолжить». Позиции не меняются.
            val whoSection by animateFloatAsState(if (me.isNotEmpty()) 1f else 0f, tween(200))
            val catSection by animateFloatAsState(if (wants != null) 1f else 0f, tween(200))
            // Пока fade out догорает, подпись держит последнее непустое значение:
            // иначе else-ветка (пикад) при me == "" ушла бы дефолтным шрифтом → «тофу».
            var shownWho by remember { mutableStateOf(me) }
            LaunchedEffect(me) {
                if (me.isNotEmpty()) shownWho = me
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Я", style = TextStyle(fontSize = 20.sp, color = Color.White))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeadButton(R.drawable.ic_head_person, selected = me == "human") {
                        onSelectMe("human")
                    }
                    HeadButton(R.drawable.ic_head_robot, selected = me == "robot") {
                        onSelectMe("robot")
                    }
                    HeadButton(R.drawable.ic_head_alien, selected = me == "alien") {
                        onSelectMe("alien")
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Фиксированная высота: брайль/пикад из фолбэка выше дефолтного
                // шрифта — без неё строка меняла бы высоту при переключении.
                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            when (shownWho) {
                                "human" -> "человек."
                                "robot" -> "⠗⠕⠃⠕⠞." // «robot» шрифтом Брайля
                                else -> "." // «alien» пикадом (CSUR U+F8D0..)
                            },
                        style =
                            TextStyle(
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = whoSection),
                                fontFamily =
                                    if (shownWho == "alien") piqadFamily else FontFamily.Default,
                            ),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Ищу",
                    style =
                        TextStyle(fontSize = 20.sp, color = Color.White.copy(alpha = whoSection)),
                )
                Spacer(Modifier.height(8.dp))
                HeadButton(
                    R.drawable.ic_head_cat,
                    selected = wants != null,
                    visible = me.isNotEmpty(),
                    onClick = onSelectCat,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "котейку.",
                    style =
                        TextStyle(fontSize = 16.sp, color = Color.White.copy(alpha = catSection)),
                )
                Spacer(Modifier.height(28.dp))
                Box(modifier = Modifier.alpha(catSection)) {
                    TextButton(onClick = onContinue, enabled = me.isNotEmpty() && wants != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Продолжить",
                                style = TextStyle(fontSize = 18.sp, color = Color.White),
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Голова-выбор: белая на розовом, выбранная — розовая на белом скруглённом фоне. */
@Composable
private fun HeadButton(icon: Int, selected: Boolean, visible: Boolean = true, onClick: () -> Unit) {
    val appear by animateFloatAsState(if (visible) 1f else 0f, tween(200))
    Box(
        modifier =
            Modifier.size(76.dp)
                .alpha(appear)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) Color.White else Color.Transparent)
                .clickable(enabled = visible, onClick = onClick)
                .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(if (selected) BrandPink else Color.White),
        )
    }
}

private suspend fun fetchCard(sessionId: String, num: Int): Card =
    withContext(Dispatchers.IO) {
        try {
            MiniLogger.d("content load start: num=$num")
            val started = SystemClock.elapsedRealtimeNanos()
            val request =
                Request.Builder()
                    .url("${BuildConfig.BASE_URL}/show?sessionId=$sessionId&num=$num")
                    .tag(Route::class.java, Route("/show"))
                    .build()
            val body = CatinderHttp.client.newCall(request).execute().use { it.body!!.string() }
            val json = JSONObject(body)
            val card =
                Card(
                    num = json.getInt("num"),
                    id = json.getString("id"),
                    name = json.getString("name"),
                    image = json.getString("image"),
                    age = json.optString("age")?.ifEmpty { null },
                    likes =
                        json.getJSONArray("likes").let { a ->
                            List(a.length()) { a.getString(it) }
                        },
                    dislikes =
                        json.getJSONArray("dislikes").let { a ->
                            List(a.length()) { a.getString(it) }
                        },
                )
            val tookMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
            MiniLogger.d("content load end: num=$num, ${tookMs}ms")
            card
        } finally {}
    }

private fun heartIcon(): ImageVector =
    ImageVector.Builder("heart", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData =
                addPathNodes(
                    "M12 20.3 C7 16.8 3 13.6 3 9.6 C3 7 5 5 7.5 5 C9.3 5 10.9 5.9 12 7.3 " +
                        "C13.1 5.9 14.7 5 16.5 5 C19 5 21 7 21 9.6 C21 13.6 17 16.8 12 20.3 Z"
                ),
            fill = SolidColor(Color.Black),
        )
        .build()

/** Текст тёмный с белой обводкой. */
@Composable
private fun StrokeText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(
            text = text,
            style = style.copy(color = Color(0x80232323), drawStyle = Stroke(width = 2.5f)),
        )
        Text(text = text, style = style.copy(color = Color(0xF2FFFFFF)))
    }
}

@Composable
private fun SwipeScreen(sessionId: String, onLogout: () -> Unit) {
    var num by remember { mutableStateOf(1) }
    var card by remember { mutableStateOf<Card?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val offsetX = remember { Animatable(0f) }
    val appearScale = remember { Animatable(1f) }
    val appearAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var cardWidth by remember { mutableStateOf(1) }
    var cardHeight by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    fun flyAway(direction: Int) {
        if (busy) return
        busy = true
        // Клик по кнопке — голос по карточке: уходит асинхронно, не блокирует UI.
        card?.let {
            com.catinder.net.Flags.vote(if (direction > 0) "like" else "dislike", it.id, sessionId)
        }
        scope.launch {
            offsetX.animateTo(direction * cardWidth * 1.8f, tween(180))
            num++
            busy = false
        }
    }

    LaunchedEffect(num) {
        offsetX.snapTo(0f)
        appearScale.snapTo(0.92f)
        appearAlpha.snapTo(0.3f)
        launch { appearAlpha.animateTo(1f, tween(250)) }
        launch { appearScale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 350f)) }
    }

    var imageReady by remember { mutableStateOf(false) }

    LaunchedEffect(num) {
        error = null
        card = null
        imageReady = false
        try {
            card = fetchCard(sessionId, num)
        } catch (e: Exception) {
            error = "server: ${e.javaClass.simpleName}"
        }
    }

    val logoColor = colorResource(R.color.ic_launcher_foreground_fill)
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(BrandPink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 5.dp)) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    colorFilter = ColorFilter.tint(logoColor),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Cat",
                    style =
                        TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = logoColor,
                        ),
                )
                Text(
                    text = "inder",
                    style =
                        TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            color = logoColor,
                        ),
                )
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "меню",
                        tint = logoColor,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Выйти") },
                        onClick = {
                            menuOpen = false
                            onLogout()
                        },
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .drawBehind {
                        // Тень догоняет карточку по проявлению: пока карточка
                        // полупрозрачная, цвет тени — фоновый (на фоне не
                        // виден), к полной непрозрачности тень доходит до
                        // рабочего серого. Альфа краски не нулевая — иначе на
                        // этом графостеке setShadowLayer рисует тень в полный
                        // рост.
                        val t = ((appearAlpha.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        if (t > 0.02f) {
                            drawIntoCanvas { canvas ->
                                val nc = canvas.nativeCanvas
                                val scale = appearScale.value
                                nc.save()
                                nc.translate(size.width / 2f + offsetX.value, size.height / 2f)
                                nc.rotate(offsetX.value / 24f)
                                nc.scale(scale, scale)
                                val radiusPx = 16.dp.toPx()
                                val shadowColor =
                                    ColorUtils.blendARGB(
                                        BrandPink.toArgb(),
                                        android.graphics.Color.BLACK,
                                        t,
                                    )
                                val paint =
                                    NativePaint().apply {
                                        color = shadowColor
                                        alpha = (90 * t).toInt()
                                        isAntiAlias = true
                                        setShadowLayer(18f, 0f, 6f, shadowColor)
                                    }
                                nc.drawRoundRect(
                                    -cardWidth / 2f,
                                    -cardHeight / 2f,
                                    cardWidth / 2f,
                                    cardHeight / 2f,
                                    radiusPx,
                                    radiusPx,
                                    paint,
                                )
                                nc.restore()
                            }
                        }
                    },
        ) {
            // Карточка центрируется в области: центр родителя = центр тени.
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        // Потолок соотношения 16:9 (высота к ширине): на вытянутых
                        // экранах карточка не вытягивается дальше, чем ширина × 16/9.
                        .layout { measurable, constraints ->
                            val cap = (constraints.maxWidth * 16f / 9f).roundToInt()
                            val placeable =
                                measurable.measure(
                                    constraints.copy(maxHeight = minOf(constraints.maxHeight, cap))
                                )
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .padding(8.dp)
                        .onSizeChanged {
                            cardWidth = it.width
                            cardHeight = it.height
                        }
                        .graphicsLayer {
                            translationX = offsetX.value
                            rotationZ = offsetX.value / 24f
                            scaleX = appearScale.value
                            scaleY = appearScale.value
                            // альфа на слое, а не краской: полупрозрачные пиксели внутри
                            // слоя на этом графостеке композитятся против чёрного
                            alpha = appearAlpha.value
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .pointerInput(num) {
                            val tracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = { tracker.resetTracking() },
                                onDragEnd = {
                                    val velocity = tracker.calculateVelocity().x
                                    val fling = abs(velocity) > 1200f
                                    val farEnough = abs(offsetX.value) > cardWidth * 0.35f
                                    scope.launch {
                                        if (farEnough || fling) {
                                            // Свайп — тоже голос по карточке: вправо лайк,
                                            // влево дизлайк. Асинхронно, UI не ждёт.
                                            card?.let {
                                                com.catinder.net.Flags.vote(
                                                    if (offsetX.value >= 0) "like" else "dislike",
                                                    it.id,
                                                    sessionId,
                                                )
                                            }
                                            busy = true
                                            offsetX.animateTo(
                                                if (offsetX.value >= 0) cardWidth * 1.8f
                                                else -cardWidth * 1.8f,
                                                tween(180),
                                            )
                                            num++
                                            busy = false
                                        } else {
                                            offsetX.animateTo(0f, spring(dampingRatio = 0.6f))
                                        }
                                    }
                                },
                                onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                                onDrag = { change, drag ->
                                    change.consume()
                                    scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                },
                            )
                        }
            ) {
                val current = card
                when {
                    error != null ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "GRROR",
                                style = TextStyle(fontSize = 18.sp, color = Color.Black),
                            )
                        }
                    current == null -> Box(Modifier.fillMaxSize())
                    else ->
                        Box(Modifier.fillMaxSize()) {
                            val imageAlpha by
                                animateFloatAsState(
                                    targetValue = if (imageReady) 1f else 0f,
                                    animationSpec = tween(300),
                                )
                            val imageStarted =
                                remember(current.num) { SystemClock.elapsedRealtimeNanos() }
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(BuildConfig.BASE_URL + current.image)
                                        .tag(Route::class.java, Route("/img/{id}"))
                                        .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onLoading = {
                                    MiniLogger.d("image load start: ${current.image}")
                                },
                                onSuccess = {
                                    val tookMs =
                                        (SystemClock.elapsedRealtimeNanos() - imageStarted) /
                                            1_000_000
                                    MiniLogger.d("image load end: ${current.image}, ${tookMs}ms")
                                    imageReady = true
                                },
                                onError = { res ->
                                    MiniLogger.e(
                                        "image load error: ${current.image}",
                                        res.result.throwable,
                                    )
                                    error = "image: ${res.result.throwable.message}"
                                },
                                modifier = Modifier.fillMaxSize().alpha(imageAlpha),
                            )
                            // подложка под текст и текст — обычное содержимое слоя:
                            // проявляются вместе с альфой карточки
                            Box(
                                modifier =
                                    Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colorStops =
                                                    arrayOf(
                                                        0.0f to Color(0x00000000),
                                                        0.45f to Color(0xA3000000),
                                                        1.0f to Color(0xB8000000),
                                                    )
                                            )
                                        )
                            )
                            Column(
                                modifier =
                                    Modifier.align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(16.dp)
                            ) {
                                StrokeText(
                                    text =
                                        if (current.age != null) {
                                            "${current.name}, возраст ${current.age.toFloat()}"
                                        } else {
                                            current.name
                                        },
                                    style = TextStyle(fontSize = 20.sp),
                                )
                                Spacer(Modifier.height(8.dp))
                                StrokeText(
                                    text = "Любит: ${current.likes.joinToString(", ")}",
                                    style = TextStyle(fontSize = 14.sp),
                                )
                                Spacer(Modifier.height(10.dp))
                                StrokeText(
                                    text = "Не любит: ${current.dislikes.joinToString(", ")}",
                                    style = TextStyle(fontSize = 14.sp),
                                )
                            }
                        }
                }
            }
            if (error == null && (card == null || !imageReady)) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = BrandPink,
                )
            }
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 48.dp, end = 48.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val heart = remember { heartIcon() }
            val ripple = rememberRipple(bounded = false, radius = 110.dp)
            Box(
                modifier =
                    Modifier.size(48.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple,
                    ) {
                        flyAway(-1)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.poop_icon),
                    contentDescription = "дизлайк",
                    modifier = Modifier.size(29.dp),
                    colorFilter = ColorFilter.tint(logoColor),
                )
            }
            Box(
                modifier =
                    Modifier.size(48.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple,
                    ) {
                        flyAway(1)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    heart,
                    contentDescription = "лайк",
                    modifier = Modifier.size(32.dp),
                    tint = logoColor,
                )
            }
        }
    }
}
