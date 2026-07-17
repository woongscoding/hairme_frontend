package com.example.myapplication

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.network.Place

/**
 * WebView 기반 OpenStreetMap 구현
 * API 키 없이 무료로 사용 가능
 */

@Composable
fun SalonMapWebView(
    salons: List<Place>,
    currentLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var reloadTrigger by remember { mutableStateOf(0) }

    // WebView 디버깅 활성화 (개발 중에만)
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // 기본 설정
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    // 하드웨어 가속
                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

                    // 캐시 설정
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                    // Mixed Content 허용
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    // WebViewClient 설정
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("SalonMapWebView", "✅ 페이지 로딩 완료")
                            hasError = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.e("SalonMapWebView", "❌ 에러: $description")
                            hasError = true
                            errorMessage = description ?: "알 수 없는 오류"
                        }
                    }

                    // WebChromeClient 설정
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                val tag = when (it.messageLevel()) {
                                    ConsoleMessage.MessageLevel.ERROR -> "❌"
                                    ConsoleMessage.MessageLevel.WARNING -> "⚠️"
                                    else -> "ℹ️"
                                }
                                Log.d("WebView-JS", "$tag ${it.message()}")
                            }
                            return true
                        }
                    }

                    // JavaScript Interface
                    addJavascriptInterface(WebAppInterface(), "Android")

                    // HTML 로드
                    val html = createSimpleMapHtml(salons, currentLocation)
                    Log.d("SalonMapWebView", "🗺️ 지도 로딩 시작 - 미용실 ${salons.size}개")
                    Log.d("SalonMapWebView", "📍 위치: $currentLocation")

                    loadDataWithBaseURL("https://map.local/", html, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                if (reloadTrigger > 0) {
                    webView.reload()
                }
            }
        )

        // 에러 UI
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "지도를 불러올 수 없습니다",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        hasError = false
                        reloadTrigger++
                    }) {
                        Text("다시 시도")
                    }
                }
            }
        }
    }
}

private fun createSimpleMapHtml(
    salons: List<Place>,
    currentLocation: Pair<Double, Double>?
): String {
    val lat = currentLocation?.first ?: 37.5665
    val lng = currentLocation?.second ?: 126.9780

    // 미용실 마커 HTML 생성
    val markers = salons.joinToString("\n") { salon ->
        val name = salon.place_name.replace("'", "\\'").replace("\"", "&quot;")
        val addr = salon.address_name.replace("'", "\\'").replace("\"", "&quot;")
        val phone = salon.phone.replace("'", "\\'")
        """
        L.marker([${salon.y}, ${salon.x}])
            .addTo(map)
            .bindPopup('<b>$name</b><br>${salon.distance}m<br>$phone<br>$addr');
        """.trimIndent()
    }

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <style>
        body { margin: 0; padding: 0; }
        #map { position: absolute; top: 0; bottom: 0; width: 100%; }
    </style>
</head>
<body>
    <div id="map"></div>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <script>
    try {
        console.log('START');

        var map = L.map('map').setView([$lat, $lng], 15);
        console.log('Map created');

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OSM'
        }).addTo(map);
        console.log('Tiles added');

        // 현재 위치
        ${if (currentLocation != null) {
            "L.marker([$lat, $lng]).addTo(map).bindPopup('<b>내 위치</b>');"
        } else ""}

        // 미용실 마커들
        $markers

        console.log('DONE - ${salons.size} salons');
    } catch(e) {
        console.error('ERROR:', e.message);
    }
    </script>
</body>
</html>
    """.trimIndent()
}

class WebAppInterface {
    @JavascriptInterface
    fun log(message: String) {
        Log.d("WebView-Interface", message)
    }
}
