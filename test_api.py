import requests
import json

print("=" * 60)
print("1. Feedback API Test")
print("=" * 60)

feedback_data = {
    "face_shape": "oval",
    "skin_tone": "cool",
    "hairstyle_id": 234,
    "user_reaction": "like",
    "ml_prediction": 0.95,
    "naver_clicked": False,
    "user_id": "test_user"
}

try:
    response = requests.post(
        "https://hairme.click/api/feedback/submit",
        json=feedback_data,
        timeout=10
    )
    print(f"[OK] Status Code: {response.status_code}")
    print(f"Response:")
    try:
        print(json.dumps(response.json(), indent=2, ensure_ascii=False))
    except:
        print(response.text)
except Exception as e:
    print(f"[ERROR] {e}")
    try:
        print(f"Response: {e.response.text if hasattr(e, 'response') else 'N/A'}")
    except:
        pass

print("\n" + "=" * 60)
print("2. Analysis API Check")
print("=" * 60)
print("[INFO] Image file required for /api/analyze test")
print("[ACTION] Please ask backend developer to verify:")
print("  - /api/analyze returns 'hairstyle_id' field")
print("  - 'hairstyle_id' is NOT null (must be a number)")
print("  - 'score' field is included (0.0-1.0)")
