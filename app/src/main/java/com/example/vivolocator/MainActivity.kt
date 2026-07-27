package com.example.vivolocator

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
                val nextDelay = Random.nextLong(50_000, 70_000)
                delay(nextDelay)
            }
        }
    }

    private fun executeLocationFetch() {
        webViewInstance?.let { webView ->
            val jsFetchScript = """
                (function() {
                    var addrElem = document.querySelector('.location-address') 
                                || document.querySelector('.device-address')
                                || document.querySelector('.device-status-info');
                    if (addrElem && addrElem.innerText.trim().length > 0) {
                        return addrElem.innerText.trim();
                    }
                    return '已发送刷新指令，正在同步最新位置...';
                })();
            """.trimIndent()

            webView.evaluateJavascript(jsFetchScript) { result ->
                val cleanResult = result?.replace("\"", "") ?: "等待登录或加载..."
                UpdateState.currentLocation = cleanResult
                UpdateState.lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                UpdateState.isRefreshing = false
            }
        }
    }
}

object UpdateState {
    var currentLocation by mutableStateOf("未获取到位置，请先点击右上角登录 vivo 账号")
    var lastUpdated by mutableStateOf("未更新")
    var isRefreshing by mutableStateOf(false)
    var showWebViewLogin by mutableStateOf(true)
}

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperOSLocatorApp(
    onWebViewCreated: (WebView) -> Unit,
    onManualRefresh: () -> Unit
) {
    val hyperBgColor = Color(0xFFF4F4F6)
    val cardBgColor = Color(0xFFFFFFFF)
    val hyperAccentBlue = Color(0xFF007AFF)
    val textPrimary = Color(0xFF1D1D1F)
    val textSecondary = Color(0xFF8E8E93)

    Scaffold(
        containerColor = hyperBgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "vivo 亲友位置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                },
                actions = {
                    TextButton(onClick = { UpdateState.showWebViewLogin = !UpdateState.showWebViewLogin }) {
                        Text(
                            text = if (UpdateState.showWebViewLogin) "隐藏网页" else "显示网页",
                            color = hyperAccentBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = hyperBgColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(bottom = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    userAgentString = DESKTOP_UA
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    javaScriptCanOpenWindowsAutomatically = true
                                    // 不设 true 了，避免需要 onCreateWindow 的麻烦
                                    setSupportMultipleWindows(false)
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url)
                                        injectDesktopJs(view)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        injectDesktopJs(view)
                                    }

                                    // ★ 拦截所有跳转，阻止跳手机版，统一在当前 WebView 加载
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val mobilePatterns = listOf("/m/", "m.cloud.vivo", "mobile=1", "/wap/")
                                        for (pattern in mobilePatterns) {
                                            if (url.contains(pattern)) {
                                                val fixedUrl = url
                                                    .replace("://m.", "://cloud.")
                                                    .replace("/m/", "/")
                                                    .replace("mobile=1", "")
                                                view?.loadUrl(fixedUrl)
                                                return true
                                            }
                                        }
                                        return false
                                    }

                                    private fun injectDesktopJs(view: WebView?) {
                                        val js = """
                                            (function() {
                                                try {
                                                    Object.defineProperty(window, 'outerWidth', {get: () => 1920});
                                                    Object.defineProperty(window, 'outerHeight', {get: () => 1080});
                                                    Object.defineProperty(screen, 'width', {get: () => 1920});
                                                    Object.defineProperty(screen, 'height', {get: () => 1080});
                                                    Object.defineProperty(navigator, 'userAgent', {
                                                        get: () => '$DESKTOP_UA'
                                                    });
                                                    Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});
                                                    Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                                                    window.navigator.chrome = { runtime: {} };
                                                    delete window.ontouchstart;
                                                    delete window.ontouchmove;
                                                    delete window.ontouchend;
                                                    var meta = document.querySelector('meta[name="viewport"]');
                                                    if (!meta) {
                                                        meta = document.createElement('meta');
                                                        meta.name = 'viewport';
                                                        document.getElementsByTagName('head')[0].appendChild(meta);
                                                    }
                                                    meta.content = 'width=1280, initial-scale=0.3, maximum-scale=2.0, user-scalable=yes';
                                                } catch(e) {}
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(js, null)
                                    }
                                }

                                // 简单的 WebChromeClient，不做新窗口
                                webChromeClient = WebChromeClient()

                                loadUrl("https://cloud.vivo.com.cn")
                                onWebViewCreated(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ---- 以下 UI 完全不变 ----
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "智能防护中", fontSize = 13.sp, color = textSecondary, fontWeight = FontWeight.Medium)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF0F0F2)) {
                            Text(
                                text = "上次更新: ${UpdateState.lastUpdated}",
                                fontSize = 11.sp,
                                color = textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "亲友当前位置", fontSize = 12.sp, color = textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = UpdateState.currentLocation,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        lineHeight = 20.sp
                    )
                }
            }

            Button(
                onClick = {
                    UpdateState.isRefreshing = true
                    onManualRefresh()
                },
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = hyperAccentBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = if (UpdateState.isRefreshing) "正在抓取中..." else "立即手动刷新",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
