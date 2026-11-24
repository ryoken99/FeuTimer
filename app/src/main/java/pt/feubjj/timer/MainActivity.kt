package pt.feubjj.timer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import pt.feubjj.timer.network.ScoreboardLanServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var page: WebView
    private var server: ScoreboardLanServer? = null
    private val state = MutableStateFlow("{}")
    private var loaded = false
    private val origin = "https://timer.feu.invalid"
    private val profile by lazy { legacyProfile(this) }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        page = runCatching { WebView(this) }.getOrElse { useClassic(); return }
        page.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        page.setBackgroundColor(android.graphics.Color.BLACK)
        page.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            @Deprecated("Compatibility for older TV boxes")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val uri = request.url
                val path = uri.path.orEmpty().removePrefix("/")
                if (uri.scheme == "https" && uri.authority == "timer.feu.invalid" &&
                    path.split('/').all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it != ".." }) {
                    val mime = when (path.substringAfterLast('.')) {
                        "html" -> "text/html"
                        "css" -> "text/css"
                        "js" -> "application/javascript"
                        "mp3" -> "audio/mpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    runCatching { assets.open("timer/" + path) }.getOrNull()?.let {
                        val response = WebResourceResponse(mime, if (mime.startsWith("text") || mime.contains("javascript")) "UTF-8" else null, it)
                        if (mime == "text/html") response.responseHeaders = mapOf("Content-Security-Policy" to
                            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-src 'none'; object-src 'none'; base-uri 'none'")
                        return response
                    }
                }
                return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), null)
            }
        }
        page.addJavascriptInterface(Bridge(), "FeuTv")
        setContentView(page)
        page.loadUrl(origin + "/index.html?tv=1")
        page.postDelayed({ if (!loaded && !isDestroyed && !isFinishing) useClassic() }, 10000)
    }

    private fun useClassic() {
        startActivity(Intent(this, ClassicTimerActivity::class.java))
        finish()
    }

    private inner class Bridge {
        @JavascriptInterface fun legacySettings() = profile
        @JavascriptInterface fun publishState(json: String) { state.value = json }
        @JavascriptInterface fun ready() = runOnUiThread {
            if (!loaded && !isDestroyed && !isFinishing) {
                loaded = true
                server = ScoreboardLanServer(state, { state.value }, ::receiveCommand, ::updateLan,
                    { assets.open("referee/index.html").use { it.readBytes() } }, lifecycleScope)
                server?.start()
            }
        }
        @JavascriptInterface fun exitHome() = runOnUiThread { finish() }
        @JavascriptInterface fun openUpdate() = runOnUiThread {
            val url = "https://github.com/ryoken99/FeuTimer/releases/latest/download/FeuTimer-AndroidTV.apk"
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure {
                Toast.makeText(this@MainActivity, "Instala o APK da última versão por USB ou ADB.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateLan(url: String?, clients: Int) = runOnUiThread {
        if (loaded && !isDestroyed) page.evaluateJavascript("window.feuLan(" + JSONObject.quote(url.orEmpty()) + ", " + clients + ")", null)
    }

    private fun receiveCommand(raw: String): String {
        val completed = CountDownLatch(1)
        runOnUiThread {
            if (loaded && !isDestroyed) {
                page.evaluateJavascript("window.feuCommand(JSON.parse(" + JSONObject.quote(raw) + "))") { completed.countDown() }
            } else completed.countDown()
        }
        completed.await(2, TimeUnit.SECONDS)
        return state.value
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!loaded || event.action !in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP))
            return super.dispatchKeyEvent(event)
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
            KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
            KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> "Escape"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_BUTTON_START -> "MediaPlayPause"
            else -> null
        }
        if (key != null) {
            val type = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
            page.evaluateJavascript("(document.activeElement || document.body).dispatchEvent(new KeyboardEvent('" + type +
                "',{key:'" + key + "',bubbles:true,cancelable:true,repeat:" + (event.repeatCount > 0) + "}))", null)
            return true
        }
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "pause"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "resume"
            KeyEvent.KEYCODE_MEDIA_STOP -> "stop"
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> "skip_phase"
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "previous_round"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "plus_10s"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "minus_10s"
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) page.evaluateJavascript("window.feuMedia('" + action + "')", null)
        return true
    }

    override fun onDestroy() {
        loaded = false
        server?.stop()
        if (::page.isInitialized) {
            page.removeJavascriptInterface("FeuTv")
            page.destroy()
        }
        super.onDestroy()
    }
}
