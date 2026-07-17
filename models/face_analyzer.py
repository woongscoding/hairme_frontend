"""
얼굴 특징 추출 모듈 (비율 기반)

카메라 거리와 무관하게 얼굴 비율만 측정하여 분석
"""
import cv2
import numpy as np
from dataclasses import dataclass, asdict
from typing import Optional
import logging

logger = logging.getLogger(__name__)


@dataclass
class FaceFeatures:
    """거리 무관 얼굴 비율 특징"""
    # 기본 비율
    face_ratio: float  # 높이/너비 (가장 중요!)
    
    # 얼굴 부위별 비율 (얼굴 너비 대비)
    forehead_ratio: float  # 이마 너비 / 얼굴 너비
    cheekbone_ratio: float  # 광대 너비 / 얼굴 너비
    jaw_ratio: float  # 턱 너비 / 얼굴 너비
    
    # 수직 비율
    upper_face_ratio: float  # 이마 높이 / 얼굴 높이
    middle_face_ratio: float  # 중안부 높이 / 얼굴 높이
    lower_face_ratio: float  # 하안부 높이 / 얼굴 높이
    
    # 예측 결과
    face_shape_hint: str  # 예측된 얼굴형
    confidence: float  # 예측 신뢰도 (0~1)
    
    # 원본 픽셀 정보 (로깅용, 실제 분석엔 사용 안함)
    original_width: int
    original_height: int
    
    def to_dict(self):
        """딕셔너리로 변환"""
        return asdict(self)


# 얼굴형별 표준 비율 (연구 데이터 기반)
FACE_SHAPE_STANDARDS = {
    "계란형": {
        "face_ratio": (1.0, 1.3),  # 황금비율
        "forehead_ratio": (0.75, 0.85),
        "cheekbone_ratio": (0.82, 0.92),
        "jaw_ratio": (0.70, 0.80),
        "balance_required": True  # 이마/광대/턱 균형
    },
    "둥근형": {
        "face_ratio": (0.8, 1.0),  # 짧고 넓음
        "forehead_ratio": (0.80, 0.90),
        "cheekbone_ratio": (0.85, 0.95),
        "jaw_ratio": (0.80, 0.90),  # 턱도 넓음
        "balance_required": True
    },
    "각진형": {
        "face_ratio": (1.0, 1.25),
        "forehead_ratio": (0.70, 0.80),
        "cheekbone_ratio": (0.88, 0.98),  # 광대 넓음!
        "jaw_ratio": (0.72, 0.82),
        "balance_required": False  # 광대 > 이마/턱
    },
    "긴형": {
        "face_ratio": (1.35, 1.6),  # 매우 길쭉
        "forehead_ratio": (0.70, 0.85),
        "cheekbone_ratio": (0.75, 0.90),
        "jaw_ratio": (0.65, 0.80),
        "balance_required": True
    }
}


def measure_region_width(roi: np.ndarray, face_width: int) -> float:
    """
    ROI 영역의 평균 수평 너비 측정 (엣지 검출 기반)
    
    Args:
        roi: 분석할 얼굴 영역
        face_width: 전체 얼굴 너비 (정규화용)
    
    Returns:
        측정된 너비 (픽셀)
    """
    try:
        # 엣지 검출
        edges = cv2.Canny(roi, 50, 150)
        
        # 각 행에서 엣지가 있는 최좌/최우 픽셀 간 거리
        widths = []
        for row in edges:
            points = np.where(row > 0)[0]
            if len(points) > 1:
                width = points[-1] - points[0]
                widths.append(width)
        
        # 중앙값 사용 (아웃라이어 제거)
        if len(widths) > 5:
            return float(np.median(widths))
        else:
            # 엣지 검출 실패 시 기본값
            return face_width * 0.7
            
    except Exception as e:
        logger.warning(f"너비 측정 실패: {str(e)}")
        return face_width * 0.7


