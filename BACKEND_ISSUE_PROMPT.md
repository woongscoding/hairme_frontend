# 백엔드 수정 요청 - 피드백 시스템 구현

## 🚨 현재 문제

Android 앱에서 사용자 피드백(좋아요/싫어요) 기능이 작동하지 않습니다.

---

## 📊 문제 1: `/api/analyze` 응답에 필수 필드 누락

### 현재 서버 응답 (실제 로그):

```json
{
  "success": true,
  "analysis_id": 16,
  "data": {
    "analysis": {
      "face_shape": "계란형",
      "personal_color": "가을웜",
      "features": "..."
    },
    "recommendations": [
      {
        "style_name": "클래식 포마드 펌",
        "reason": "강조된 이마로 신뢰감을 주고, 깔끔한 젠틀맨 스타일 연출에 적합합니다.",
        "hairstyle_id": null,  // ❌ null입니다!
        "score": null          // ❌ null입니다!
      },
      {
        "style_name": "세미 리젠트 컷",
        "reason": "앞머리를 가볍게 세워 활동적이고 도전적인 이미지를 부여합니다.",
        "hairstyle_id": null,  // ❌ null입니다!
        "score": null          // ❌ null입니다!
      },
      {
        "style_name": "소프트 투블럭 댄디컷",
        "reason": "옆머리 부피를 줄여 단정하며, 트렌디하면서도 포멀한 느낌을 유지합니다.",
        "hairstyle_id": null,  // ❌ null입니다!
        "score": null          // ❌ null입니다!
      }
    ]
  }
}
```

### 필요한 수정:

**각 추천 헤어스타일에 다음 필드를 추가해주세요:**

1. `hairstyle_id` (Integer, NOT NULL)
   - 데이터베이스의 헤어스타일 고유 ID
   - 예: 234, 87, 156 등
   - 이 값으로 사용자 피드백을 저장합니다

2. `score` (Float, NOT NULL, 0.0~1.0)
   - ML 모델의 예측 점수 또는 매칭 점수
   - 예: 0.95, 0.88, 0.82 등
   - 순위가 높을수록 높은 점수

### 수정 후 기대하는 응답:

```json
{
  "success": true,
  "analysis_id": 16,
  "data": {
    "analysis": {
      "face_shape": "계란형",
      "personal_color": "가을웜",
      "features": "..."
    },
    "recommendations": [
      {
        "hairstyle_id": 234,        // ✅ DB의 실제 ID
        "style_name": "클래식 포마드 펌",
        "reason": "강조된 이마로 신뢰감을 주고, 깔끔한 젠틀맨 스타일 연출에 적합합니다.",
        "score": 0.95,              // ✅ ML 예측 점수
        "image_search_url": "https://search.naver.com/search.naver?where=image&query=..."  // (선택)
      },
      {
        "hairstyle_id": 87,         // ✅ DB의 실제 ID
        "style_name": "세미 리젠트 컷",
        "reason": "앞머리를 가볍게 세워 활동적이고 도전적인 이미지를 부여합니다.",
        "score": 0.88,              // ✅ ML 예측 점수
        "image_search_url": "..."
      },
      {
        "hairstyle_id": 156,        // ✅ DB의 실제 ID
        "style_name": "소프트 투블럭 댄디컷",
        "reason": "옆머리 부피를 줄여 단정하며, 트렌디하면서도 포멀한 느낌을 유지합니다.",
        "score": 0.82,              // ✅ ML 예측 점수
        "image_search_url": "..."
      }
    ]
  }
}
```

---

## 🚨 문제 2: `/api/feedback/submit` 엔드포인트 없음 (404)

### 현재 상황:

```bash
POST https://hairme.click/api/feedback/submit
→ HTTP 404 {"detail": "Not Found"}
```

이 엔드포인트가 구현되어 있지 않습니다.

### 필요한 작업:

**새로운 엔드포인트 생성: `POST /api/feedback/submit`**

---

## 📋 해결 방법 (상세 가이드)

### 1. `/api/analyze` 수정

#### FastAPI 예시 코드:

