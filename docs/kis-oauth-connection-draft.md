# KIS OAuth Connection Draft

## 목적

- 유저가 `KIS app key / secret`를 직접 입력하지 않도록 한다.
- 서비스 공용 KIS 제휴 키로 OAuth 연결을 시작하고, 유저별 연결 상태와 토큰만 저장한다.
- 포트폴리오 동기화는 기존 `POST /portfolio/sync`를 유지하되, 연결된 계좌 기준으로 실행한다.

## 현재 상태

- 현재 구현된 경로는 `PATCH /users/me/kis-key`와 `POST /portfolio/sync`다.
- 이 구조는 개발/검증에는 빠르지만, 실제 배포 UX로는 부적합하다.
- 프론트는 이제 `KIS 연결 준비중` 구조로 전환하고, CSV/OCR 가져오기를 메인 온보딩으로 사용한다.

## 제안 엔드포인트

### 1. 연결 시작

- `POST /integrations/kis/connect/start`
- 인증 필요: `yes`
- 역할:
  - 백엔드가 KIS OAuth 인가 URL을 생성한다.
  - CSRF 방지용 `state`를 발급한다.
  - 프론트는 `authorizationUrl`을 브라우저/웹뷰로 연다.

예시 응답:

```json
{
  "success": true,
  "data": {
    "authorizationUrl": "https://.../oauth2/authorize?...",
    "state": "kis_oauth_state_token",
    "expiresAt": "2026-03-19T15:00:00+09:00"
  },
  "message": "KIS 연결을 시작합니다."
}
```

### 2. OAuth callback

- `GET /integrations/kis/connect/callback`
- 인증 필요: `no`
- query:
  - `code`
  - `state`
  - `error` optional
- 역할:
  - KIS callback을 수신한다.
  - 백엔드가 code를 access/refresh token으로 교환한다.
  - 연결된 계좌 식별 정보와 토큰을 저장한다.
  - 프론트 deep link 또는 완료 페이지로 리다이렉트한다.

### 3. 연결 상태 조회

- `GET /integrations/kis/connect/status`
- 인증 필요: `yes`
- 역할:
  - 현재 유저가 KIS에 연결되어 있는지 확인한다.
  - 마지막 동기화 시각과 계좌 마스킹 정보를 내려준다.

예시 응답:

```json
{
  "success": true,
  "data": {
    "connected": true,
    "broker": "KIS",
    "accountMask": "123-45****-01",
    "connectedAt": "2026-03-19T14:30:00+09:00",
    "lastSyncedAt": "2026-03-19T14:35:00+09:00"
  },
  "message": "요청이 성공했습니다."
}
```

### 4. 연결 해제

- `DELETE /integrations/kis/connect`
- 인증 필요: `yes`
- 역할:
  - 저장된 KIS 연결 토큰과 계좌 식별 정보를 제거한다.
  - 이후 `POST /portfolio/sync`는 다시 연결 전까지 차단한다.

### 5. 포트폴리오 동기화

- 기존 `POST /portfolio/sync` 유지
- 변경점:
  - `users.kisAppKeyEncrypted / kisAppSecretEncrypted` 대신
    `kis_connection.access_token / refresh_token / account metadata`를 사용한다.
  - 연결 상태가 없으면 `KIS_NOT_CONNECTED` 계열 에러를 반환한다.

## 저장 모델 초안

신규 테이블 예시:

- `broker_connections`
  - `id`
  - `user_id`
  - `broker` (`KIS`)
  - `connection_status` (`PENDING`, `CONNECTED`, `FAILED`, `DISCONNECTED`)
  - `access_token_encrypted`
  - `refresh_token_encrypted`
  - `token_expires_at`
  - `account_mask`
  - `external_account_id`
  - `connected_at`
  - `last_synced_at`
  - `created_at`
  - `updated_at`

- `oauth_connect_sessions`
  - `id`
  - `user_id`
  - `broker`
  - `state`
  - `redirect_uri`
  - `expires_at`
  - `consumed_at`
  - `created_at`

## 에러 초안

- `KIS_NOT_CONNECTED`
- `KIS_OAUTH_STATE_INVALID`
- `KIS_OAUTH_EXPIRED`
- `KIS_TOKEN_EXCHANGE_FAILED`
- `KIS_CONNECTION_ALREADY_EXISTS`

## 마이그레이션 방향

1. 프론트에서 `PATCH /users/me/kis-key` 사용 제거
2. `KIS 연결하기` 버튼은 `POST /integrations/kis/connect/start` 호출
3. callback 및 연결 상태 조회 구현
4. `POST /portfolio/sync`를 연결 토큰 기반으로 변경
5. 기존 `users.me.kis-key` 경로는 deprecated 처리 후 제거