def extract_face_features(image_data: bytes) -> Optional[FaceFeatures]:
    """
    OpenCV로 얼굴 비율 특징 추출 (거리 무관)
    
    얼굴형 판별 기준:
    - 계란형: 1.0 < ratio < 1.3, 균형잡힌 3등분
    - 둥근형: ratio < 1.0, 턱이 둥글고 넓음
    - 각진형: 광대 > 이마/턱, 각진 턱선
    - 긴형: ratio > 1.35, 세로로 길쭉함
    
    Args:
        image_data: 이미지 바이너리 데이터
        
    Returns:
        FaceFeatures 또는 None (얼굴 검출 실패 시)
    """
    try:
        # 이미지 디코딩
        nparr = np.frombuffer(image_data, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            logger.error("이미지 디코딩 실패")
            return None
        
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        
        # 얼굴 검출
        face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
        )
        faces = face_cascade.detectMultiScale(
            gray, 
            scaleFactor=1.1, 
            minNeighbors=5, 
            minSize=(100, 100)
        )
        
        if len(faces) == 0:
            logger.warning("얼굴 검출 실패")
            return None
        
        # 가장 큰 얼굴 선택
        (x, y, w, h) = max(faces, key=lambda f: f[2] * f[3])
        face_roi = gray[y:y+h, x:x+w]
        
        # ========== 1. 기본 얼굴 비율 (가장 중요!) ==========
        face_ratio = h / w
        
        # ========== 2. 얼굴을 3등분으로 나눔 ==========
        # 이마 영역 (상단 1/3)
        upper_third = face_roi[0:int(h*0.33), :]
        # 중안부 영역 (중간 1/3) - 광대
        middle_third = face_roi[int(h*0.33):int(h*0.67), :]
        # 하안부 영역 (하단 1/3) - 턱
        lower_third = face_roi[int(h*0.67):, :]
        
        # 각 영역의 수평 너비 측정
        forehead_width = measure_region_width(upper_third, w)
        cheekbone_width = measure_region_width(middle_third, w)
        jaw_width = measure_region_width(lower_third, w)
        
        # ========== 3. 비율 계산 (얼굴 너비 w로 정규화) ==========
        forehead_ratio = forehead_width / w
        cheekbone_ratio = cheekbone_width / w
        jaw_ratio = jaw_width / w
        
        # 수직 비율 (각 영역은 균등하게 1/3)
        upper_face_ratio = 0.33
        middle_face_ratio = 0.33
        lower_face_ratio = 0.33
        
        # ========== 4. 얼굴형 판별 로직 ==========
        face_shape_hint, confidence = classify_face_shape(
            face_ratio, 
            forehead_ratio, 
            cheekbone_ratio, 
            jaw_ratio
        )
        
        logger.info(f"✅ 얼굴 특징 추출 완료: {face_shape_hint} (신뢰도: {confidence:.2f})")
        
        return FaceFeatures(
            face_ratio=round(face_ratio, 3),
            forehead_ratio=round(forehead_ratio, 3),
            cheekbone_ratio=round(cheekbone_ratio, 3),
            jaw_ratio=round(jaw_ratio, 3),
            upper_face_ratio=upper_face_ratio,
            middle_face_ratio=middle_face_ratio,
            lower_face_ratio=lower_face_ratio,
            face_shape_hint=face_shape_hint,
            confidence=round(confidence, 2),
            original_width=int(w),
            original_height=int(h)
        )
        
    except Exception as e:
        logger.error(f"얼굴 특징 추출 실패: {str(e)}")
        return None


def classify_face_shape(
    face_ratio: float,
    forehead_ratio: float,
    cheekbone_ratio: float,
    jaw_ratio: float
) -> tuple[str, float]:
    """
    비율을 기반으로 얼굴형 분류
    
    Returns:
        (얼굴형, 신뢰도)
    """
    
    # 규칙 기반 분류
    if face_ratio > 1.35:
        # 긴형: 세로가 매우 길다
        confidence = min((face_ratio - 1.35) * 2 + 0.5, 0.9)
        return "긴형", confidence
        
    elif face_ratio < 0.95:
        # 둥근형: 가로가 넓다
        confidence = min((0.95 - face_ratio) * 2 + 0.5, 0.9)
        return "둥근형", confidence
        
    elif cheekbone_ratio > forehead_ratio * 1.1 and cheekbone_ratio > jaw_ratio * 1.15:
        # 각진형: 광대가 이마/턱보다 확실히 넓다
        width_diff = cheekbone_ratio - max(forehead_ratio, jaw_ratio)
        confidence = min(width_diff * 3 + 0.5, 0.85)
        return "각진형", confidence
        
    elif 1.0 <= face_ratio <= 1.3 and abs(forehead_ratio - jaw_ratio) < 0.1:
        # 계란형: 비율 균형, 이마/턱 비슷
        balance_score = 1 - abs(forehead_ratio - jaw_ratio) * 5
        confidence = min(balance_score * 0.8, 0.8)
        return "계란형", confidence
        
    else:
        # 애매한 경우 - 확률 계산으로 결정
        probabilities = calculate_shape_probability(
            face_ratio, forehead_ratio, cheekbone_ratio, jaw_ratio
        )
        best_shape = max(probabilities, key=probabilities.get)
        confidence = probabilities[best_shape]
        
        return best_shape, confidence


