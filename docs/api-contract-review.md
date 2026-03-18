# FOLO API Contract Review

프론트 검토 기준으로 현재 구현과 원래 PDF 명세 사이의 중요한 차이를 정리한 문서다.

## 기준 문서

- 런타임 OpenAPI: `GET /api/v3/api-docs`
- 저장된 OpenAPI 산출물: `docs/openapi.json`
- Postman 컬렉션: `docs/Folo.postman_collection.json`
- 응답 shape 가이드: `docs/response-shape-guide.md`
- Swagger UI: `GET /api/swagger-ui.html`

## 공통 응답 envelope

- 성공 응답은 항상 `success=true`, `data`, `message`, `timestamp`를 포함한다.
- 실패 응답은 항상 `success=false`, `error.code`, `error.message`, `timestamp`를 포함한다.
- 프론트는 HTTP status만 보지 말고 `success`와 `error.code`도 함께 처리하는 것이 안전하다.

## 현재 구현 기준 핵심 변경점

### 인증

- `POST /auth/signup`
  - 원래 PDF: access/refresh token 즉시 발급
  - 현재 구현: `verificationRequired=true`만 반환, 정상 JWT는 발급하지 않음
  - 이유: 이메일 인증 완료 전 전체 권한 토큰 발급을 막기 위함

- `POST /auth/email/confirm`
  - 현재 구현에서 최초 JWT 발급 지점

### 거래/보유/포트폴리오

- `trades`가 원본이고 `holdings`, `portfolios`는 projection
- holding 수동 CRUD는 아직 열지 않았음
- `FRIENDS_ONLY`는 맞팔 기준으로 해석

### Import

- `POST /portfolio/import/csv`
  - 현재 구현은 `importJobId`와 `preview[].importResultId`를 반환

- `POST /portfolio/import/confirm`
  - 신규 권장 endpoint
  - request body는 `importResultIds`
  - response는 `savedTrades`, `confirmedImportResultIds`, `tradeIds`를 반환

- `POST /portfolio/import/csv/confirm`
  - legacy compatibility endpoint
  - 내부 동작은 `POST /portfolio/import/confirm`과 동일

- import confirm request
  - 원래 PDF의 `tradeIds`
  - 현재 구현은 `importResultIds`
  - 이유: 아직 trade가 생성되기 전 preview 결과를 확정해야 하기 때문

- import confirm idempotency
  - 같은 `importResultId`를 두 번 확정하면 `409 DUPLICATE`가 반환된다.
  - 이유: 중복 거래 생성 방지

- `POST /portfolio/import/ocr`
  - 현재 구현은 로컬 heuristic OCR stub
  - 파일명 포맷:
    - `TICKER_MARKET_TYPE_QTY_PRICE_yyyyMMddHHmmss.ext`
    - 예: `005930_KRX_BUY_10_74200_20250312101500.png`

### KIS

- `PATCH /users/me/kis-key`
  - 현재 구현: AES-GCM 기반 암호화 저장

- `POST /portfolio/sync`
  - 현재 구현: local stub adapter 기반
  - `KIS_STUB_FILE`이 비어 있으면 신규 trade를 가져오지 않음
  - stub 파일은 `KisSyncTradePayload` 배열 JSON 형식

### Reminder

- `dayOfMonth`는 `1..28`로 확정
- `GET/POST/PATCH/DELETE /reminders` 구현 완료

## 프론트 확인 포인트

- signup 후 바로 홈으로 보내지 말고 이메일 인증 화면으로 이동해야 함
- import confirm 요청 body는 `importResultIds`로 맞춰야 함
- 신규 구현 기준으로 confirm URL은 `/portfolio/import/confirm`을 사용하는 쪽이 맞음
- OCR preview는 실패 시 `parsed=null`, `confidence=0.0`이 올 수 있음
- KIS sync는 현재 stub adapter 기반이므로 실제 외부 연동 전까지는 데모/개발 데이터 중심으로 확인해야 함

## 아직 남은 항목

- Apple/Google 실소셜 로그인
- 실제 KIS Open API 연동
- 실제 OCR 엔진 연동
- 종목 차트 `/stocks/{ticker}/chart`
- Home summary / todos
