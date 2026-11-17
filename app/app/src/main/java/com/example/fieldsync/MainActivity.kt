package com.example.fieldsync

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.example.fieldsync.data.AppDatabase
import com.example.fieldsync.data.ChatRepository
import com.example.fieldsync.data.LIFETIME_MS
import com.example.fieldsync.data.MessageEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

// ---------- colours (same family as the class design) ----------
private val Bg = Color(0xFF101827)
private val BgTop = Color(0xFF16233A)
private val CardBg = Color(0xFF1D2939)
private val Teal = Color(0xFF16A085)
private val TealLight = Color(0xFF2ED3A8)
private val TealSoft = Color(0xFF72D6BC)
private val Muted = Color(0xFFAAB5C5)
private val Faint = Color(0xFF8793A5)
private val LineColor = Color(0xFF344054)
private val OtherBubble = Color(0xFF263448)
private val Amber = Color(0xFFF4C56A)
private val Coral = Color(0xFFFF6B6B)
private val Violet = Color(0xFFB794F4)

private val TealBrush = Brush.horizontalGradient(listOf(Teal, TealLight))
private val VioletBrush = Brush.horizontalGradient(listOf(Color(0xFF7C5CFF), Violet))
private val MyBubbleBrush = Brush.linearGradient(listOf(Color(0xFF1E9C82), Color(0xFF176B5B)))
private val SenderColors = listOf(
    Color(0xFF72D6BC), Color(0xFFFFB86B), Color(0xFFB794F4),
    Color(0xFF63B3ED), Color(0xFFF687B3), Color(0xFFF6E05E)
)

private val LifetimeSeconds = (LIFETIME_MS / 1000).toInt()

private enum class Conn { Idle, Connecting, Connected, Reconnecting, Failed }

private fun Conn.label() = when (this) {
    Conn.Idle -> "disconnected"
    Conn.Connecting -> "connecting..."
    Conn.Connected -> "connected"
    Conn.Reconnecting -> "reconnecting..."
    Conn.Failed -> "relay unreachable"
}

private fun Conn.color() = when (this) {
    Conn.Idle -> Faint
    Conn.Connecting, Conn.Reconnecting -> Amber
    Conn.Connected -> TealLight
    Conn.Failed -> Coral
}

class MainActivity : ComponentActivity() {

    private val repo by lazy {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "vanish.db").build()
        ChatRepository(applicationContext, db)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent { VanishApp(repo) }
    }
}

