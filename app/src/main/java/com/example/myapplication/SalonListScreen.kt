package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.network.KakaoLocalSearchResponse
import com.example.myapplication.network.Place
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.util.AnalyticsHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

/**
 * SalonListScreen - 주변 미용실 검색 및 표시
 *
 * 기능:
 * 1. GPS로 현재 위치 수집
 * 2. 카카오 로컬 API로 주변 미용실 검색
 * 3. 거리순으로 정렬된 리스트 표시
 * 4. 클릭 시 카카오맵 앱으로 이동
 */

// 색상 상수
private object SalonColors {
    val Background = Color.White
    val Primary = Color(0xFF5B4FFF)
    val CardBackground = Color(0xFFF8F7FF)
    val KakaoYellow = Color(0xFFFEE500)
    val TextPrimary = Color.Black
    val TextSecondary = Color(0xFF666666)
    val TextTertiary = Color(0xFF999999)
}


@OptIn(ExperimentalMaterial3Api::class)
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
    var isMapView by remember { mutableStateOf(false) } // 지도 보기 상태

    // GPS 권한 요청
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // 권한 승인 → 위치 가져오기
            scope.launch {
                fetchCurrentLocation(context) { lat, lng ->
                    currentLocation = Pair(lat, lng)
                    // 위치를 받으면 자동으로 미용실 검색
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
            // 이미 권한이 있음 → 바로 위치 가져오기
            isLoading = true
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
        } else {
            // 권한 요청
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "주변 미용실",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (styleName.isNotEmpty() && styleName != "주변 미용실") {
                            Text(
                                text = "$styleName 전문",
                                fontSize = 12.sp,
                                color = SalonColors.TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로가기")
                    }
                },
                actions = {
                    // 지도/리스트 전환 버튼
                    if (salons.isNotEmpty()) {
                        IconButton(onClick = { isMapView = !isMapView }) {
                            Icon(
                                imageVector = if (isMapView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.Map,
                                contentDescription = if (isMapView) "리스트 보기" else "지도 보기",
                                tint = SalonColors.Primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SalonColors.Background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SalonColors.Background)
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    LoadingIndicator()
                }
                errorMessage != null -> {
                    ErrorMessage(errorMessage!!)
                }
                salons.isEmpty() -> {
                    EmptyMessage()
                }
                else -> {
                    // 지도 보기 / 리스트 보기 전환
                    if (isMapView) {
                        // 카카오맵 사용
                        SalonMapView(
                            salons = salons,
                            currentLocation = currentLocation
                        )
                    } else {
                        SalonList(salons = salons)
                    }
                }
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
            CircularProgressIndicator(color = SalonColors.Primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "주변 미용실 검색 중...",
                color = SalonColors.TextSecondary
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
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Red
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = SalonColors.TextSecondary,
                fontSize = 16.sp
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
                modifier = Modifier.size(64.dp),
                tint = SalonColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "주변에 미용실이 없습니다",
                color = SalonColors.TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun SalonList(salons: List<Place>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "총 ${salons.size}개의 미용실",
                fontSize = 14.sp,
                color = SalonColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(salons) { salon ->
            SalonCard(salon = salon)
        }
    }
}

@Composable
private fun SalonCard(salon: Place) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 카카오맵 앱 또는 웹 열기
                AnalyticsHelper.logSalonClick(salon.place_name)
                openKakaoMap(context, salon.place_url)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SalonColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 미용실 이름
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = salon.place_name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SalonColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (salon.distance.isNotEmpty()) {
                    Text(
                        text = "${salon.distance}m",
                        fontSize = 14.sp,
                        color = SalonColors.Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 주소
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SalonColors.TextTertiary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = salon.road_address_name.ifEmpty { salon.address_name },
                    fontSize = 14.sp,
                    color = SalonColors.TextSecondary,
                    lineHeight = 20.sp
                )
            }

            // 전화번호
            if (salon.phone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SalonColors.TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = salon.phone,
                        fontSize = 14.sp,
                        color = SalonColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 카카오맵에서 보기 버튼
            Button(
                onClick = {
                    openKakaoMap(context, salon.place_url)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SalonColors.KakaoYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "카카오맵에서 보기",
                    fontWeight = FontWeight.Medium
                )
            }
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
