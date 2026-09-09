package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.network.KakaoLocalSearchResponse
import com.example.myapplication.network.Place
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.ui.theme.Atelier
import com.example.myapplication.ui.theme.AtelierTopBar
import com.example.myapplication.ui.theme.atelierSerif
import com.example.myapplication.util.AnalyticsHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

/**
 * SalonListScreen - 주변 미용실 검색 및 표시 (Atelier 리디자인)
 *
 * 기능(기존 로직 유지):
 * 1. GPS로 현재 위치 수집
 * 2. 카카오 로컬 API로 주변 미용실 검색
 * 3. 거리순으로 정렬된 리스트 표시
 * 4. 클릭 시 카카오맵 앱으로 이동
 *
 * UI: 상단 260dp 고정 지도(카카오맵 WebView) + my_location FAB + 헤어라인 구분 리스트
 */

@Composable
fun SalonListScreen(
    styleName: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 상태 관리
    var salons by remember { mutableStateOf<List<Place>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // 위치 재조회 + 재검색 (진입 시/FAB 클릭 시 공용)
    val refreshSearch: () -> Unit = {
        isLoading = true
        errorMessage = null
        fetchCurrentLocation(context) { lat, lng ->
            currentLocation = Pair(lat, lng)
            scope.launch {
                searchSalons(
                    styleName = styleName,
                    latitude = lat,
                    longitude = lng,
                    onResult = { result ->
                        salons = result
                        isLoading = false
                    },
                    onError = { error ->
                        errorMessage = error
                        isLoading = false
                    }
                )
            }
        }
    }

    // GPS 권한 요청
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            refreshSearch()
        } else {
            Toast.makeText(context, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 화면 진입 시 GPS 권한 확인 및 위치 요청
    LaunchedEffect(Unit) {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED
        ) {
            refreshSearch()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Atelier.Background)
            .statusBarsPadding()
    ) {
        AtelierTopBar(
            title = "주변 미용실",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationContentDescription = "뒤로가기",
            onNavigationClick = onBackClick,
            actions = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = refreshSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "다시 검색",
                        tint = Atelier.Ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )

        when {
            isLoading -> LoadingIndicator()
            errorMessage != null -> ErrorMessage(errorMessage!!)
            salons.isEmpty() -> EmptyMessage()
            else -> {
                // 지도 영역: 상단 260dp 고정 (카카오맵 WebView 유지)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    SalonMapView(
                        salons = salons,
                        currentLocation = currentLocation,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 우하단 흰 원형 my_location FAB
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(44.dp)
                            .background(Atelier.Surface, CircleShape)
                            .border(1.dp, Atelier.Border, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = refreshSearch),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "내 위치에서 다시 검색",
                            tint = Atelier.Ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                SalonList(salons = salons)
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Atelier.BrandViolet,
                trackColor = Atelier.Divider
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "주변 미용실 검색 중...",
                fontSize = 14.sp,
                color = Atelier.TextSecondary
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Atelier.TrendAccent
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Atelier.TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Atelier.Chevron
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "주변에 미용실이 없습니다",
                fontSize = 14.sp,
                color = Atelier.TextSecondary
            )
        }
    }
}

@Composable
private fun SalonList(salons: List<Place>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // 리스트 헤더: Serif "내 주변 N곳" + "거리순"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "내 주변 ${salons.size}곳",
                    style = atelierSerif(size = 17)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "거리순",
                    fontSize = 12.sp,
                    color = Atelier.TextTertiary
                )
            }
        }

        items(salons) { salon ->
            SalonRow(salon = salon, showTopBorder = true)
        }
    }
}

@Composable
private fun SalonRow(salon: Place, showTopBorder: Boolean) {
    val context = LocalContext.current

    Column {
        if (showTopBorder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Atelier.Border)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // 카카오맵 앱 또는 웹 열기 (기존 로직 유지)
                    AnalyticsHelper.logSalonClick(salon.place_name)
                    openKakaoMap(context, salon.place_url)
                }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 56dp 썸네일 자리 (radius 2dp)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Atelier.Divider, Atelier.ThumbShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = Atelier.Chevron,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = salon.place_name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Atelier.Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildList {
                        if (salon.distance.isNotEmpty()) add("${salon.distance}m")
                        add(salon.road_address_name.ifEmpty { salon.address_name })
                        if (salon.phone.isNotEmpty()) add(salon.phone)
                    }.joinToString(" · "),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Atelier.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Atelier.Chevron,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * GPS 현재 위치 가져오기
 */
private fun fetchCurrentLocation(
    context: android.content.Context,
    onSuccess: (latitude: Double, longitude: Double) -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onSuccess(location.latitude, location.longitude)
                } else {
                    Toast.makeText(context, "현재 위치를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "위치 정보 오류: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    } catch (e: SecurityException) {
        Toast.makeText(context, "위치 권한이 없습니다", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 카카오 로컬 API로 미용실 검색
 */
private suspend fun searchSalons(
    styleName: String,
    latitude: Double,
    longitude: Double,
    onResult: (List<Place>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        android.util.Log.d("SalonSearch", "========================================")
        android.util.Log.d("SalonSearch", "검색어: $styleName")
        android.util.Log.d("SalonSearch", "위치: ($latitude, $longitude)")
        android.util.Log.d("SalonSearch", "========================================")

        // ✅ styleName 파라미터 활용
        // "주변 미용실"이거나 비어있으면 모든 미용실 검색
        // 특정 스타일 이름이 있으면 해당 스타일을 포함한 미용실 검색
        val searchQuery = if (styleName.isEmpty() || styleName == "주변 미용실") {
            "미용실"
        } else {
            "$styleName 미용실"
        }

        android.util.Log.d("SalonSearch", "최종 검색 쿼리: $searchQuery")

        // Authorization 헤더는 RetrofitClient의 인터셉터에서 자동으로 추가됩니다.
        val response: KakaoLocalSearchResponse = RetrofitClient.kakaoLocalApiService.searchByKeyword(
            query = searchQuery,
            x = longitude,
            y = latitude,
            radius = 2000, // 2km 반경 (네이버 기본 1km보다 여유있게)
            size = 15,
            sort = "distance" // 거리순 정렬
        )

        if (response.documents.isNotEmpty()) {
            onResult(response.documents)
        } else {
            onError("주변에 미용실이 없습니다")
        }
    } catch (e: retrofit2.HttpException) {
        // HTTP 에러 상세 정보
        val errorBody = e.response()?.errorBody()?.string()
        android.util.Log.e("SalonSearch", "HTTP ${e.code()}: $errorBody")

        when (e.code()) {
            403 -> {
                onError("카카오 API 인증 실패 (HTTP 403)\n\n" +
                        "카카오 개발자 콘솔에서 확인하세요:\n" +
                        "1. 플랫폼 > Android 플랫폼 등록 확인\n" +
                        "2. 패키지명: com.example.myapplication\n" +
                        "3. 키 해시 등록 확인\n" +
                        "4. 카카오 로컬 API 활성화 확인")
            }
            401 -> {
                onError("API 키가 유효하지 않습니다 (HTTP 401)\n\nAPI 키를 다시 확인해주세요.")
            }
            else -> {
                onError("HTTP 오류 ${e.code()}\n${errorBody ?: e.message}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("SalonSearch", "검색 오류", e)
        onError("검색 오류: ${e.message}")
    }
}

/**
 * 카카오맵 앱/웹 열기
 */
private fun openKakaoMap(context: android.content.Context, placeUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(placeUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "카카오맵을 열 수 없습니다", Toast.LENGTH_SHORT).show()
    }
}
