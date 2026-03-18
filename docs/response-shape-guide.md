# FOLO Response Shape Guide

프론트에서 가장 먼저 맞춰야 하는 건 개별 DTO보다 공통 envelope다.

## Success Envelope

```json
{
  "success": true,
  "data": {},
  "message": "요청이 성공했습니다.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

## Error Envelope

```json
{
  "success": false,
  "data": null,
  "message": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청값이 올바르지 않습니다."
  },
  "timestamp": "2026-03-18T13:25:08Z"
}
```

## Key Response Examples

### `POST /auth/signup`

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "nickname": "foloUser",
    "email": "folo@example.com",
    "verificationRequired": true
  },
  "message": "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

### `POST /auth/email/confirm`

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "nickname": "foloUser",
    "email": "folo@example.com",
    "profileImage": null,
    "accessToken": "eyJ...",
    "refreshToken": "eyJ..."
  },
  "message": "이메일 인증이 완료되었습니다.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

### `POST /portfolio/import/csv`

```json
{
  "success": true,
  "data": {
    "importJobId": 11,
    "parsedTrades": 1,
    "failedTrades": 0,
    "preview": [
      {
        "importResultId": 101,
        "ticker": "AAPL",
        "name": "Apple Inc.",
        "market": "NASDAQ",
        "tradeType": "BUY",
        "quantity": 2,
        "price": 180,
        "tradedAt": "2025-03-12T10:15:00",
        "valid": true,
        "errorMessage": null,
        "selected": true
      }
    ]
  },
  "message": "CSV 파싱이 완료되었습니다.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

### `POST /portfolio/import/confirm`

```json
{
  "success": true,
  "data": {
    "savedTrades": 1,
    "confirmedImportResultIds": [101],
    "tradeIds": [55]
  },
  "message": "1건의 거래 기록이 저장되었습니다.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

### `POST /reminders`

```json
{
  "success": true,
  "data": {
    "reminderId": 7,
    "ticker": "005930",
    "name": "삼성전자",
    "amount": 300000,
    "dayOfMonth": 5,
    "isActive": true,
    "nextReminderDate": "2026-04-05"
  },
  "message": "리마인더가 생성되었습니다.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

### `POST /portfolio/sync`

```json
{
  "success": true,
  "data": {
    "syncedHoldings": 2,
    "syncedTrades": 3,
    "syncedAt": "2026-03-18T13:25:08.123"
  },
  "message": "포트폴리오가 동기화되었습니다.",
  "timestamp": "2026-03-18T13:25:08Z"
}
```

## Frontend Notes

- import preview 확정은 `tradeIds`가 아니라 `importResultIds`를 보낸다.
- OCR도 같은 confirm endpoint를 재사용한다.
- 중복 확정 시 `409`와 `error.code=DUPLICATE`를 반환한다.
- `timestamp`는 UTC offset이 포함된 ISO-8601 문자열이다.
