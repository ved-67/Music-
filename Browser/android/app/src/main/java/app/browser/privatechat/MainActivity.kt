package app.browser.privatechat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val Navy = Color(0xFF101A2B)
private val Blue = Color(0xFF2675E8)
private val SurfaceDark = Color(0xFF1B2940)
private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

data class ChatMessage(val id: String, val sender: String, val kind: String, val body: String, val created: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { BrowserApp() } }
}

@Composable
fun BrowserApp() {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connect your private backend to start.") }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    suspend fun request(path: String, method: String = "GET", body: String? = null, auth: String = token, contentType: String = "application/json"): String = withContext(Dispatchers.IO) {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + path
        val builder = Request.Builder().url(url).addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
        if (auth.isNotBlank()) builder.addHeader("Authorization", "Bearer $auth")
        if (body != null) builder.method(method, body.toRequestBody(contentType.toMediaType())) else builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Server ${response.code}: ${text.take(220)}")
            text
        }
    }

    suspend fun loadMessages() {
        if (token.isBlank()) return
        val result = request("/rest/v1/messages?select=id,sender_id,kind,body,created_at&order=created_at.asc&limit=200")
        val arr = JSONArray(result)
        messages = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(o.optString("id"), o.optString("sender_id"), o.optString("kind"), o.optString("body"), o.optString("created_at"))
        }
    }

    suspend fun sendMessage(kind: String, body: String) {
        val payload = JSONObject().put("sender_id", userId).put("kind", kind).put("body", body).toString()
        request("/rest/v1/messages", "POST", payload).also { loadMessages() }
    }

    fun authenticate(signUp: Boolean) {
        if (!configured) { status = "Backend not configured. Follow Browser/README.md and add GitHub Actions secrets."; return }
        scope.launch {
            busy = true
            try {
                val route = if (signUp) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
                val response = withContext(Dispatchers.IO) {
                    val body = JSONObject().put("email", email.trim()).put("password", password).toString()
                    val req = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + route)
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .post(body.toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).execute().use { res ->
                        val text = res.body?.string().orEmpty()
                        if (!res.isSuccessful) throw IllegalStateException("Sign-in failed: ${text.take(220)}")
                        JSONObject(text)
                    }
                }
                token = response.optString("access_token")
                userId = response.optJSONObject("user")?.optString("id").orEmpty()
                if (token.isBlank()) throw IllegalStateException("Account created. Sign in after confirming your email if confirmation is enabled.")
                status = "Connected"
                loadMessages()
            } catch (e: Exception) { status = e.message ?: "Connection failed" }
            busy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = SurfaceDark)) {
        Surface(Modifier.fillMaxSize(), color = Navy) {
            if (token.isBlank()) {
                Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).background(Blue, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                        Text("🌐", fontSize = 48.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Browser", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Private chat for two", color = Color.LightGray)
                    Spacer(Modifier.height(30.dp))
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { authenticate(false) }, Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.length >= 6) { Text(if (busy) "Please wait…" else "Sign in") }
                    TextButton(onClick = { authenticate(true) }, enabled = !busy) { Text("Create account") }
                    Text(status, color = Color.LightGray, fontSize = 12.sp)
                    if (!configured) Text("Backend setup is required before accounts can connect.", color = Color(0xFFFFCC80), fontSize = 12.sp)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) { Text("🌐") }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text("Browser", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp); Text("Private conversation", color = Color.LightGray, fontSize = 12.sp) }
                        IconButton(onClick = { scope.launch { try { loadMessages(); status = "Updated" } catch (e: Exception) { status = e.message ?: "Sync failed" } } }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                    }
                    Divider(color = Color.DarkGray)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), reverseLayout = false) {
                        items(messages, key = { it.id }) { msg ->
                            val mine = msg.sender == userId
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                Column(Modifier.padding(vertical = 4.dp).background(if (mine) Blue else SurfaceDark, RoundedCornerShape(18.dp)).padding(12.dp).widthIn(max = 280.dp)) {
                                    when (msg.kind) {
                                        "voice" -> Text("🎙 Voice note · ${msg.body}", color = Color.White)
                                        "photo" -> Text("📷 Photo · ${msg.body}", color = Color.White)
                                        else -> Text(msg.body, color = Color.White)
                                    }
                                    Text(msg.created.take(16).replace("T", " "), color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Text(status, Modifier.padding(horizontal = 16.dp), color = Color.LightGray, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Message…") }, maxLines = 4)
                        IconButton(onClick = {
                            if (draft.isNotBlank()) scope.launch { try { sendMessage("text", draft.trim()); draft = ""; status = "Sent" } catch (e: Exception) { status = e.message ?: "Send failed" } }
                        }) { Icon(Icons.Default.Send, "Send", tint = Blue) }
                        IconButton(onClick = {
                            if (!recording) {
                                val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
                                try {
                                    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                                    r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setOutputFile(file.absolutePath); r.prepare(); r.start()
                                    recorder = r; audioFile = file; recording = true; status = "Recording voice note…"
                                } catch (e: Exception) { status = "Microphone error: ${e.message}" }
                            } else {
                                try { recorder?.stop(); recorder?.release(); recorder = null; recording = false
                                    val f = audioFile
                                    if (f != null) scope.launch { try { val bytes = withContext(Dispatchers.IO) { f.readBytes() }; val path = "/storage/v1/object/voice-notes/${userId}/${f.name}"
                                        withContext(Dispatchers.IO) { val req = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + path).addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY).addHeader("Authorization", "Bearer $token").addHeader("Content-Type", "audio/mp4").post(bytes.toRequestBody("audio/mp4".toMediaType())).build(); client.newCall(req).execute().use { if (!it.isSuccessful) error("Voice upload failed: ${it.code}") } }
                                        sendMessage("voice", path); status = "Voice note sent"
                                    } catch (e: Exception) { status = e.message ?: "Voice upload failed" } }
                                } catch (e: Exception) { recording = false; status = e.message ?: "Recording failed" }
                            }
                        }) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, "Voice note", tint = if (recording) Color.Red else Blue) }
                        IconButton(onClick = { status = "Photo picker: choose a photo to share." }) { Icon(Icons.Default.Image, "Photo", tint = Blue) }
                    }
                    LaunchedEffect(token) {
                        while (isActive) { delay(4000); try { loadMessages() } catch (_: Exception) { } }
                    }
                }
            }
        }
    }
}