```python
# backend/routes/analyze.py

from typing import List
from pydantic import BaseModel

class HairstyleRecommendation(BaseModel):
    hairstyle_id: int          # ✅ 추가
    style_name: str
    reason: str
    score: float               # ✅ 추가
    image_search_url: str = None

@router.post("/api/analyze")
async def analyze_hairstyle(
    file: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    # 1. 이미지 분석 (기존 로직)
    face_analysis = await analyze_face_image(file)

    # 2. 헤어스타일 추천 (여기를 수정해주세요!)
    recommendations = []

    # DB에서 추천 헤어스타일 조회
    # (예시: 얼굴형에 맞는 스타일 필터링)
    hairstyles = db.query(Hairstyle).filter(
        Hairstyle.suitable_face_shape == face_analysis.face_shape
    ).all()

    # ML 모델로 점수 계산 또는 고정 점수 부여
    for i, hairstyle in enumerate(hairstyles[:3]):
        # ✅ 중요: hairstyle_id와 score를 반드시 포함!
        recommendations.append({
            "hairstyle_id": hairstyle.id,  # ✅ DB의 실제 ID
            "style_name": hairstyle.name,
            "reason": generate_reason(hairstyle, face_analysis),
            "score": calculate_score(hairstyle, face_analysis),  # ✅ 0.0~1.0 점수
            "image_search_url": f"https://search.naver.com/search.naver?where=image&query={hairstyle.name}"
        })

    # 점수 순으로 정렬 (높은 순)
    recommendations.sort(key=lambda x: x["score"], reverse=True)

    return {
        "success": True,
        "analysis_id": analysis.id,
        "data": {
            "analysis": {
                "face_shape": face_analysis.face_shape,
                "personal_color": face_analysis.skin_tone,
                "features": face_analysis.features
            },
            "recommendations": recommendations[:3]  # 상위 3개
        },
        "processing_time": elapsed_time,
        "model_used": "gemini-flash-latest"
    }
```

#### 점수 계산 예시:

```python
def calculate_score(hairstyle, face_analysis):
    """
    ML 모델이 없다면 간단한 규칙 기반 점수도 가능
    """
    base_score = 0.7

    # 얼굴형 매칭
    if hairstyle.suitable_face_shape == face_analysis.face_shape:
        base_score += 0.2

    # 피부톤 매칭
    if hairstyle.suitable_skin_tone == face_analysis.skin_tone:
        base_score += 0.1

    return min(base_score, 1.0)  # 최대 1.0
```

**또는 ML 모델 사용:**

```python
def calculate_score(hairstyle, face_analysis):
    """
    실제 ML 모델로 예측
    """
    features = [
        face_analysis.face_shape_encoded,
        face_analysis.skin_tone_encoded,
        hairstyle.style_type_encoded
    ]

    prediction = ml_model.predict([features])[0]
    return float(prediction)  # 0.0~1.0
```

---

### 2. `/api/feedback/submit` 엔드포인트 생성

#### FastAPI 예시 코드:

```python
# backend/routes/feedback.py

from pydantic import BaseModel
from datetime import datetime
from sqlalchemy.orm import Session

class FeedbackRequest(BaseModel):
    face_shape: str           # "계란형", "둥근형" 등
    skin_tone: str            # "쿨톤", "웜톤", "가을웜" 등
    hairstyle_id: int         # ✅ DB의 헤어스타일 ID
    user_reaction: str        # "👍" 또는 "👎"
    ml_prediction: float      # ML 예측 점수 (0.0~1.0)
    naver_clicked: bool = False  # 네이버 검색 클릭 여부
    user_id: str = "anonymous"   # 사용자 ID (선택)

class FeedbackResponse(BaseModel):
    success: bool
    message: str

@router.post("/api/feedback/submit", response_model=FeedbackResponse)
async def submit_feedback(
    request: FeedbackRequest,
    db: Session = Depends(get_db)
):
    try:
        # 피드백 데이터 DB에 저장
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
        db.refresh(feedback)

        logger.info(f"✅ Feedback saved: ID={feedback.id}, hairstyle={request.hairstyle_id}, reaction={request.user_reaction}")

        return FeedbackResponse(
            success=True,
            message="피드백이 저장되었습니다"
        )

    except Exception as e:
        logger.error(f"❌ Feedback submission failed: {e}")
        db.rollback()
        raise HTTPException(
            status_code=500,
            detail=f"피드백 저장 실패: {str(e)}"
        )
```