// =====================================================================
//  App: holds all state, runs the vanish timer and the relay sync loop
// =====================================================================
@Composable
fun VanishApp(repo: ChatRepository) {
    val messages by repo.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(repo.userName) }
    var relayInput by remember { mutableStateOf(repo.prettyRelay()) }
    var joined by remember { mutableStateOf(false) }
    var conn by remember { mutableStateOf(Conn.Idle) }
    var formError by remember { mutableStateOf("") }
    var errorDetail by remember { mutableStateOf("") }
    var session by remember { mutableIntStateOf(0) }       // 0 = not joined, >0 = sync loop running
    var showSettings by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Twice a second: update countdowns and delete expired messages (the "vanish" step).
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            repo.purgeExpired(now)
            delay(500)
        }
    }

    // Every second: send my messages to the relay, fetch new ones from it.
    LaunchedEffect(session) {
        if (session == 0) return@LaunchedEffect
        while (true) {
            try {
                repo.sync()
                if (repo.isConnected()) {
                    conn = Conn.Connected
                    errorDetail = ""
                    joined = true
                } else {
                    conn = if (joined) Conn.Reconnecting else Conn.Connecting
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorDetail = e.message ?: (e::class.java.simpleName)
                conn = if (joined) Conn.Reconnecting else Conn.Failed
            }
            delay(1000)
        }
    }

    fun join() {
        val n = name.trim()
        if (n.isEmpty()) { formError = "Please enter your display name."; return }
        if (relayInput.isBlank()) { formError = "Please enter the relay address (tap the gear for help)."; return }
        try {
            repo.setRelay(relayInput)
        } catch (e: IllegalArgumentException) {
            formError = "That relay address doesn't look valid."
            return
        }
        repo.userName = n.take(30)
        name = n.take(30)
        relayInput = repo.prettyRelay()
        formError = ""
        errorDetail = ""
        joined = false
        conn = Conn.Connecting
        session += 1
    }

    fun leave() {
        session = 0
        joined = false
        conn = Conn.Idle
        showSettings = false
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!joined) {
            JoinScreen(
                name = name,
                onName = { name = it; formError = "" },
                relay = relayInput,
                onRelay = { relayInput = it; formError = "" },
                conn = conn,
                formError = formError,
                errorDetail = errorDetail,
                onJoin = ::join,
                onSettings = { showSettings = true }
            )
        } else {
            ChatScreen(
                repo = repo,
                name = name,
                messages = messages.filter { it.expiresAt > now },
                now = now,
                conn = conn,
                onSettings = { showSettings = true }
            )
        }
    }

    if (showSettings) {
        RelaySettingsDialog(
            repo = repo,
            relay = relayInput,
            onRelay = { relayInput = it },
            joined = joined,
            onSave = {
                try {
                    repo.setRelay(relayInput)
                    relayInput = repo.prettyRelay()
                    if (session > 0) conn = Conn.Connecting
                    showSettings = false
                    true
                } catch (e: IllegalArgumentException) {
                    false
                }
            },
            onLeave = ::leave,
            onClose = { showSettings = false }
        )
    }
}

// =====================================================================
//  Join screen
// =====================================================================
@Composable
private fun JoinScreen(
    name: String,
    onName: (String) -> Unit,
    relay: String,
    onRelay: (String) -> Unit,
    conn: Conn,
    formError: String,
    errorDetail: String,
    onJoin: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, Bg)))
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Logo(76)
            Spacer(Modifier.height(18.dp))
            VanishTitle(34)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your class. Your conversations.\nGone in $LifetimeSeconds seconds.",
                color = Muted, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
                    .padding(22.dp)
            ) {
                Text("Join Class Network", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Enter your name and the relay address to connect with classmates.", color = Muted, lineHeight = 21.sp)
                Spacer(Modifier.height(20.dp))

                FieldLabel("DISPLAY NAME")
                DarkField(
                    value = name,
                    onValueChange = onName,
                    placeholder = "Enter your name",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(16.dp))
                FieldLabel("RELAY ADDRESS")
                DarkField(
                    value = relay,
                    onValueChange = onRelay,
                    placeholder = "e.g. 192.168.100.42:8000",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    onDone = onJoin
                )
                Spacer(Modifier.height(22.dp))

                PressButton(
                    text = when (conn) {
                        Conn.Connecting -> "Connecting..."
                        Conn.Failed -> "Try again"
                        else -> "Join Network"
                    },
                    enabled = conn != Conn.Connecting,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onJoin
                )

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(conn.color(), pulsing = conn == Conn.Connecting)
                    Spacer(Modifier.width(8.dp))
                    Text("Status: ${conn.label()}", color = conn.color(), fontSize = 13.sp)
                }

                AnimatedVisibility(visible = formError.isNotEmpty()) {
                    Text(formError, color = Coral, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
                AnimatedVisibility(visible = conn == Conn.Failed) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            "Can't reach the relay. Check that the server is running, the address is right, and the phone is on the same Wi-Fi.",
                            color = Amber, fontSize = 12.sp, lineHeight = 18.sp
                        )
                        if (errorDetail.isNotEmpty()) {
                            Text(errorDetail, color = Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Messages automatically expire after $LifetimeSeconds seconds.",
                color = Faint, fontSize = 12.sp, textAlign = TextAlign.Center
            )
        }

        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
            GearButton(onSettings)
        }
    }
}

