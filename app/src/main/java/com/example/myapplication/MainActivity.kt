package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import com.example.myapplication.util.RewardedAdManager
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.PhotoSelectionScreen
import com.example.myapplication.PhotoConfirmScreen
import com.example.myapplication.ResultScreen
import com.example.myapplication.viewmodel.AnalysisUiState
import com.example.myapplication.viewmodel.AnalysisViewModel
import com.example.myapplication.viewmodel.AuthUiState
import com.example.myapplication.viewmodel.AuthViewModel
import com.example.myapplication.viewmodel.HairColorUiState
import com.example.myapplication.viewmodel.MyResultsViewModel
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
 * 제휴 링크를 외부 브라우저(ACTION_VIEW)로 연다.
 *
 * 인앱 웹뷰를 쓰지 않는 이유: 쿠팡 앱 연동/전환율. 쿠팡 앱이 설치돼 있으면
 * 시스템이 딥링크로 앱을 열어준다. 실패 시 조용히 토스트 (합성 결과 화면 유지).
 */
private fun openInExternalBrowser(context: android.content.Context, url: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "❌ 외부 브라우저 열기 실패: ${e.message}")
        Toast.makeText(context, "브라우저를 열 수 없어요", Toast.LENGTH_SHORT).show()
    }
}

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

    // ✅ 카카오 로그인 ViewModel (Hilt 임시 비활성화로 수동 생성)
    val activityContext = LocalContext.current
    val authViewModel: AuthViewModel = run {
        val application = activityContext.applicationContext as Application
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(application) as T
                }
            }
        )
    }

    // ✅ 로그인 에러 메시지 Toast 표시 (일회성)
    val authError by authViewModel.errorMessage.collectAsStateWithLifecycle()
    LaunchedEffect(authError) {
        authError?.let {
            Toast.makeText(activityContext, it, Toast.LENGTH_LONG).show()
            authViewModel.consumeError()
        }
    }

    // ✅ 광고 보상 결과 안내 (일회성)
    val rewardMessage by authViewModel.rewardMessage.collectAsStateWithLifecycle()
    LaunchedEffect(rewardMessage) {
        rewardMessage?.let {
            Toast.makeText(activityContext, it, Toast.LENGTH_LONG).show()
            authViewModel.consumeRewardMessage()
        }
    }

    // ✅ 신규 가입 환영 다이얼로그
    val welcomeUser by authViewModel.welcomeUser.collectAsStateWithLifecycle()
    welcomeUser?.let { user ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { authViewModel.consumeWelcome() },
            title = { androidx.compose.material3.Text("환영합니다 🎉") },
            text = {
                androidx.compose.material3.Text(
                    "${user.nickname ?: "회원"}님, HairMe 가입을 환영해요!\n" +
                        "신규 가입 보너스 크레딧이 지급되었어요. (현재 크레딧 ${user.credits}개)"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { authViewModel.consumeWelcome() }) {
                    androidx.compose.material3.Text("확인")
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            val usageState by viewModel.usageState.collectAsStateWithLifecycle()
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val isWaitingReward by authViewModel.isWaitingReward.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                AnalyticsHelper.logScreenView("HomeScreen")
                viewModel.fetchUsage() // 퀵 엔트리 "오늘 무료 N회 남음" 표시용
                RewardedAdManager.load(activityContext) // 버튼을 눌렀을 때 바로 뜨도록 미리 받아둠
            }
            HomeScreen(
                usageRemaining = usageState.remaining,
                onStartClick = {
                    AnalyticsHelper.logStartAnalysis()
                    viewModel.resetState() // 분석 상태 초기화
                    navController.navigate("photo_selection")
                },
                onFindSalonClick = {
                    AnalyticsHelper.logFindSalonFromHome()
                    navController.navigate("salon_list/주변 미용실")
                },
                authUiState = authUiState,
                onKakaoLoginClick = {
                    // 카카오톡 앱 전환에 Activity Context 필요
                    authViewModel.loginWithKakao(activityContext)
                },
                onLogoutClick = {
                    authViewModel.logout()
                },
                onMyResultsClick = {
                    navController.navigate("my_results")
                },
                onWatchAdClick = {
                    val userId = (authUiState as? AuthUiState.LoggedIn)?.user?.userId
                    val activity = activityContext.findActivity()
                    when {
                        // user_id가 없으면 서버가 지급 대상을 못 정하므로 광고를 띄우지 않는다
                        userId.isNullOrBlank() || activity == null ->
                            Toast.makeText(
                                activityContext,
                                "로그인 후 이용할 수 있어요",
                                Toast.LENGTH_SHORT
                            ).show()
                        else -> RewardedAdManager.show(
                            activity = activity,
                            userId = userId,
                            onRewarded = { authViewModel.refreshCreditsAfterReward() },
                            onFailed = { message ->
                                Toast.makeText(activityContext, message, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                isWaitingReward = isWaitingReward
            )
        }

        // ✅ 내가 만든 스타일 (회원 합성 결과 히스토리)
        composable("my_results") {
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
            // 백스택 엔트리 스코프 → 재진입 시 새 인스턴스가 만들어져 presigned URL을 항상 새로 받음
            val myResultsViewModel: MyResultsViewModel = viewModel()
            val myResultsState by myResultsViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                AnalyticsHelper.logScreenView("MyResultsScreen")
            }
            // 진입 시 + (화면 내 로그인 유도로) 로그인 완료 시 목록 로드
            LaunchedEffect(authUiState) {
                if (authUiState is AuthUiState.LoggedIn) {
                    myResultsViewModel.refresh()
                }
            }

            MyResultsScreen(
                uiState = myResultsState,
                authUiState = authUiState,
                onBackClick = { navController.popBackStack() },
                onRetryClick = { myResultsViewModel.refresh() },
                onLoadMore = { myResultsViewModel.loadMore() },
                onRequestLogin = { authViewModel.loginWithKakao(activityContext) },
                onGoSynthesizeClick = {
                    viewModel.resetState()
                    navController.navigate("photo_selection")
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
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle() // 제휴 제품 클릭 로그인 분기용
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
                            beforeImageUri = selectedImageUri, // Before/After 그리드용 원본 사진
                            usageRemaining = usageState.remaining,
                            onDismiss = { viewModel.resetSynthesisState() },
                            onTryAnother = { viewModel.resetSynthesisState() },
                            // 제휴 제품 추천
                            recommendedProducts = state.recommendedProducts,
                            disclosure = state.disclosure,
                            isLoggedIn = authUiState is AuthUiState.LoggedIn,
                            onProductClick = { product ->
                                AnalyticsHelper.logProductClick(product.productId, state.hairstyleName)
                                viewModel.openProductLink(
                                    product = product,
                                    style = state.hairstyleName,
                                    source = "synthesis_result",
                                    onLink = { url -> openInExternalBrowser(activityContext, url) },
                                    onFailure = {
                                        Toast.makeText(
                                            activityContext,
                                            "제품 페이지를 열지 못했어요. 잠시 후 다시 시도해주세요",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            onRequestLogin = { authViewModel.loginWithKakao(activityContext) }
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
                            beforeImageUri = selectedImageUri, // Before/After 그리드용 원본 사진
                            usageRemaining = usageState.remaining,
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

    // ✅ 갤러리 실행기 (시스템 Photo Picker — 저장소 권한 불필요)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
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
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
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
/**
 * Compose의 LocalContext는 ContextWrapper로 감싸여 있을 수 있어
 * 전면 광고 표시에 필요한 Activity를 풀어서 꺼낸다.
 */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