#### DB 모델:

```python
# backend/models/feedback.py

from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, ForeignKey
from sqlalchemy.sql import func

class Feedback(Base):
    __tablename__ = "feedback"

    id = Column(Integer, primary_key=True, index=True)
    face_shape = Column(String(50), nullable=False)
    skin_tone = Column(String(50), nullable=False)
    hairstyle_id = Column(Integer, ForeignKey("hairstyles.id"), nullable=False)
    user_reaction = Column(String(10), nullable=False)  # "👍" or "👎"
    ml_prediction = Column(Float, nullable=False)
    naver_clicked = Column(Boolean, default=False)
    user_id = Column(String(100), default="anonymous")
    created_at = Column(DateTime, server_default=func.now())
```

#### DB 마이그레이션 (Alembic):

```python
# alembic/versions/xxx_add_feedback_table.py

def upgrade():
    op.create_table(
        'feedback',
        sa.Column('id', sa.Integer(), primary_key=True),
        sa.Column('face_shape', sa.String(50), nullable=False),
        sa.Column('skin_tone', sa.String(50), nullable=False),
        sa.Column('hairstyle_id', sa.Integer(), sa.ForeignKey('hairstyles.id'), nullable=False),
        sa.Column('user_reaction', sa.String(10), nullable=False),
        sa.Column('ml_prediction', sa.Float(), nullable=False),
        sa.Column('naver_clicked', sa.Boolean(), default=False),
        sa.Column('user_id', sa.String(100), default='anonymous'),
        sa.Column('created_at', sa.DateTime(), server_default=sa.func.now())
    )

def downgrade():
    op.drop_table('feedback')
```

**또는 SQL 직접 실행:**

```sql
CREATE TABLE feedback (
    id SERIAL PRIMARY KEY,
    face_shape VARCHAR(50) NOT NULL,
    skin_tone VARCHAR(50) NOT NULL,
    hairstyle_id INT NOT NULL REFERENCES hairstyles(id),
    user_reaction VARCHAR(10) NOT NULL,
    ml_prediction FLOAT NOT NULL,
    naver_clicked BOOLEAN DEFAULT FALSE,
    user_id VARCHAR(100) DEFAULT 'anonymous',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feedback_hairstyle ON feedback(hairstyle_id);
CREATE INDEX idx_feedback_created ON feedback(created_at);
```

---

## 🧪 테스트 방법

### 1. `/api/analyze` 테스트

```bash
# 이미지 업로드 후 hairstyle_id와 score 확인
curl -X POST https://hairme.click/api/analyze \
  -F "file=@test_face.jpg" \
  | jq '.data.recommendations[0]'
```

**기대 출력:**
```json
{
  "hairstyle_id": 234,    // ✅ 숫자 (null 아님)
  "style_name": "클래식 포마드 펌",
  "reason": "...",
  "score": 0.95           // ✅ 숫자 (null 아님)
}
```

### 2. `/api/feedback/submit` 테스트

```bash
curl -X POST https://hairme.click/api/feedback/submit \
  -H "Content-Type: application/json" \
  -d '{
    "face_shape": "계란형",
    "skin_tone": "가을웜",
    "hairstyle_id": 234,
    "user_reaction": "👍",
    "ml_prediction": 0.95,
    "naver_clicked": false
  }'
```

**기대 출력:**
```json
{
  "success": true,
  "message": "피드백이 저장되었습니다"
}
```

### 3. DB 확인

```sql
-- 피드백이 저장되었는지 확인
SELECT * FROM feedback ORDER BY created_at DESC LIMIT 10;
```

---

## 📱 Android 앱 테스트

백엔드 수정 완료 후:

