# Handoff: HairMe "Atelier" UI 리디자인

## Overview
HairMe(AI 헤어스타일 추천 Android 앱, Jetpack Compose + Material3)의 전체 UI 리디자인.
기존의 기본 Material 스타일(#5B4FFF 퍼플 + 이모지 아이콘)을 **"Atelier" — 에디토리얼 아이보리** 방향으로 교체한다:
따뜻한 종이색 배경, 세리프 헤드라인, 얇은 헤어라인 보더, 블랙 필(pill) CTA, 퍼플은 포인트 컬러로만 사용.

대상 코드베이스: `woongscoding/hairme_frontend` (Kotlin, Jetpack Compose, Material3, 단일 모듈 `app`).

## About the Design Files
이 번들의 `HairMe Redesign.dc.html`은 **HTML로 제작된 디자인 레퍼런스**다 — 프로덕션 코드가 아니다.
할 일은 이 HTML을 복사하는 것이 아니라, **기존 Compose 코드베이스의 패턴을 유지하면서 이 디자인을 Compose로 재구현**하는 것이다.
기존 화면 파일(`HomeScreen.kt`, `PhotoSelectionScreen.kt`, `PhotoConfirmScreen.kt`, `ResultScreen.kt`, `SynthesisResultScreen.kt`, `SalonListScreen.kt`)의 콜백 시그니처·ViewModel 연결·문자열 리소스 구조는 그대로 두고 **UI 레이어만** 교체한다.

파일 안에는 두 개의 섹션이 있다. **위쪽 섹션(배지 2a, "Atelier 풀 플로우")이 구현 대상**이다.
아래 섹션(1a/1b/1c)은 초기 방향 탐색용 비교 시안이므로 무시한다.

## Fidelity
**High-fidelity.** 색상·타이포·간격·라운드 값은 최종 확정값이다. 픽셀 단위로 재현할 것.
단, 사선 스트라이프로 표시된 영역은 전부 **이미지 플레이스홀더**다(실제 사진/지도가 들어갈 자리). 스트라이프 패턴 자체를 구현하지 말 것.

## Design Tokens

### Colors
| Token | Hex | 용도 |
|---|---|---|
| `AtelierBackground` | `#FAF7F2` | 모든 화면 배경 (따뜻한 아이보리) |
| `AtelierSurface` | `#FFFFFF` | 카드 배경 |
| `AtelierBorder` | `#E2DCCF` | 카드 보더, 구분선 (두께 1dp) |
| `AtelierDivider` | `#EEE9DE` | 카드 내부 구분선 |
| `AtelierInk` | `#1C1826` | 기본 텍스트, 블랙 CTA 배경 |
| `AtelierTextSecondary` | `#6B665C` | 본문 보조 텍스트 |
| `AtelierTextTertiary` | `#8A8578` | 라벨, 메타 텍스트 |
| `AtelierChevron` | `#C9C2B2` | chevron 등 비활성 아이콘 |
| `BrandViolet` | `#5B4FFF` | 포인트 (기존 브랜드 유지 — 아이콘, 매칭 %, 프로그레스) |
| `BrandVioletDeep` | `#4438D6` | 결과 값 텍스트 (얼굴형/퍼스널컬러) |
| `BrandVioletSoft` | `#9B94FF` | 오버라인 라벨, 보조 포인트 |
| `TrendAccent` | `#C2410C` | TREND 배지 텍스트 (보더 `#E8C9B4`) |

퍼플을 배경 전체에 칠하지 않는 것이 이 디자인의 핵심. 퍼플은 항상 작은 포인트로만.

### Typography
- **디스플레이/제목**: Noto Serif KR (SemiBold 600). 앱에 폰트 추가 필요 — Google Fonts에서 다운로드 후 `res/font/`에 추가하거나 Compose `GoogleFont` provider 사용.
- **본문/UI**: Pretendard (또는 시스템 기본). Pretendard 권장: https://github.com/orioncactus/pretendard
- 스케일:
  - 홈 히어로 제목: Serif 30sp / line-height 1.35
  - 화면 제목(앱바): Serif 17sp SemiBold
  - 섹션 제목: Serif 17sp SemiBold
  - 카드 제목: Serif 16–22sp SemiBold
  - 본문: 13–14sp, line-height 1.55–1.6
  - 메타/라벨: 11–12sp, 라벨은 letter-spacing 0.14em + 대문자
  - 워드마크 "HAIRME": Serif 19sp, letter-spacing 0.22em

### Shape & Elevation
- 카드: **radius 4dp** (거의 직각 — 기존 20dp 라운드를 버림), 보더 1dp `#E2DCCF`, **elevation 0** (그림자 없음)
- 버튼/CTA: **완전한 pill (radius 999)**
- 썸네일: radius 2dp
- 컬러 스와치: 원형, 흰 보더 3dp + 바깥 1dp `#E2DCCF` 링

### Buttons
- **Primary**: 배경 `#1C1826`, 흰 텍스트 14–15sp SemiBold, pill, 높이 48–56dp
- **Secondary**: 투명 배경 + 보더 1dp `#1C1826`, 텍스트 `#1C1826`, pill
- **Tertiary(텍스트)**: `#8A8578`, underline (offset 4dp)
- 아이콘 버튼(좋아요/싫어요): 원형, 보더 1dp `#E2DCCF`, 아이콘 20dp `#8A8578`

### Icons
이모지(✂️📷💡✨ 등)를 **전부 Material Symbols Rounded 아이콘으로 교체**.
Compose에서는 `androidx.compose.material.icons` 또는 Material Symbols 폰트 사용.
사용 아이콘: `location_on`, `history`, `face_retouching_natural`, `auto_awesome`, `photo_camera`, `imagesmode`(갤러리), `lightbulb`, `arrow_back`, `arrow_forward`, `chevron_right`, `thumb_up`, `thumb_down`, `close`, `download`, `share`, `search`, `my_location`, `block`.
아이콘 크기: 앱바 22dp, 카드 내 22–26dp, 인라인 16–18dp.

## Screens / Views

### 01 · 홈 (HomeScreen.kt) — 대시보드형으로 개편
기존의 "로고+시작 버튼" 단일 랜딩을 대시보드로 교체.
- **상단 바**: 좌측 워드마크 "HAIRME"(Serif, letter-spacing 0.22em), 우측 `location_on` + `history` 아이콘 (24dp 패딩, gap 14dp)
- **히어로 텍스트**: Serif 30sp "오늘의 나에게\n어울리는 스타일" + 보조문구 14sp `#6B665C` "얼굴형과 퍼스널컬러를 읽어\n가장 잘 어울리는 헤어를 제안합니다"
- **메인 CTA 카드** (흰 카드, 패딩 24dp): 오버라인 "AI CONSULTATION"(11sp, `#9B94FF`, letter-spacing 0.16em) + Serif 22sp "얼굴형 분석" + 우측 `face_retouching_natural` 아이콘(26dp, `#5B4FFF`). 구분선 아래: "약 5초 · 사진 한 장이면 충분해요"(13sp) + 블랙 pill 버튼 "분석 시작 →"
- **퀵 엔트리 2열 그리드** (gap 14dp): ① 주변 미용실(`location_on`) ② 가상 스타일링(`auto_awesome`, "오늘 무료 3회 남음" — `usageState.remaining` 연동). 각각 흰 카드, 아이콘 22dp 퍼플, 제목 14sp SemiBold, 설명 12sp
- **최근 분석 섹션**: Serif 17sp 제목 + "전체 보기" 12sp. 리스트 행: 상하 1dp 보더, 44dp 썸네일, "계란형 · 여름 쿨톤"(14sp SemiBold) + 날짜·건수(12sp), chevron. Room 히스토리(`AnalysisHistoryEntity`) 연동

### 02 · 사진 선택 (PhotoSelectionScreen.kt)
- 앱바: back + Serif "사진 선택"
- 헤더: Serif 26sp "얼굴 사진을\n준비해 주세요" + 보조문구
- 선택 카드 2개 (세로, gap 14dp): 원형 아이콘 웰(52dp, 보더 1dp, 아이콘 24dp 퍼플) + Serif 17sp 제목 + 13sp 설명 + chevron. 카메라 / 갤러리
- 하단 고정 팁: 상단 1dp 보더 위, `lightbulb` 아이콘(18dp, `#9B94FF`) + 13sp 안내문 (기존 노란 카드 → 제거)

### 03 · 사진 확인 (PhotoConfirmScreen.kt)
- 사진 프리뷰: 카드 radius 4dp, 높이 min 300 / max 500dp (기존 로직 유지), ContentScale.Fit
- **성별 선택 → 세그먼트 컨트롤**로 교체: pill 컨테이너(보더 1dp, 흰 배경, 패딩 4dp) 안에 2개 세그먼트. 선택 = 블랙 pill + 흰 텍스트, 비선택 = 투명 + `#8A8578`. 라벨: "남성 스타일" / "여성 스타일"
- 제목: Serif 16sp "어떤 스타일을 추천할까요?"
- 하단: 블랙 pill "이 사진으로 분석하기" (앞에 `auto_awesome` 18dp `#9B94FF`) + 언더라인 텍스트 버튼 "다시 선택하기". 로딩 시 기존 CircularProgressIndicator 로직 유지(흰색, pill 내부)

### 04 · 분석 결과 (ResultScreen.kt)
- 앱바: back + Serif "분석 리포트" + 우측 "4.5초 소요"(12sp, `elapsedTimeMs` 연동)
- **요약 카드**: 흰 카드를 세로 1dp 구분선으로 2분할. 각 셀: 라벨(11sp, letter-spacing 0.14em, `#8A8578`) + 값(Serif 21sp, `#4438D6`). 얼굴형 / 퍼스널컬러
- 섹션 헤더: Serif "추천 스타일" + 우측 "가상 체험 N회 남음"(12sp, usageState 연동)
- **1위 카드 (이미지 중심)**: 상단 190dp 스타일 예시 이미지(네이버 검색 대신 카드 내 이미지 — 이미지가 없으면 클릭 시 기존 네이버 이미지 검색 유지), 하단: Serif 19sp "1 · 투블럭컷" + "92% 매칭"(14sp Bold 퍼플), 3dp 프로그레스 바(트랙 `#EEE9DE`, 필 `#5B4FFF`), 추천 이유 13sp, 액션 행 = 보더 pill "가상으로 체험하기"(flex 1) + 원형 thumb_up/thumb_down 버튼(기존 피드백 API 연동 유지)
- **2위 이하**: 컴팩트 행 카드 — 64dp 썸네일 + Serif 16sp 제목 + 매칭 %(12sp Bold 퍼플) + chevron
- **트렌드 스타일**: 같은 컴팩트 행 + "TREND" 배지(10sp, `#C2410C`, 보더 `#E8C9B4`, pill). 🔥 이모지 제거. score 없으면 % 대신 설명 텍스트
- **염색 추천 섹션** (스크롤 하단): Serif "퍼스널컬러 염색 추천" + "여름 쿨 기준". 컬러 카드: 52dp 원형 스와치(실제 hex) + Serif 16sp 이름 + 설명·밝기 12sp + 보더 pill "체험" 버튼. 피해야 할 컬러: `block` 아이콘(`#C2410C`) + 13sp 텍스트 (기존 ⚠️ 제거)
- **하단 CTA**: 보더 pill "이 스타일 잘하는 주변 미용실 찾기"(`location_on` 퍼플) — **기존 카카오 옐로우 버튼을 이걸로 교체** + 블랙 pill "홈으로 돌아가기"
- 합성 횟수 배너(초록/주황/빨강 배경)는 제거하고 섹션 헤더 우측 "N회 남음" 텍스트로 통합

### 05 · 가상 스타일링 결과 (SynthesisResultScreen.kt)
- 앱바: `close` + Serif "스타일 적용 결과" + 우측 "N회 남음"
- 제목: Serif 24sp "투블럭컷,\n이렇게 어울려요" (스타일명 동적)
- **Before/After 2열 그리드** (gap 12dp): Before = 보더 1dp `#E2DCCF`, After = **보더 2dp `#5B4FFF`**. 높이 250dp. 하단 캡션 "BEFORE"(12sp, letter-spacing 0.14em, `#8A8578`) / "AFTER"(퍼플 Bold)
- 저장/공유: 보더 pill 2열 (`download` / `share` 아이콘 17dp)
- 하단: 블랙 pill "다른 스타일 적용해보기" + 언더라인 "결과로 돌아가기"

### 06 · 주변 미용실 (SalonListScreen.kt)
- 앱바: back + Serif "주변 미용실" + 우측 `search`
- 지도 영역: 상단 260dp 고정(카카오맵 WebView 유지), 우하단 흰 원형 `my_location` FAB(보더 1dp)
- 리스트 헤더: Serif "내 주변 N곳" + "거리순"(12sp)
- 살롱 행: 상단 1dp 보더 구분, 56dp 썸네일(radius 2dp) + 이름 15sp SemiBold + 메타 12sp "★ 4.8 · 180m · …" + chevron

## Interactions & Behavior
- 모든 기존 콜백/네비게이션 플로우 유지 (onStartClick, onFindSalonClick, onSynthesizeClick, submitFeedback 등)
- 좋아요/싫어요 선택 상태: 보더·아이콘 색을 `#5B4FFF`로 전환 (기존 초록/빨강 → 퍼플로 통일)
- 버튼 press: Compose 기본 ripple, 색 `#1C1826` 계열
- 합성 횟수 소진 시: 기존 스낵바 로직 유지, pill 버튼은 opacity 0.4 disabled
- 로딩/에러 상태는 기존 로직 그대로, 색상만 토큰으로 교체

## State Management
변경 없음 — 기존 `AnalysisViewModel`, `HairColorUiState`, `SynthesisUsageState`, Room 히스토리를 그대로 사용. UI 레이어만 교체.

## Assets
- Noto Serif KR 폰트 (Google Fonts, weight 600 필수)
- Pretendard 폰트 (선택, 권장)
- Material Symbols Rounded 아이콘
- 스타일 예시 사진·지도는 기존 데이터 소스 사용 (디자인 파일의 스트라이프는 플레이스홀더)

## Files
- `HairMe Redesign.dc.html` — 디자인 레퍼런스. 브라우저로 열면 폰 프레임 목업으로 볼 수 있다. **상단 "2a Atelier 풀 플로우" 섹션이 구현 대상**, 하단 1a/1b/1c는 폐기된 탐색 시안.
