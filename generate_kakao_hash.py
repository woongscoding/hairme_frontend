import hashlib
import base64

# Release keystore의 Base64 인증서
cert_base64 = """
MIIDdzCCAl+gAwIBAgIIKtlWZ9jPDHcwDQYJKoZIhvcNAQEMBQAwaTELMAkGA1UE
BhMCS1IxDjAMBgNVBAgTBVNlb3VsMQ4wDAYDVQQHEwVTZW91bDEPMA0GA1UEChMG
SGFpck1lMRQwEgYDVQQLEwtEZXZlbG9wbWVudDETMBEGA1UEAxMKSGFpck1lIEFw
cDAgFw0yNTExMDUxMzM4MTNaGA8yMDUzMDMyMzEzMzgxM1owaTELMAkGA1UEBhMC
S1IxDjAMBgNVBAgTBVNlb3VsMQ4wDAYDVQQHEwVTZW91bDEPMA0GA1UEChMGSGFp
ck1lMRQwEgYDVQQLEwtEZXZlbG9wbWVudDETMBEGA1UEAxMKSGFpck1lIEFwcDCC
ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKcB/Ip9CLV7h0zcfc3q7/pu
IjFPSxjyioO+T972hXq1twmBQ/a/KqX1lumgyXMPqYGFhPa6SML5kChjzHaY0PS5
1U0Cd8wT5gmA61a/BqvP1yVs5yG3Ea8caPLqQnerDycRQpTs+vMRgioRgNoKOnyt
0ufZKUA2BmDqQDjRNEJqczKwFbeHjEOJ8FWk7OFwwK3vh+1pNszhKnZONRr6Erce
V8OSAlVUI0BkM2IjF/HK3CRxB7cNESl2oX+4RKypC5IrjPiiod8Ri4deht3HX9ki
O1BJq09AYa0yAVq+uYcdGcJVVq7ur0DMp//h5azi+qXKOAE8teKHBJ7QuGux9ekC
AwEAAaMhMB8wHQYDVR0OBBYEFCtI/hqZuU1srICLklFh/VjbV/reMA0GCSqGSIb3
DQEBDAUAA4IBAQBTZ9DZTEAfmKs0xhpdgVP1IxsgPqGIzLTwO6ujX/Grql6JhZEi
jFdwmUoRYjFYsVrrg2RkulAbg/GxLemk7fpYKwBaXroRlyCheJjHWK9z/9nJXhz0
NLhh6SMll29aR0CGxRkfdHL3DCGk2zmBaebtl/Pt7XqHTcOMRui/Ifdv2FNMWlw8
VtyL6VhnKrRO+V2ZKGTS57NtE+3bWz4j6Qccs9gTwsCUuSQRb0+h39exROVyM9vi
ouUNoidxpbtQIWOxs3KQmyuzMyyb6x/M4bSYGjqwAuqpjjixEug7FxRwZbSPc0zE
wVrjET+yuMZFRknJfmoB6BO7tL0IhlWRcvQ0
"""

# Base64 디코딩
cert_der = base64.b64decode(cert_base64.strip())

# SHA1 해시 생성
sha1_hash = hashlib.sha1(cert_der).digest()

# Base64 인코딩 (카카오 키 해시)
kakao_key_hash = base64.b64encode(sha1_hash).decode('utf-8')

print("=" * 60)
print("카카오 개발자 콘솔에 등록할 키 해시:")
print("=" * 60)
print(kakao_key_hash)
print("=" * 60)
print("\n이 키 해시를 카카오 개발자 콘솔의")
print("앱 설정 > 플랫폼 > Android > 키 해시에 추가하세요!")
