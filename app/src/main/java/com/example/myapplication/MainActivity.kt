package com.example.myapplication

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
// import androidx.hilt.navigation.compose.hiltViewModel  // Hilt 임시 비활성화
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.PhotoSelectionScreen
import com.example.myapplication.PhotoConfirmScreen
import com.example.myapplication.ResultScreen
import com.example.myapplication.viewmodel.AnalysisUiState
import com.example.myapplication.viewmodel.AnalysisViewModel
import com.example.myapplication.viewmodel.HairColorUiState
import com.example.myapplication.viewmodel.HairColorSynthesisUiState
import com.example.myapplication.viewmodel.SynthesisUiState
import com.example.myapplication.viewmodel.SynthesisUsageState
import com.example.myapplication.util.AnalyticsHelper
import com.example.myapplication.util.KeyHashUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
// import dagger.hilt.android.AndroidEntryPoint  // Hilt 임시 비활성화
import java.io.File

// ✅ TAG를 최상단에 상수로 정의
private const val TAG = "MainActivity"

/**
 * MainActivity
 * 
 * 임시로 Hilt 비활성화 - 빌드 문제 해결 후 재활성화 예정
 */
// @Andr
// oidEntryPoint  // Hilt 임시 비활성화
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 카카오 API 키 해시 출력 (Logcat에서 확인)
        KeyHashUtil.getKeyHash(this)

        setContent {
            MyApplicationTheme {
                HairMeApp()
            }
        }
    }
}

