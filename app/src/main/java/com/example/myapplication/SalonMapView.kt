package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.network.Place
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle

/**
 * SalonMapView - 카카오맵을 사용한 미용실 지도 표시
 *
 * 기능:
 * - 현재 위치 표시
 * - 미용실 위치 마커 표시
 * - 마커 클릭 시 정보 표시
 * - 거리 정보 표시
 */

@Composable
fun SalonMapView(
    salons: List<Place>,
    currentLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                start(object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {
                        // 지도 파괴 시
                    }

                    override fun onMapError(error: Exception?) {
                        // 에러 발생 시
                        android.util.Log.e("SalonMapView", "카카오맵 에러: ${error?.message}")
                    }
                }, object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        kakaoMap = map
                        android.util.Log.d("SalonMapView", "✅ 카카오맵 로딩 완료")

                        // 현재 위치로 카메라 이동
                        currentLocation?.let { location ->
                            val cameraUpdate = CameraUpdateFactory.newCenterPosition(
                                LatLng.from(location.first, location.second),
                                15
                            )
                            map.moveCamera(cameraUpdate)

                            // 현재 위치 마커 추가 (파란색)
                            val myLocationStyle = LabelStyle.from(android.R.drawable.ic_menu_mylocation).apply {
                                setTextStyles(LabelTextStyle.from(18, Color.BLUE))
                            }
                            val myLocationLabel = map.labelManager?.layer?.addLabel(
                                LabelOptions.from(LatLng.from(location.first, location.second))
                                    .setStyles(LabelStyles.from(myLocationStyle))
                                    .setTexts(LabelTextBuilder().setTexts("📍 내 위치"))
                            )
                        } ?: run {
                            // 현재 위치가 없으면 서울 기본 위치
                            val cameraUpdate = CameraUpdateFactory.newCenterPosition(
                                LatLng.from(37.5665, 126.9780),
                                15
                            )
                            map.moveCamera(cameraUpdate)
                        }

                        // 빨간색 마커 비트맵 생성
                        val redMarkerBitmap = createRedMarkerBitmap()

                        // 미용실 마커들 추가 (빨간색 마커)
                        salons.forEach { salon ->
                            val position = LatLng.from(salon.y.toDouble(), salon.x.toDouble())

                            // 빨간색 텍스트 스타일 (크기 20)
                            val redTextStyle = LabelTextStyle.from(20, Color.RED)

                            // 빨간색 비트맵 마커 스타일
                            val salonStyle = LabelStyle.from(redMarkerBitmap).apply {
                                setTextStyles(redTextStyle)
                            }

                            map.labelManager?.layer?.addLabel(
                                LabelOptions.from(position)
                                    .setStyles(LabelStyles.from(salonStyle))
                                    .setTexts(LabelTextBuilder().setTexts("✂️ ${salon.place_name}"))
                            )

                            android.util.Log.d("SalonMapView", "✂️ 빨간 마커 추가: ${salon.place_name}")
                        }

                        android.util.Log.d("SalonMapView", "✅ ${salons.size}개 미용실 마커 추가 완료")
                    }

                    override fun getPosition(): LatLng {
                        // 초기 카메라 위치
                        return currentLocation?.let {
                            LatLng.from(it.first, it.second)
                        } ?: LatLng.from(37.5665, 126.9780)
                    }
                })
            }
        },
        update = { mapView ->
            // 필요시 업데이트 로직 추가
        }
    )
}

/**
 * 빨간색 원형 마커 비트맵 생성
 */
private fun createRedMarkerBitmap(): Bitmap {
    val size = 60 // 마커 크기
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 빨간색 원 그리기
    val paint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // 흰색 테두리
    val borderPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, borderPaint)

    return bitmap
}