// =====================================================================
//  Chat screen
// =====================================================================
@Composable
private fun ChatScreen(
    repo: ChatRepository,
    name: String,
    messages: List<MessageEntity>,
    now: Long,
    conn: Conn,
    onSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { repo.send(t) }
    }

    Column(Modifier.fillMaxSize().background(Bg).imePadding()) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardBg)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                VanishTitle(22)
                Text("Class Network  ·  $name", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            StatusBadge(conn)
            Spacer(Modifier.width(10.dp))
            GearButton(onSettings)
        }

        // notice
        Box(Modifier.fillMaxWidth().background(Color(0xFF3B3020)).padding(8.dp), contentAlignment = Alignment.Center) {
            Text("Every message vanishes $LifetimeSeconds seconds after it arrives.", color = Amber, fontSize = 12.sp)
        }
        AnimatedVisibility(visible = conn == Conn.Reconnecting) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF4A2A2A)).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("Relay unreachable. Retrying... new messages will send when it is back.", color = Coral, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }

        // messages
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { m ->
                        MessageRow(m, mine = repo.isMine(m), now = now)
                    }
                }
            }
        }

        // quick replies + input
        Column(Modifier.fillMaxWidth().background(CardBg).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("👋 Hi", "😂", "👍", "🔥", "❤️", "👀", "On my way!").forEach { q ->
                    QuickChip(q) { sendText(q) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DarkField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    placeholder = "Write a message...",
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Send,
                    onDone = { sendText(draft); draft = "" }
                )
                Spacer(Modifier.width(10.dp))
                SendButton(enabled = draft.isNotBlank()) { sendText(draft); draft = "" }
            }
        }
    }
}

