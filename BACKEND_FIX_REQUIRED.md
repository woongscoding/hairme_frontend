# 백엔드 수정 필요 사항

## 📋 문제 요약

**현재 상황:**
- Android 앱에서 피드백(좋아요/싫어요) 기능이 작동하지 않음
- Logcat 오류: `hairstyle_id가 null입니다`

**원인:**
1. `/api/analyze` 응답에 `hairstyle_id` 필드가 없음
2. `/api/feedback/submit` 엔드포인트가 없음 (404 오류)

---

## 🔧 필요한 수정 사항

### 1. `/api/analyze` 응답에 `hairstyle_id` 추가 (우선순위: 높음)

**현재 백엔드 응답 (문제):**
```json
{
  "success": true,
  "analysis_id": 123,
  "data": {
    "analysis": {
      "face_shape": "계란형",
      "personal_color": "쿨톤",
      "features": "이목구비가 뚜렷함"
    },
    "recommendations": [
      {
        "style_name": "클래식 포마드 펌",
        "reason": "계란형 얼굴에 잘 어울림",
        "score": 0.95
        // ❌ hairstyle_id가 없음!
      }
    ]
  }
}
```

**수정 필요 (해결책):**
```json
{
  "success": true,
  "analysis_id": 123,
  "data": {
    "analysis": {
      "face_shape": "계란형",
      "personal_color": "쿨톤",
      "features": "이목구비가 뚜렷함"
    },
    "recommendations": [
      {
        "hairstyle_id": 234,  // ✅ 추가 필요! (DB의 헤어스타일 ID)
        "style_name": "클래식 포마드 펌",
        "reason": "계란형 얼굴에 잘 어울림",
        "score": 0.95,
        "image_search_url": "https://search.naver.com/..."
      }
    ]
  }
}
```

**FastAPI 예시 코드:**
```python
# backend/models.py
class HairstyleRecommendation(BaseModel):
    hairstyle_id: int  # ✅ 추가
    style_name: str
    reason: str
    score: float
    image_search_url: Optional[str] = None

# backend/routes/analyze.py
@router.post("/api/analyze")
async def analyze_hairstyle(file: UploadFile):
    # ... 분석 로직 ...

    recommendations = []
    for hairstyle in top_hairstyles:
        recommendations.append({
            "hairstyle_id": hairstyle.id,  # ✅ DB ID 포함
            "style_name": hairstyle.name,
            "reason": generate_reason(hairstyle, face_data),
            "score": hairstyle.score,
            "image_search_url": f"https://search.naver.com/search.naver?where=image&query={hairstyle.name}"
        })

    return {
        "success": True,
        "analysis_id": analysis.id,
        "data": {
            "analysis": {...},
            "recommendations": recommendations
        }
    }
```

---

### 2. `/api/feedback/submit` 엔드포인트 생성 (우선순위: 높음)

**엔드포인트:** `POST /api/feedback/submit`

**요청 형식:**
```json
{
  "face_shape": "계란형",
  "skin_tone": "쿨톤",
  "hairstyle_id": 234,
  "user_reaction": "👍",
  "ml_prediction": 0.95,
  "naver_clicked": false,
  "user_id": "anonymous"
}
```

**응답 형식:**
```json
{
  "success": true,
  "message": "피드백이 저장되었습니다"
}
```

**FastAPI 예시 코드:**
```python
# backend/models.py
class FeedbackRequest(BaseModel):
    face_shape: str
    skin_tone: str
    hairstyle_id: int
    user_reaction: str  # "👍" 또는 "👎"
    ml_prediction: float
    naver_clicked: bool = False
    user_id: str = "anonymous"

class FeedbackResponse(BaseModel):
    success: bool
    message: str

# backend/routes/feedback.py
@router.post("/api/feedback/submit")
async def submit_feedback(request: FeedbackRequest, db: Session = Depends(get_db)):
    try:
        # 피드백 DB에 저장
        feedback = Feedback(
            face_shape=request.face_shape,
            skin_tone=request.skin_tone,
            hairstyle_id=request.hairstyle_id,
            user_reaction=request.user_reaction,
            ml_prediction=request.ml_prediction,
            naver_clicked=request.naver_clicked,
            user_id=request.user_id,
            created_at=datetime.now()
        )
        db.add(feedback)
        db.commit()

        return FeedbackResponse(
            success=True,
            message="피드백이 저장되었습니다"
        )
    except Exception as e:
        logger.error(f"Feedback submission failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
```

