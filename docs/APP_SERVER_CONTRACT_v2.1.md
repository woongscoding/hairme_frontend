# 수익화 v2.1 — 앱이 기대하는 서버 계약

앱(Phase 1·2)은 아래 필드가 **없어도** 지금 서버에서 기존 동작으로 돌아가고, 서버가 필드를 내려주는 순간 새 동작으로 바뀐다. 서버 작업 때 이 이름 그대로 맞추면 앱 재배포 없이 켜진다.

## 이미 있는 것 (앱이 그대로 사용)

- `POST /api/credits/purchase` `{product_id, purchase_token}` → 200 `{granted, balance}` / 409 같은 토큰 중복(이미 지급) / 422 `one_per_account_used`(첫 구매 상품 재구매, 서버 미승인) / 403 `purchase_account_mismatch`(다른 계정의 결제, 서버 미승인) / 400 무효. 서버는 acknowledge만 하므로 **앱이 200과 `error` 없는 409 뒤에만 consume** 한다.
- `GET /api/credits` → `balance`, `history[].reason` (`"purchase"`가 있으면 유료 사용자로 판정)
- 합성 응답 `quota` (`mode: credits|device`) → 잔액·남은 횟수 갱신에 사용 (`/api/usage/consume` 호출은 제거)
- 429 본문 `limit_type` → `"device"`일 때만 결제 화면, `"ip"`·분당 제한은 일반 에러
- 기기 한도 429의 `limit_detail` → `"lifetime"`(실험군 평생 무료 체험 소진)이면 로그인 유도 문구로 결제/로그인 화면, `"daily"`는 기존 문구
- 첫 구매 상품 재구매 거절 `error: "one_per_account_used"` → **consume 하지 않고**(서버 미승인 → Google 자동 환불) "결제 금액은 자동 환불돼요" 안내. 서버는 **422**로 보낸다. 앱은 상태 코드가 아니라 `error`로 판정하므로 409로 와도 consume 하지 않는다. `error`가 없는 409만 "이미 지급됨 → consume"
- 결제 계정 확인: 앱은 결제 때 `setObfuscatedAccountId(sha256(user_id) hex)`를 넣는다. 서버는 영수증의 `obfuscatedExternalAccountId`가 있고 로그인 사용자의 해시와 다르면 **403** `error: "purchase_account_mismatch"`(미승인 → 결제한 계정이 3일 안에 반영하지 않으면 Google 자동 환불). 앱은 **consume 하지 않고**, 이번 세션에서는 그 토큰을 다시 보내지 않는다. 앱도 보내기 전에 같은 비교를 해서, 계정 표시가 지금 사용자와 다른 결제는 아예 보내지 않는다(값이 없는 예전 결제는 그대로 보낸다)
- `GET /api/credits/products` → `starter_5`의 `available`로 첫 구매 상품 노출 (회원만, 실패하면 결제 이력으로 판단)

## 서버에 추가해야 하는 것

| 위치 | 필드 | 값 | 앱 동작 |
| --- | --- | --- | --- |
| `GET /api/usage` | `free_policy` | `"daily"` \| `"lifetime"` | 남은 횟수·결제 화면 문구 ("오늘 무료" vs "무료 체험") |
| `GET /api/usage` | `experiment_arm` | `"control"` \| `"treatment"` | treatment: 보상형 광고가 크레딧 대신 퍼스널컬러 상세·전체 랭킹을 연다. 홈/결제 화면의 "광고 보고 크레딧 받기" 숨김. Firebase 사용자 속성 `free_policy_arm` |
| `GET /api/usage` | `signup_bonus` | int (대조군 5, 실험군 3) | "로그인하고 N회 더 받기" |
| 합성 응답 (스타일·염색) | `is_preview` | bool | true면 "무료 미리보기 · 512px · 워터마크" + "이 스타일 고화질로 보기" → 결제 화면 |
| `POST /api/v2/analyze-hybrid` `data` | `full_ranking` | `[{style_name, score}]` 점수순 | 잠금 해제 시 "전체 스타일 랭킹" 표시 |
| `GET /api/credits` | `has_purchased` | bool | history 20건 밖의 결제까지 반영 (없으면 history로 판정) |
| `CREDIT_PRODUCTS` | `starter_5` | 5 | 첫 구매 상품. **계정당 1회 제한은 서버가 강제해야 한다** (앱은 결제 이력이 있으면 숨기기만 함) |

주의할 점:
- 실험군 배정(신규 기기 해시 50:50), 512px·워터마크 렌더링, 캐시 키에 화질 포함(무료 미리보기 캐시가 유료 재합성에 적중하면 안 됨)은 모두 서버 쪽 작업.
- 실험군 기능 해제 광고는 SSV `user_id` 없이 띄운다. 서버의 `reward-callback`은 이를 `verification_ping`으로 보고 지급하지 않아야 한다 (현재 동작 그대로).

## 웹 (공유 링크)

- `https://hairme.app/.well-known/assetlinks.json` — `com.hairme.app` + Play 앱 서명 키 SHA-256
- `https://hairme.app/s?m=<section>` — 앱 없으면 Play(`referrer=utm_source=result_share&utm_medium=<section>&utm_campaign=result_share_v1`)로 리다이렉트
- 준비되면 앱의 `ShareHelper.SHORT_LINK_BASE`를 `"https://hairme.app/s"`로 바꾼다. 그 전까지 공유 텍스트는 Play 링크를 직접 쓴다.

## 앱이 남기는 Firebase 이벤트 (Phase 3 판정용)

`paywall_view`(trigger, limit_type, logged_in, credits30_variant, experiment_arm) → `purchase_start` → `purchase_complete`(sku, credits, value, currency, placement; 서버 검증 후) / `purchase_failed`,
`login`(new_user), `hd_upgrade_click`, `feature_unlock`, `salon_intent_declared`, `install_referrer`, `app_link_open`, `push_sent` / `push_open`