@Composable
fun HairMeApp(
    // viewModel: AnalysisViewModel = hiltViewModel()  // Hilt 임시 비활성화
    viewModel: AnalysisViewModel = run {
        val context = LocalContext.current
        // ✅ Application 타입을 가져오기
        val application = context.applicationContext as Application

        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnalysisViewModel(application) as T
                }
            }
        )
    }
) {
    val navController = rememberNavController()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedGender by remember { mutableStateOf("male") } // 성별 상태 추가

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            LaunchedEffect(Unit) {
                AnalyticsHelper.logScreenView("HomeScreen")
            }
            HomeScreen(
                onStartClick = {
                    AnalyticsHelper.logStartAnalysis()
                    viewModel.resetState() // 분석 상태 초기화
                    navController.navigate("photo_selection")
                },
                onFindSalonClick = {
                    AnalyticsHelper.logFindSalonFromHome()
                    navController.navigate("salon_list/주변 미용실")
                }
            )
        }

        composable("photo_selection") {
            LaunchedEffect(Unit) {
                AnalyticsHelper.logScreenView("PhotoSelectionScreen")
            }
            PhotoSelectionScreenWrapper(
                navController = navController,
                onImageSelected = { uri ->
                    selectedImageUri = uri
                    navController.navigate("photo_confirm")
                }
            )
        }

        composable("photo_confirm") {
            LaunchedEffect(Unit) {
                AnalyticsHelper.logScreenView("PhotoConfirmScreen")
            }
            // ViewModel을 주입
            PhotoConfirmScreenWrapper(
                navController = navController,
                imageUri = selectedImageUri,
                selectedGender = selectedGender, // 성별 전달
                onGenderSelected = { gender -> selectedGender = gender }, // 성별 변경 콜백
                viewModel = viewModel // ViewModel 전달
            )
        }

        composable("result") {
            // ✅ ViewModel에서 직접 결과를 가져옴
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val synthesisState by viewModel.synthesisState.collectAsStateWithLifecycle()
            val hairColorState by viewModel.hairColorState.collectAsStateWithLifecycle()
            val hairColorSynthesisState by viewModel.hairColorSynthesisState.collectAsStateWithLifecycle() // ✅ v34
            val usageState by viewModel.usageState.collectAsStateWithLifecycle()
            val success = uiState as? AnalysisUiState.Success

            // 포그라운드 복귀 시 Usage 재조회
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.fetchUsage()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // ✅ v22: 디버깅 로그 (analysisId 제거)
            LaunchedEffect(success) {
                Log.d(TAG, "🎯 Result 화면 진입")
                Log.d(TAG, "   - uiState: $uiState")
                Log.d(TAG, "   - success: $success")
                Log.d(TAG, "   - selectedGender: $selectedGender")

                // Firebase Analytics: 결과 화면 조회 + 분석 완료 이벤트
                AnalyticsHelper.logScreenView("ResultScreen")
                success?.let {
                    AnalyticsHelper.logAnalysisComplete(
                        faceShape = it.data.face_shape,
                        skinTone = it.data.skin_tone,
                        elapsedMs = it.elapsedTimeMs
                    )
                    // ✅ 퍼널 분석용: 통합 분석 완료 이벤트
                    AnalyticsHelper.logFaceAnalysisComplete(
                        success = true,
                        faceShape = it.data.face_shape,
                        skinTone = it.data.skin_tone,
                        elapsedMs = it.elapsedTimeMs
                    )
                }

                // ✅ v33: 퍼스널컬러 기반 염색색 추천 자동 호출
                success?.let {
                    Log.d(TAG, "🎨 염색색 추천 요청: ${it.data.skin_tone}")
                    viewModel.fetchHairColorRecommendations(it.data.skin_tone)
                }
            }

            success?.let {
                ResultScreen(
                    faceShape = it.data.face_shape,
                    skinTone = it.data.skin_tone,
                    recommendedStyles = it.data.recommended_styles,
                    analysisId = it.analysisId, // ✅ v25: 피드백 제출용 analysis_id
                    elapsedTimeMs = it.elapsedTimeMs, // ✅ 분석 소요 시간
                    gender = selectedGender, // ✅ v28: 성별 전달 (헤어스타일 이름에 표시용)
                    hairColorState = hairColorState, // ✅ v33: 염색색 추천 상태 전달
                    usageState = usageState,
                    onBackClick = {
                        viewModel.resetState() // 홈으로 가기 전 상태 초기화
                        viewModel.resetSynthesisState() // 합성 상태도 초기화
                        viewModel.resetHairColorState() // 염색색 추천 상태도 초기화
                        viewModel.resetHairColorSynthesisState() // ✅ v34: 염색색 합성 상태도 초기화
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    onFindSalonClick = { styleName ->
                        // ✅ v21: 미용실 찾기 화면으로 이동
                        AnalyticsHelper.logFindSalonFromResult()
                        navController.navigate("salon_list/$styleName")
                    },
                    onSynthesizeClick = { styleName ->
                        // ✅ v30: 헤어스타일 합성 시작
                        AnalyticsHelper.logSynthesizeHairstyle(styleName)
                        Log.d(TAG, "🎨 헤어스타일 합성 요청: $styleName")
                        viewModel.synthesizeHairstyle(styleName)
                    },
                    onSynthesizeHairColor = { colorName, colorHex ->
                        // ✅ v34: 염색색 합성 시작
                        AnalyticsHelper.logSynthesizeHairColor(colorName)
                        Log.d(TAG, "🎨 염색색 합성 요청: $colorName ($colorHex)")
                        viewModel.synthesizeHairColor(colorName, colorHex)
                    }
                )

                // ✅ v30: 합성 상태에 따른 다이얼로그 표시
                when (val state = synthesisState) {
                    is SynthesisUiState.Loading -> {
                        SynthesisLoadingDialog(
                            hairstyleName = state.hairstyleName,
                            onDismiss = { viewModel.resetSynthesisState() }
                        )
                    }
                    is SynthesisUiState.Success -> {
                        SynthesisResultDialog(
                            synthesizedImage = state.synthesizedImage,
                            hairstyleName = state.hairstyleName,
                            onDismiss = { viewModel.resetSynthesisState() },
                            onTryAnother = { viewModel.resetSynthesisState() }
                        )
                    }
                    is SynthesisUiState.Error -> {
                        SynthesisErrorDialog(
                            errorMessage = state.message,
                            onDismiss = { viewModel.resetSynthesisState() },
                            onRetry = {
                                // 마지막 요청한 스타일로 다시 시도 - 간단히 상태만 초기화
                                viewModel.resetSynthesisState()
                            }
                        )
                    }
                    SynthesisUiState.Idle -> {
                        // 다이얼로그 표시 안함
                    }
                }

                // ✅ v34: 염색색 합성 상태에 따른 다이얼로그 표시
                when (val state = hairColorSynthesisState) {
                    is HairColorSynthesisUiState.Loading -> {
                        ColorSynthesisLoadingDialog(
                            colorName = state.colorName,
                            onDismiss = { viewModel.resetHairColorSynthesisState() }
                        )
                    }
                    is HairColorSynthesisUiState.Success -> {
                        ColorSynthesisResultDialog(
                            synthesizedImage = state.synthesizedImage,
                            colorName = state.colorName,
                            onDismiss = { viewModel.resetHairColorSynthesisState() },
                            onTryAnother = { viewModel.resetHairColorSynthesisState() }
                        )
                    }
                    is HairColorSynthesisUiState.Error -> {
                        SynthesisErrorDialog(
                            errorMessage = state.message,
                            onDismiss = { viewModel.resetHairColorSynthesisState() },
                            onRetry = {
                                viewModel.resetHairColorSynthesisState()
                            }
                        )
                    }
                    HairColorSynthesisUiState.Idle -> {
                        // 다이얼로그 표시 안함
                    }
                }
            } ?: run {
                // ✅ success가 null인 경우 처리
                Log.e(TAG, "❌ Result 화면에 도착했으나 Success 상태가 아님!")
            }
        }

        // ✅ v21: 미용실 리스트 화면
        composable("salon_list/{styleName}") { backStackEntry ->
            val styleName = backStackEntry.arguments?.getString("styleName") ?: ""
            LaunchedEffect(Unit) {
                AnalyticsHelper.logScreenView("SalonListScreen")
            }
            SalonListScreen(
                styleName = styleName,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// ... PhotoSelectionScreenWrapper는 변경 사항 없음 ...
@Composable
fun PhotoSelectionScreenWrapper(
    navController: NavHostController,
    onImageSelected: (Uri) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var shouldLaunchCamera by remember { mutableStateOf(false) }

    // ✅ 카메라 실행기 (먼저 정의)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            Log.d(TAG, "✅ 카메라 촬영 성공: $tempPhotoUri")
            onImageSelected(tempPhotoUri!!)
        } else {
            Log.e(TAG, "❌ 카메라 촬영 실패 또는 취소")
        }
        shouldLaunchCamera = false
    }

    // ✅ 카메라 권한 요청기
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "✅ 카메라 권한 승인됨")
            shouldLaunchCamera = true
        } else {
            Log.e(TAG, "❌ 카메라 권한 거부됨")
            Toast.makeText(context, context.getString(R.string.error_camera_permission), Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ 권한 승인 후 자동으로 카메라 실행
    LaunchedEffect(shouldLaunchCamera) {
        if (shouldLaunchCamera) {
            try {
                val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                tempPhotoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                Log.d(TAG, "🚀 카메라 실행: $tempPhotoUri")
                tempPhotoUri?.let { cameraLauncher.launch(it) }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 카메라 실행 실패: ${e.message}")
                Toast.makeText(context, "카메라 실행 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            shouldLaunchCamera = false
        }
    }

    // ✅ 갤러리 실행기
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "✅ 갤러리에서 이미지 선택: $uri")
            onImageSelected(uri)
        } else {
            Log.e(TAG, "❌ 갤러리 선택 취소 또는 실패")
        }
    }

    PhotoSelectionScreen(
        onBackClick = { navController.popBackStack() },
        onCameraClick = {
            AnalyticsHelper.logSelectCamera()
            AnalyticsHelper.logPhotoInput("camera")
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) -> {
                    // ✅ 권한이 이미 있으면 바로 카메라 실행
                    try {
                        val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        tempPhotoUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        Log.d(TAG, "🚀 카메라 실행 (권한 있음): $tempPhotoUri")
                        tempPhotoUri?.let { cameraLauncher.launch(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 카메라 실행 실패: ${e.message}")
                        Toast.makeText(context, "카메라 실행 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    // ✅ 권한 요청
                    Log.d(TAG, "📋 카메라 권한 요청")
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        },
        onGalleryClick = {
            AnalyticsHelper.logSelectGallery()
            AnalyticsHelper.logPhotoInput("gallery")
            Log.d(TAG, "🖼️ 갤러리 실행")
            galleryLauncher.launch("image/*")
        }
    )
}


// --- 여기가 핵심 리팩토링 ---
@Composable
fun PhotoConfirmScreenWrapper(
    navController: NavHostController,
    imageUri: Uri?,
    selectedGender: String, // 성별 파라미터 추가
    onGenderSelected: (String) -> Unit, // 성별 변경 콜백 추가
    viewModel: AnalysisViewModel // ViewModel을 파라미터로 받음
) {
    val context = LocalContext.current

    // 1. ViewModel의 UI 상태를 구독 (Lifecycle-aware)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 2. UI 상태에 따라 반응 (화면 이동, 토스트 메시지)
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AnalysisUiState.Success -> {
                // 성공 시: 결과 화면으로 이동
                Log.d(TAG, "✅ 분석 성공! 결과 화면으로 이동")
                navController.navigate("result") {
                    popUpTo("home")
                }
            }
            is AnalysisUiState.Error -> {
                // 실패 시: 토스트 띄우고 이전 화면으로
                AnalyticsHelper.logAnalysisError(state.message)
                // ✅ 퍼널 분석용: 통합 분석 실패 이벤트
                AnalyticsHelper.logFaceAnalysisComplete(success = false)
                Toast.makeText(context, context.getString(R.string.error_analysis_failed, state.message), Toast.LENGTH_LONG).show()
                viewModel.resetState() // 상태 초기화
                navController.popBackStack()
            }
            else -> {
                // Idle, Loading 상태. (아무것도 안함)
            }
        }
    }

    PhotoConfirmScreen(
        imageUri = imageUri,
        // 3. ViewModel의 상태를 UI에 바인딩
        isLoading = (uiState is AnalysisUiState.Loading),
        selectedGender = selectedGender, // 성별 전달
        onGenderSelected = onGenderSelected, // 성별 변경 콜백 전달
        onBackClick = { navController.popBackStack() },
        onRetryClick = { navController.popBackStack() },
        onAnalyzeClick = {
            if (imageUri == null) {
                Toast.makeText(context, context.getString(R.string.error_image_not_found), Toast.LENGTH_SHORT).show()
                return@PhotoConfirmScreen
            }
            // 4. 분석 시작: ViewModel에 위임 (성별 전달)
            AnalyticsHelper.logAnalyzeClick(selectedGender)
            Log.d(TAG, "🚀 분석 시작 요청 (gender=$selectedGender)")
            viewModel.analyzeImage(imageUri, selectedGender) // 성별 파라미터 추가
        }
    )
}