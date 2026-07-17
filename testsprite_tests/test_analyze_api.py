#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HairMe Analyze API 응답 확인 스크립트

목적: /api/analyze 응답에서 hairstyle_id가 포함되는지 확인
"""

import sys
import io
import requests
import json
from pathlib import Path

# Windows 콘솔 인코딩 문제 해결
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

# API 설정
BASE_URL = "https://hairme.click"
ANALYZE_ENDPOINT = f"{BASE_URL}/api/analyze"

def test_analyze_with_sample_image():
    """
    샘플 이미지로 분석 API 테스트

    주의: 실제 이미지 파일이 필요합니다.
    테스트용 얼굴 이미지를 준비해주세요.
    """
    print("=" * 60)
    print("🧪 HairMe Analyze API 응답 테스트")
    print("=" * 60)
    print(f"Endpoint: {ANALYZE_ENDPOINT}\n")

    # 샘플 이미지 파일 경로 (여러 위치 시도)
    possible_paths = [
        Path(__file__).parent / "sample_face.jpg",
        Path.home() / "Downloads" / "face.jpg",
        Path.home() / "Pictures" / "face.jpg",
    ]

    image_path = None
    for path in possible_paths:
        if path.exists():
            image_path = path
            break

    if image_path is None:
        print("⚠️  샘플 이미지를 찾을 수 없습니다.")
        print("\n다음 중 한 곳에 얼굴 사진을 저장해주세요:")
        for path in possible_paths:
            print(f"   - {path}")
        print("\n또는 아래 코드를 수정하여 이미지 경로를 직접 지정하세요:")
        print(f'   image_path = Path(r"C:\\Users\\...\\your_image.jpg")')

        # Mock 응답 데이터 표시
        print("\n" + "=" * 60)
        print("📋 예상되는 서버 응답 구조:")
        print("=" * 60)
        mock_response = {
            "success": True,
            "analysis_id": 123,
            "data": {
                "analysis": {
                    "face_shape": "계란형",
                    "personal_color": "쿨톤",
                    "features": "이목구비가 뚜렷함"
                },
                "recommendations": [
                    {
                        "hairstyle_id": 234,  # ✅ 이 필드가 중요!
                        "style_name": "시스루뱅 단발펌",
                        "reason": "계란형 얼굴에 잘 어울림",
                        "score": 0.95,
                        "image_search_url": "https://search.naver.com/..."
                    },
                    {
                        "hairstyle_id": 87,
                        "style_name": "중단발 레이어드컷",
                        "reason": "쿨톤 피부에 잘 어울림",
                        "score": 0.88,
                        "image_search_url": "https://search.naver.com/..."
                    },
                    {
                        "hairstyle_id": 156,
                        "style_name": "웨이브 펌",
                        "reason": "여성스러운 분위기 연출",
                        "score": 0.82,
                        "image_search_url": "https://search.naver.com/..."
                    }
                ]
            },
            "error": None,
            "processing_time": 10.94,
            "model_used": "gemini-flash-latest"
        }
        print(json.dumps(mock_response, indent=2, ensure_ascii=False))

        print("\n" + "=" * 60)
        print("⚠️  확인 사항:")
        print("=" * 60)
        print("1. 백엔드 API가 'hairstyle_id' 필드를 포함하는가?")
        print("2. hairstyle_id가 null이 아닌 정수 값인가?")
        print("3. recommendations 배열의 모든 항목에 hairstyle_id가 있는가?")
        print("\n만약 hairstyle_id가 없거나 null이면:")
        print("   → Android 앱에서 '헤어스타일 ID를 찾을 수 없습니다' 오류 발생")
        print("   → 백엔드 개발자에게 hairstyle_id 추가 요청 필요")
        return

    print(f"✅ 이미지 파일 발견: {image_path}")
    print(f"   파일 크기: {image_path.stat().st_size / 1024:.1f} KB\n")

    try:
        # 이미지 파일 열기
        with open(image_path, 'rb') as f:
            files = {
                'file': ('face.jpg', f, 'image/jpeg')
            }

            print("📤 API 요청 전송 중...")
            response = requests.post(
                ANALYZE_ENDPOINT,
                files=files,
                timeout=60
            )

        print(f"📥 응답 수신: HTTP {response.status_code}\n")

        if response.status_code == 200:
            data = response.json()

            print("=" * 60)
            print("📊 서버 응답 분석:")
            print("=" * 60)

            # 기본 정보
            print(f"✅ success: {data.get('success')}")
            print(f"✅ analysis_id: {data.get('analysis_id')}")
            print(f"✅ processing_time: {data.get('processing_time')}초")
            print(f"✅ model_used: {data.get('model_used')}\n")

            # 분석 결과
            if 'data' in data and data['data']:
                analysis = data['data'].get('analysis', {})
                print("📋 분석 결과:")
                print(f"   - 얼굴형: {analysis.get('face_shape')}")
                print(f"   - 피부톤: {analysis.get('personal_color')}")
                print(f"   - 특징: {analysis.get('features')}\n")

                # 추천 헤어스타일 (중요!)
                recommendations = data['data'].get('recommendations', [])
                print(f"💇 추천 헤어스타일: {len(recommendations)}개\n")

                all_have_id = True
                for i, rec in enumerate(recommendations, 1):
                    print(f"[{i}] {rec.get('style_name')}")

                    hairstyle_id = rec.get('hairstyle_id')
                    if hairstyle_id is not None:
                        print(f"    ✅ hairstyle_id: {hairstyle_id}")
                    else:
                        print(f"    ❌ hairstyle_id: null (문제!)")
                        all_have_id = False

                    print(f"    - score: {rec.get('score')}")
                    print(f"    - reason: {rec.get('reason')}")
                    print()

                print("=" * 60)
                if all_have_id:
                    print("✅ 결과: 모든 추천에 hairstyle_id가 포함되어 있습니다!")
                    print("   → Android 앱에서 정상 작동할 것입니다.")
                else:
                    print("❌ 문제 발견: hairstyle_id가 null인 항목이 있습니다!")
                    print("   → Android 앱에서 '헤어스타일 ID를 찾을 수 없습니다' 오류 발생")
                    print("   → 백엔드 개발자에게 문의하여 hairstyle_id 추가 필요")
                print("=" * 60)

            # 전체 JSON 응답 출력
            print("\n📄 전체 JSON 응답:")
            print(json.dumps(data, indent=2, ensure_ascii=False))

        else:
            print(f"❌ API 실패: {response.status_code}")
            print(f"응답: {response.text}")

    except FileNotFoundError:
        print(f"❌ 이미지 파일을 찾을 수 없습니다: {image_path}")
    except requests.exceptions.Timeout:
        print("❌ 요청 시간 초과 (60초)")
    except requests.exceptions.ConnectionError as e:
        print(f"❌ 연결 오류: {e}")
    except Exception as e:
        print(f"❌ 오류 발생: {e}")


if __name__ == "__main__":
    test_analyze_with_sample_image()