**데이터베이스 스키마 (예시):**
```sql
CREATE TABLE feedback (
    id SERIAL PRIMARY KEY,
    face_shape VARCHAR(50) NOT NULL,
    skin_tone VARCHAR(50) NOT NULL,
    hairstyle_id INT NOT NULL,
    user_reaction VARCHAR(10) NOT NULL,  -- '👍' or '👎'
    ml_prediction FLOAT NOT NULL,
    naver_clicked BOOLEAN DEFAULT FALSE,
    user_id VARCHAR(100) DEFAULT 'anonymous',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (hairstyle_id) REFERENCES hairstyles(id)
);
```

---

## 🧪 테스트 방법

### 1. `/api/analyze` 테스트

```bash
curl -X POST https://hairme.click/api/analyze \
  -F "file=@test_face.jpg" \
  | jq '.data.recommendations[0].hairstyle_id'
```

**기대 결과:** `234` (숫자, null이 아님)

### 2. `/api/feedback/submit` 테스트

```bash
curl -X POST https://hairme.click/api/feedback/submit \
  -H "Content-Type: application/json" \
  -d '{
    "face_shape": "계란형",
    "skin_tone": "쿨톤",
    "hairstyle_id": 234,
    "user_reaction": "👍",
    "ml_prediction": 0.95,
    "naver_clicked": false
  }'
```

**기대 결과:**
```json
{
  "success": true,
  "message": "피드백이 저장되었습니다"
}
```

---

## 📊 현재 Android 앱 상태

### ✅ 이미 구현된 기능:
- 피드백 API 요청 로직 (`ResultScreen.kt`)
- hairstyle_id null 체크 및 에러 처리
- 상세한 로깅 (디버깅용)
- 네이버 검색 클릭 추적

### ⏳ 임시 해결책 적용:
- hairstyle_id가 null일 때: 피드백 버튼 숨김
- 대신 "💡 피드백 기능은 준비 중입니다" 메시지 표시
- 네이버 검색은 정상 작동 (피드백만 전송 안 됨)

### 🎯 백엔드 수정 완료 후:
1. hairstyle_id가 응답에 포함되면 → 피드백 버튼 자동 표시
2. `/api/feedback/submit` 구현되면 → 피드백 정상 작동
3. 추가 코드 수정 불필요

---

## 🔗 관련 파일

**Android 앱:**
- `app/src/main/java/com/example/myapplication/ResultScreen.kt` (피드백 UI)
- `app/src/main/java/com/example/myapplication/network/HairstyleApiService.kt` (API 인터페이스)
- `app/src/main/java/com/example/myapplication/network/ApiResponse.kt` (응답 모델)
- `app/src/main/java/com/example/myapplication/repository/HairstyleRepository.kt` (API 호출)

**테스트 스크립트:**
- `testsprite_tests/test_feedback_api.py` (피드백 API 테스트)
- `testsprite_tests/test_analyze_api.py` (분석 API 응답 확인)

---

## 📞 문의

백엔드 수정 완료 시 알려주시면, Android 앱에서 즉시 테스트 가능합니다.

**Logcat 확인 방법:**
```
Android Studio → Logcat
필터: "HairstyleRepository" 또는 "ResultScreen"
```

**성공 로그 예시:**
```
📊 서버 응답 상세 정보:
   [1] 클래식 포마드 펌
       - hairstyle_id: 234  ✅

📤 피드백 제출 시작
   - hairstyle_id: 234  ✅

✅ 피드백 제출 성공
```