@Composable
private fun MessageRow(m: MessageEntity, mine: Boolean, now: Long) {
    val msLeft = (m.expiresAt - now).coerceAtLeast(0)
    val secLeft = ((msLeft + 999) / 1000).toInt()
    val frac by animateFloatAsState(
        targetValue = (msLeft.toFloat() / LIFETIME_MS).coerceIn(0f, 1f),
        animationSpec = tween(500, easing = LinearEasing),
        label = "frac"
    )
    val fade by animateFloatAsState(
        targetValue = if (secLeft <= 3) 0.55f else 1f,
        animationSpec = tween(400),
        label = "fade"
    )
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val barColor = if (frac > 0.5f) lerp(Amber, TealSoft, (frac - 0.5f) * 2f) else lerp(Coral, Amber, frac * 2f)
    val senderColor = SenderColors[abs(m.sender.hashCode()) % SenderColors.size]

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = 0.8f + 0.2f * enter.value
                scaleX = s
                scaleY = s
                alpha = enter.value.coerceIn(0f, 1f) * fade
                transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
            },
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(senderColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(m.sender.take(1).uppercase(), color = senderColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }
        val shape = if (mine) RoundedCornerShape(16.dp, 16.dp, 3.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 3.dp)
        Column(
            Modifier
                .widthIn(min = 120.dp, max = 280.dp)
                .clip(shape)
                .then(if (mine) Modifier.background(MyBubbleBrush) else Modifier.background(OtherBubble))
                .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 9.dp)
        ) {
            Text(if (mine) "You" else m.sender, color = if (mine) TealSoft else senderColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(m.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (!mine) "" else if (m.dirty) "sending..." else "✓ delivered",
                    color = Color(0xFFB8C9C5), fontSize = 10.sp
                )
                Text("vanishes in ${secLeft}s", color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(barColor))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val t = rememberInfiniteTransition(label = "empty")
    val bob by t.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✉", fontSize = 46.sp, color = Teal, modifier = Modifier.graphicsLayer { translationY = bob * density })
        Spacer(Modifier.height(14.dp))
        Text("No messages yet", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(
            "Say hi with a quick reply below. It will vanish in $LifetimeSeconds seconds.",
            color = Faint, textAlign = TextAlign.Center, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// =====================================================================
//  Relay settings (the gear)
// =====================================================================
@Composable
private fun RelaySettingsDialog(
    repo: ChatRepository,
    relay: String,
    onRelay: (String) -> Unit,
    joined: Boolean,
    onSave: () -> Boolean,
    onLeave: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }   // null = not tested yet
    var ok by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CardBg,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = { Text("Relay settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Enter the shared Vanish Relay Protocol v1 WebSocket address. " +
                        "Everyone in the class must use exactly the same relay.",
                    lineHeight = 20.sp, fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel("RELAY ADDRESS")
                DarkField(
                    value = relay,
                    onValueChange = { onRelay(it); result = null; saveError = "" },
                    placeholder = "wss://vanish-relay-sawaira.bonto.page/ws",
                    keyboardType = KeyboardType.Uri
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("Use class relay") { onRelay("wss://vanish-relay-sawaira.bonto.page/ws"); result = null }
                }
                Text(
                    "Use the shared secure wss:// address supplied by your class protocol.",
                    fontSize = 12.sp, color = Faint, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp)
                )
                Spacer(Modifier.height(14.dp))
                PressButton(
                    text = if (testing) "Testing..." else "Test connection",
                    enabled = !testing,
                    brush = VioletBrush,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    testing = true
                    scope.launch {
                        val err = repo.testRelay(relay)
                        ok = err == null
                        result = err ?: "Relay reachable. Ready to chat!"
                        testing = false
                    }
                }
                AnimatedVisibility(visible = result != null) {
                    Text(
                        (if (ok) "✓ " else "✗ ") + (result ?: ""),
                        color = if (ok) TealLight else Coral,
                        fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (saveError.isNotEmpty()) {
                    Text(saveError, color = Coral, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (joined) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onLeave) { Text("Leave network", color = Coral) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onSave()) saveError = "That address doesn't look valid." }) {
                Text("Save", color = TealLight, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = Muted) } }
    )
}

// =====================================================================
//  Small reusable pieces
// =====================================================================
@Composable
private fun Logo(size: Int) {
    val t = rememberInfiniteTransition(label = "logo")
    val pulse by t.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val ring by t.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = ring }.background(Teal, CircleShape))
        Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Teal, TealLight)), CircleShape), contentAlignment = Alignment.Center) {
            Text("V", fontSize = (size / 2).sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun VanishTitle(sizeSp: Int) {
    Text(
        "VANISH",
        style = TextStyle(
            brush = Brush.horizontalGradient(listOf(Color.White, TealSoft)),
            fontSize = sizeSp.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (sizeSp / 7).sp
        )
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Faint) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() },
            onSend = { onDone?.invoke() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Teal,
            unfocusedBorderColor = LineColor,
            cursorColor = TealLight,
            focusedContainerColor = Bg,
            unfocusedContainerColor = Bg
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PressButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = TealBrush,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.6f }
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chip"
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(OtherBubble)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else if (enabled) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "send"
    )
    Box(
        Modifier
            .size(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(14.dp))
            .background(TealBrush)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("➤", color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun GearButton(onClick: () -> Unit) {
    var turns by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = turns,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "gear"
    )
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF2A3547))
            .clickable { turns += 120f; onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("⚙", fontSize = 22.sp, color = Muted, modifier = Modifier.graphicsLayer { rotationZ = rotation })
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dota"
    )
    Box(Modifier.size(9.dp).graphicsLayer { alpha = if (pulsing) a else 1f }.background(color, CircleShape))
}

@Composable
private fun StatusBadge(conn: Conn) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(conn.color().copy(alpha = 0.16f)).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(conn.color(), pulsing = conn != Conn.Connected && conn != Conn.Idle)
        Spacer(Modifier.width(6.dp))
        Text(conn.label(), color = conn.color(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
