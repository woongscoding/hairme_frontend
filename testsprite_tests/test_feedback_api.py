#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HairMe Feedback API Test Script

테스트 목적: 피드백 제출 API가 정상적으로 동작하는지 확인
API 엔드포인트: POST https://hairme.click/api/feedback/submit
"""

import sys
import io
import requests
import json
from typing import Dict, Any

# Windows 콘솔 인코딩 문제 해결
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

# API 설정
BASE_URL = "https://hairme.click"
FEEDBACK_ENDPOINT = f"{BASE_URL}/api/feedback/submit"

# 테스트 케이스
test_cases = [
    {
        "name": "✅ Test 1: Valid Like Feedback",
        "data": {
            "face_shape": "계란형",
            "skin_tone": "쿨톤",
            "hairstyle_id": 123,
            "user_reaction": "👍",
            "ml_prediction": 0.95,
            "naver_clicked": False,
            "user_id": "test_user_1"
        },
        "expected_status": 200
    },
    {
        "name": "✅ Test 2: Valid Dislike Feedback",
        "data": {
            "face_shape": "둥근형",
            "skin_tone": "웜톤",
            "hairstyle_id": 87,
            "user_reaction": "👎",
            "ml_prediction": 0.72,
            "naver_clicked": False,
            "user_id": "test_user_2"
        },
        "expected_status": 200
    },
    {
        "name": "✅ Test 3: Feedback with Naver Click",
        "data": {
            "face_shape": "각진형",
            "skin_tone": "중간톤",
            "hairstyle_id": 234,
            "user_reaction": "👍",
            "ml_prediction": 0.88,
            "naver_clicked": True,
            "user_id": "test_user_3"
        },
        "expected_status": 200
    },
    {
        "name": "❌ Test 4: Invalid hairstyle_id (9999)",
        "data": {
            "face_shape": "계란형",
            "skin_tone": "쿨톤",
            "hairstyle_id": 9999,
            "user_reaction": "👍",
            "ml_prediction": 0.95
        },
        "expected_status": [400, 404]  # 백엔드 구현에 따라 다를 수 있음
    },
    {
        "name": "❌ Test 5: Missing required fields",
        "data": {
            "hairstyle_id": 123
        },
        "expected_status": 422  # FastAPI validation error
    },
    {
        "name": "❌ Test 6: Invalid ml_prediction (out of range)",
        "data": {
            "face_shape": "계란형",
            "skin_tone": "쿨톤",
            "hairstyle_id": 123,
            "user_reaction": "👍",
            "ml_prediction": 1.5  # 0.0-1.0 범위 초과
        },
        "expected_status": [400, 422]
    }
]


def test_feedback_api(test_case: Dict[str, Any]) -> None:
    """
    피드백 API 테스트 실행
    """
    print(f"\n{'='*60}")
    print(f"🧪 {test_case['name']}")
    print(f"{'='*60}")

    # 요청 데이터 출력
    print("\n📤 Request:")
    print(f"   URL: {FEEDBACK_ENDPOINT}")
    print(f"   Method: POST")
    print(f"   Headers: Content-Type: application/json")
    print(f"   Body:")
    print(json.dumps(test_case['data'], indent=6, ensure_ascii=False))

    try:
        # API 호출
        response = requests.post(
            FEEDBACK_ENDPOINT,
            json=test_case['data'],
            headers={"Content-Type": "application/json"},
            timeout=10
        )

        # 응답 출력
        print(f"\n📥 Response:")
        print(f"   Status Code: {response.status_code}")

        try:
            response_json = response.json()
            print(f"   Body:")
            print(json.dumps(response_json, indent=6, ensure_ascii=False))
        except json.JSONDecodeError:
            print(f"   Body (raw): {response.text}")

        # 결과 검증
        expected = test_case['expected_status']
        if isinstance(expected, list):
            success = response.status_code in expected
        else:
            success = response.status_code == expected

        if success:
            print(f"\n✅ PASS: Status code matches expected ({expected})")

            # success 필드 검증 (200 응답인 경우)
            if response.status_code == 200:
                try:
                    if response_json.get('success') == True:
                        print(f"✅ SUCCESS: Feedback submitted successfully")
                    else:
                        print(f"⚠️  WARNING: success=false in response")
                except:
                    pass
        else:
            print(f"\n❌ FAIL: Expected {expected}, got {response.status_code}")

    except requests.exceptions.Timeout:
        print(f"\n❌ FAIL: Request timeout (10s)")
    except requests.exceptions.ConnectionError as e:
        print(f"\n❌ FAIL: Connection error: {e}")
    except Exception as e:
        print(f"\n❌ FAIL: Unexpected error: {e}")


def main():
    """
    모든 테스트 실행
    """
    print("=" * 60)
    print("🧪 HairMe Feedback API Test Suite")
    print("=" * 60)
    print(f"Base URL: {BASE_URL}")
    print(f"Endpoint: {FEEDBACK_ENDPOINT}")
    print(f"Total Tests: {len(test_cases)}")

    # 테스트 실행
    for i, test_case in enumerate(test_cases, 1):
        test_feedback_api(test_case)

    print(f"\n{'='*60}")
    print(f"✅ Test suite completed: {len(test_cases)} tests executed")
    print(f"{'='*60}\n")


if __name__ == "__main__":
    main()