def calculate_shape_probability(
    face_ratio: float,
    forehead_ratio: float,
    cheekbone_ratio: float,
    jaw_ratio: float
) -> dict:
    """
    각 얼굴형일 확률 계산 (모든 케이스 평가)
    
    Returns:
        {"계란형": 0.65, "둥근형": 0.23, ...}
    """
    probabilities = {}
    
    for shape, standards in FACE_SHAPE_STANDARDS.items():
        score = 0.0
        
        # 1. face_ratio 점수 (가중치 50%)
        ratio_range = standards["face_ratio"]
        if ratio_range[0] <= face_ratio <= ratio_range[1]:
            score += 0.5
        else:
            # 범위 밖이면 거리에 따라 감점
            distance = min(
                abs(face_ratio - ratio_range[0]),
                abs(face_ratio - ratio_range[1])
            )
            score += max(0.5 - distance * 0.5, 0)
        
        # 2. 이마 비율 점수 (가중치 15%)
        forehead_range = standards["forehead_ratio"]
        if forehead_range[0] <= forehead_ratio <= forehead_range[1]:
            score += 0.15
        else:
            distance = min(
                abs(forehead_ratio - forehead_range[0]),
                abs(forehead_ratio - forehead_range[1])
            )
            score += max(0.15 - distance, 0)
        
        # 3. 광대 비율 점수 (가중치 15%)
        cheekbone_range = standards["cheekbone_ratio"]
        if cheekbone_range[0] <= cheekbone_ratio <= cheekbone_range[1]:
            score += 0.15
        else:
            distance = min(
                abs(cheekbone_ratio - cheekbone_range[0]),
                abs(cheekbone_ratio - cheekbone_range[1])
            )
            score += max(0.15 - distance, 0)
        
        # 4. 턱 비율 점수 (가중치 10%)
        jaw_range = standards["jaw_ratio"]
        if jaw_range[0] <= jaw_ratio <= jaw_range[1]:
            score += 0.10
        else:
            distance = min(
                abs(jaw_ratio - jaw_range[0]),
                abs(jaw_ratio - jaw_range[1])
            )
            score += max(0.10 - distance, 0)
        
        # 5. 균형 점수 (가중치 10%)
        if standards["balance_required"]:
            # 이마/턱 비율이 비슷해야 함
            balance = 1 - abs(forehead_ratio - jaw_ratio) * 2
            score += max(balance * 0.1, 0)
        else:
            # 각진형: 광대가 넓어야 함
            if cheekbone_ratio > max(forehead_ratio, jaw_ratio):
                score += 0.1
        
        probabilities[shape] = round(max(score, 0), 3)
    
    return probabilities


def create_enhanced_prompt(features: FaceFeatures) -> str:
    """
    OpenCV 특징을 포함한 개선된 Gemini 프롬프트 생성
    
    Args:
        features: 추출된 얼굴 특징
        
    Returns:
        Gemini API용 프롬프트
    """
    # 모든 얼굴형 확률 계산
    probabilities = calculate_shape_probability(
        features.face_ratio,
        features.forehead_ratio,
        features.cheekbone_ratio,
        features.jaw_ratio
    )
    
    # 상위 2개 예측
    sorted_shapes = sorted(probabilities.items(), key=lambda x: x[1], reverse=True)
    
    prompt = f"""얼굴 사진을 분석하여 JSON으로 응답하세요.

**📊 OpenCV 측정 비율 (카메라 거리 보정됨):**

1. **얼굴 세로/가로 비율: {features.face_ratio:.2f}**
   - 해석: {'정사각형에 가까움' if features.face_ratio < 1.0 else '세로가 긺' if features.face_ratio > 1.3 else '표준 비율'}
   - 참고: 1.0=정사각형, 1.2=표준, 1.5+=긴형

2. **부위별 너비 비율 (얼굴 너비 대비):**
   - 이마: {features.forehead_ratio:.2f}
   - 광대: {features.cheekbone_ratio:.2f}
   - 턱: {features.jaw_ratio:.2f}

**🤖 OpenCV 예측 확률:**
1위: {sorted_shapes[0][0]} ({sorted_shapes[0][1]:.1%})
2위: {sorted_shapes[1][0]} ({sorted_shapes[1][1]:.1%})

**💡 가장 유력한 예측: {features.face_shape_hint}**
   신뢰도: {features.confidence:.0%}

⚠️ **중요:** 위 수치는 참고용입니다. 이미지를 직접 보고 최종 판단하세요.
특히 다음 요소는 이미지로만 판별 가능합니다:
- 광대뼈의 각진/둥근 정도
- 턱선의 선명도
- 얼굴 윤곽의 부드러움

최종 분석 결과를 JSON으로:
{{
  "analysis": {{
    "face_shape": "계란형/둥근형/각진형/긴형 중 선택",
    "personal_color": "봄웜/가을웜/여름쿨/겨울쿨 중 선택",
    "features": "이목구비 특징 상세 설명",
    "opencv_agreement": {str(features.confidence > 0.7).lower()}
  }},
  "recommendations": [
    {{"style_name": "추천 스타일명", "reason": "추천 이유 설명"}}
  ]
}}"""
    
    return prompt
