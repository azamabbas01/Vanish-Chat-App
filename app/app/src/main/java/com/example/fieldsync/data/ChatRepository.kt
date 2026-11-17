package com.example.fieldsync.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.cancellation.CancellationException

const val LIFETIME_MS = 30_000L

/**
 * Client for Vanish Relay Protocol v1.
 * Messages are queued locally while offline, but are never sent after their
 * original 30-second lifetime. The relay is authoritative for expiry.
 */
class ChatRepository(context: Context, db: AppDatabase) {
    private val dao = db.messageDao()
    private val prefs = context.getSharedPreferences("vanish", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS) // protocol-level JSON ping is used instead
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val deviceId: String = prefs.getString("device_id", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    private val idPrefix = deviceId.take(8)

    @Volatile private var socket: WebSocket? = null
    @Volatile private var joined = false
    @Volatile private var connecting = false
    @Volatile private var serverOffsetMs = 0L
    @Volatile private var lastPongAt = 0L
    @Volatile private var lastPingAt = 0L
    @Volatile private var reconnectAt = 0L
    @Volatile private var reconnectDelayMs = 1_000L
    @Volatile private var lastError: String? = null

    var userName: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) { prefs.edit().putString("name", value.take(30)).apply() }

    var relayUrl: String = prefs.getString("relay", DEFAULT_RELAY).orEmpty()
        private set

    fun isMine(m: MessageEntity): Boolean = m.id.startsWith("$idPrefix-")
    fun isConnected(): Boolean = joined
    fun observe(): Flow<List<MessageEntity>> = dao.observeAll()

    fun prettyRelay(): String = relayUrl

    fun setRelay(input: String) {
        val url = normalize(input)
        require(url.startsWith("wss://")) { "The shared class relay must use wss://." }
        closeSocket()
        relayUrl = url
        prefs.edit().putString("relay", url).apply()
        reconnectDelayMs = 1_000L
        reconnectAt = 0L
    }

    private fun normalize(input: String): String {
        var u = input.trim()
        if (u.isEmpty()) return ""
        if (!u.startsWith("ws://") && !u.startsWith("wss://")) {
            u = if (u.startsWith("https://")) "wss://" + u.removePrefix("https://")
                 else if (u.startsWith("http://")) "ws://" + u.removePrefix("http://")
                 else "wss://$u"
        }
        if (!u.endsWith("/ws")) u = u.trimEnd('/') + "/ws"
        return u
    }