### 1. Android Studio Logcat 확인

```
필터: "HairstyleRepository"
```

**성공 시 로그:**
```
📊 서버 응답 상세 정보:
   [1] 클래식 포마드 펌
       - hairstyle_id: 234  ✅ (숫자)
       - score: 0.95  ✅ (숫자)

🔄 변환된 AnalysisResult:
   [1] 클래식 포마드 펌
       - id: 234  ✅
       - score: 0.95  ✅
```

### 2. 피드백 버튼 테스트

```
필터: "ResultScreen"
```

**성공 시 로그:**
```
📤 피드백 제출 시작
   - 얼굴형: 계란형
   - 피부톤: 가을웜
   - 헤어스타일 ID: 234  ✅
   - 사용자 반응: 👍
   - ML 예측 점수: 0.95

📡 서버 응답 수신 - HTTP 200
✅ 피드백 제출 성공
```

**앱 화면:**
- Toast 메시지: "좋아요가 반영되었습니다"

---

## ⚠️ 주의사항

### 1. hairstyle_id는 반드시 실제 DB ID여야 합니다

❌ **잘못된 예:**
```python
# 하드코딩된 더미 값
recommendations.append({
    "hairstyle_id": 1,  # 모든 추천에 같은 값
    ...
})
```

✅ **올바른 예:**
```python
# DB에서 실제 ID 사용
recommendations.append({
    "hairstyle_id": hairstyle.id,  # 각 헤어스타일의 고유 ID
    ...
})
```

### 2. score는 0.0~1.0 범위

```python
# 점수가 1.0을 넘지 않도록
score = min(calculated_score, 1.0)
score = max(score, 0.0)
```

### 3. 추천 순서는 점수 순

```python
# 높은 점수부터 정렬
recommendations.sort(key=lambda x: x["score"], reverse=True)
return recommendations[:3]  # 상위 3개
```

---

## 📊 예상 데이터 흐름

```
사용자 얼굴 사진 업로드
  ↓
POST /api/analyze
  ↓
{
  "recommendations": [
    {"hairstyle_id": 234, "score": 0.95, ...},  ← ✅ ID와 점수 포함
    {"hairstyle_id": 87, "score": 0.88, ...},
    {"hairstyle_id": 156, "score": 0.82, ...}
  ]
}
  ↓
Android 앱에 표시
  ↓
사용자가 "좋아요" 클릭
  ↓
POST /api/feedback/submit
{
  "hairstyle_id": 234,  ← Android에서 받은 ID
  "user_reaction": "👍",
  "ml_prediction": 0.95
}
  ↓
DB에 저장 → 성공 응답
  ↓
Android 앱: "좋아요가 반영되었습니다" Toast 표시
```

---

## ✅ 완료 체크리스트

- [ ] `/api/analyze` 응답에 `hairstyle_id` (Integer) 추가
- [ ] `/api/analyze` 응답에 `score` (Float, 0.0~1.0) 추가
- [ ] `POST /api/feedback/submit` 엔드포인트 생성
- [ ] `feedback` 테이블 생성 (또는 마이그레이션)
- [ ] curl로 두 엔드포인트 테스트
- [ ] Android 앱에서 실제 테스트
- [ ] Logcat에서 성공 로그 확인

---

## 🔗 참고 파일

**Android 앱 (수정 불필요, 이미 완성):**
- `app/src/main/java/com/example/myapplication/network/HairstyleApiService.kt`
- `app/src/main/java/com/example/myapplication/network/ApiResponse.kt`
- `app/src/main/java/com/example/myapplication/ResultScreen.kt`

**테스트 스크립트:**
- `testsprite_tests/test_analyze_api.py` (분석 API 응답 확인)
- `testsprite_tests/test_feedback_api.py` (피드백 API 테스트)

---

## 💬 질문이나 문제가 있다면

1. 로그 확인: FastAPI 서버 로그
2. Android Logcat 확인
3. DB 쿼리 확인: `SELECT * FROM feedback;`

수정 완료 후 알려주시면 즉시 Android 앱에서 테스트하겠습니다!
