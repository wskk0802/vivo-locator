package com.example.vivolocator

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import com.tencent.smtt.sdk.CookieManager
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import com.tencent.smtt.sdk.IX5WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private var webViewInstance: WebView? = null
    private var autoFetchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyperOSLocatorApp(
                onWebViewCreated = { webViewInstance = it },
                onManualRefresh = { executeLocationFetch() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        startRandomIntervalPolling()
    }

    override fun onPause() {
        super.onPause()
        autoFetchJob?.cancel()
    }

    private fun startRandomIntervalPolling() {
        autoFetchJob?.cancel()
        autoFetchJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                executeLocationFetch()
                delay(Random.nextLong(50_000, 70_000))
            }
        }
    }

    private fun executeLocationFetch() {
        webViewInstance?.evaluateJavascript(
            "(function(){ var e = document.querySelector('.location-address') || document.querySelector('.device-address') || document.querySelector('.device-status-info'); return e ? e.innerText.trim() : '已发送刷新指令，正在同步最新位置...'; })();"
        ) { result ->
            UpdateState.currentLocation = result?.replace("\"", "") ?: "等待登录或加载..."
            UpdateState.lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            UpdateState.isRefreshing = false
        }
    }
}

object UpdateState {
    var currentLocation by mutableStateOf("未获取到位置，请先登录 vivo 账号并过验证")
    var lastUpdated by mutableStateOf("未更新")
    var isRefreshing by mutableStateOf(false)
    var showWebViewLogin by mutableStateOf(true)
}

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

private val desktopJsInject = """
    (function() {
        try {
            Object.defineProperty(window, 'outerWidth', {get: () => 1920});
            Object.defineProperty(window, 'outerHeight', {get: () => 1080});
            Object.defineProperty(screen, 'width', {get: () => 1920});
            Object.defineProperty(screen, 'height', {get: () => 1080});
            Object.defineProperty(navigator, 'userAgent', {get: () => '$DESKTOP_UA'});
            Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});
            Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
            window.navigator.chrome = {runtime: {}};
            var m = document.querySelector('meta[name="viewport"]');
            if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
            m.content = 'width=1280';
        } catch(e) {}
    })();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperOSLocatorApp(
    onWebViewCreated: (WebView) -> Unit,
    onManualRefresh: () -> Unit
) {
    val context = LocalContext.current
    val hyperBgColor = Color(0xFFF4F4F6)
    val cardBgColor = Color(0xFFFFFFFF)
    val hyperAccentBlue = Color(0xFF007AFF)
    val textPrimary = Color(0xFF1D1D1F)
    val textSecondary = Color(0xFF8E8E93)

    Scaffold(
        containerColor = hyperBgColor,
        topBar = {
            TopAppBar(
                title = { Text("vivo 亲友位置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textPrimary) },
                actions = {
                    TextButton(onClick = { UpdateState.showWebViewLogin = !UpdateState.showWebViewLogin }) {
                        Text(if (UpdateState.showWebViewLogin) "隐藏网页" else "显示网页", color = hyperAccentBlue, fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = hyperBgColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
        ) {
            AnimatedVisibility(
                visible = UpdateState.showWebViewLogin,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.weight(1f)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.userAgentString = DESKTOP_UA
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(true)
                                // X5 下 mixedContentMode 通过 set 方法
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                cm.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(desktopJsInject, null)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        url?.let {
                                            if (it.contains("/m.") || it.contains("/m/") || it.contains("mobile=1")) {
                                                val fixed = it.replace("://m.", "://find.")
                                                    .replace("/m/", "/")
                                                    .replace("mobile=1", "")
                                                view?.loadUrl(fixed)
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }

                                // X5 的 onCreateWindow 通过 IX5WebChromeClient 接口
                                webChromeClient = object : WebChromeClient(), IX5WebChromeClient {
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?
                                    ): Boolean {
                                        val newWebView = WebView(context).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.databaseEnabled = true
                                            settings.userAgentString = DESKTOP_UA
                                            settings.setSupportMultipleWindows(true)
                                        }
                                        newWebView.webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(v: WebView?, u: String?) {
                                                super.onPageFinished(v, u)
                                                v?.evaluateJavascript(desktopJsInject, null)
                                            }
                                        }
                                        // X5 用 WebViewTransport（com.tencent 包下的）
                                        val transport = resultMsg?.obj as? com.tencent.smtt.sdk.WebView.WebViewTransport
                                        transport?.webView = newWebView
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }

                                loadUrl("https://find.vivo.com.cn")
                                onWebViewCreated(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 定位卡片
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF34C759)))
                            Spacer(Modifier.width(8.dp))
                            Text("智能防护中", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF0F0F2)) {
                            Text("上次更新: ${UpdateState.lastUpdated}", fontSize = 11.sp, color = textSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("亲友当前位置", fontSize = 12.sp, color = textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(UpdateState.currentLocation, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                }
            }

            // 刷新按钮
            Button(
                onClick = { UpdateState.isRefreshing = true; onManualRefresh() },
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = hyperAccentBlue),
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 6.dp)
            ) {
                Text(if (UpdateState.isRefreshing) "正在抓取中..." else "立即手动刷新", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