    suspend fun testRelay(input: String): String? {
        val url = try { normalize(input) } catch (_: Exception) { return "Invalid relay address." }
        if (!url.startsWith("wss://")) return "Use the secure wss:// class relay address."
        return try {
            val welcome = CompletableDeferred<Boolean>()
            val req = Request.Builder().url(url).build()
            val testSocket = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val join = JsonObject().apply {
                        addProperty("type", "join")
                        addProperty("protocol", 1)
                        addProperty("device_id", deviceId)
                        addProperty("name", userName.ifBlank { "Test" }.take(30))
                    }
                    webSocket.send(gson.toJson(join))
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val type = JsonParser.parseString(text).asJsonObject.get("type")?.asString
                        if (type == "welcome") welcome.complete(true)
                        if (type == "error") welcome.complete(false)
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!welcome.isCompleted) welcome.completeExceptionally(t)
                }
            })
            try {
                withTimeout(12_000) { if (welcome.await()) null else "Relay rejected the JOIN request." }
            } finally { testSocket.close(1000, "test complete") }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            "Timed out waiting for the relay welcome message. The free host may be waking up; try again."
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { "Can't connect to the shared relay: ${e.message ?: "connection failed"}" }
    }

    suspend fun send(text: String) {
        val clean = text.trim()
        require(clean.isNotEmpty() && clean.length <= 500) { "Messages must contain 1–500 characters." }
        val now = System.currentTimeMillis()
        dao.insert(MessageEntity(
            id = "$idPrefix-${UUID.randomUUID()}",
            sender = userName.ifBlank { "Anon" }.take(30),
            text = clean,
            createdAt = now,
            expiresAt = now + LIFETIME_MS,
            version = 0,
            dirty = true
        ))
    }

    suspend fun purgeExpired(now: Long) = dao.purgeExpired(now)

    /** Called by the existing one-second UI loop. Owns reconnect, heartbeat and delivery. */
    suspend fun sync() {
        val now = System.currentTimeMillis()
        if (joined && now - lastPongAt > 30_000L) {
            closeSocket()
            scheduleReconnect()
        }
        if (!joined && !connecting && now >= reconnectAt) connect()
        if (!joined) return

        if (now - lastPingAt >= 15_000L) {
            socket?.send("""{"type":"ping"}""")
            lastPingAt = now
        }
        val pending = dao.pending(now)
        for (m in pending) {
            if (m.expiresAt <= now) continue
            val packet = JsonObject().apply {
                addProperty("type", "message")
                addProperty("id", m.id)
                addProperty("sender", userName.take(30))
                addProperty("text", m.text)
            }
            if (socket?.send(gson.toJson(packet)) != true) break
        }
    }

    private fun connect() {
        val req = Request.Builder().url(relayUrl).build()
        connecting = true
        lastError = null
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = JsonObject().apply {
                    addProperty("type", "join")
                    addProperty("protocol", 1)
                    addProperty("device_id", deviceId)
                    addProperty("name", userName.take(30))
                }
                webSocket.send(gson.toJson(join))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JsonParser.parseString(text).asJsonObject
                    when (obj.get("type")?.asString) {
                        "welcome" -> {
                            if (obj.get("protocol")?.asInt != 1) {
                                lastError = "Relay protocol version mismatch"
                                webSocket.close(1002, "protocol mismatch")
                                return
                            }
                            serverOffsetMs = obj.get("server_time").asLong - System.currentTimeMillis()
                            joined = true
                            connecting = false
                            lastPongAt = System.currentTimeMillis()
                            lastPingAt = lastPongAt
                            reconnectDelayMs = 1_000L
                            reconnectAt = 0L
                        }
                        "pong" -> lastPongAt = System.currentTimeMillis()
                        "message" -> {
                            val id = obj.get("id")?.asString ?: return
                            val sender = obj.get("sender")?.asString ?: "Anon"
                            val body = obj.get("text")?.asString ?: return
                            val sentAt = obj.get("sent_at")?.asLong ?: return
                            val expiresAt = obj.get("expires_at")?.asLong ?: return
                            val localExpiry = expiresAt - serverOffsetMs
                            val localSent = sentAt - serverOffsetMs
                            val now = System.currentTimeMillis()
                            if (localExpiry <= now) return
                            // The relay echoes to the sender; Room's primary key deduplicates it.
                            onIncomingMessage(id, sender, body, localSent, localExpiry)
                        }
                        "delivered" -> {
                            val id = obj.get("id")?.asString ?: return
                            onDelivered(id)
                        }
                        "expired" -> {
                            val id = obj.get("id")?.asString ?: return
                            onExpired(id)
                        }
                        "error" -> lastError = obj.get("message")?.asString ?: obj.get("code")?.asString
                    }
                } catch (_: Exception) { /* Ignore malformed/unrecognized frames safely. */ }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                joined = false
                connecting = false
                socket = null
                lastError = t.message
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                joined = false
                connecting = false
                socket = null
                scheduleReconnect()
            }
        })
    }

    // WebSocket callbacks are not suspend functions. Use a small dedicated coroutine
    // scope tied to the process; Room serializes inserts and uses IGNORE for duplicate IDs.
    private fun onIncomingMessage(id: String, sender: String, text: String, created: Long, expiry: Long) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            dao.insert(MessageEntity(id, sender, text, created, expiry, 1, false))
        }
    }
    private fun onDelivered(id: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { dao.markSynced(id, 1) }
    }
    private fun onExpired(id: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { dao.deleteById(id) }
    }

    private fun scheduleReconnect() {
        reconnectAt = System.currentTimeMillis() + reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(8_000L)
    }

    private fun closeSocket() {
        joined = false
        connecting = false
        socket?.close(1000, "client closing")
        socket = null
    }
}
