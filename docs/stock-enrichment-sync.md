# Stock Enrichment Sync

## 목적

- Polygon 및 KIS 기반 배당/메타데이터 enrichment를 실제로 실행 가능하게 한다.
- nightly scheduler와 수동 trigger를 함께 제공한다.

## 실행 조건

- `integration.market-data.enabled=true`
- 배당:
  - `POLYGON_DIVIDEND_ENABLED=true`
  - KRX 배당 일정 수집까지 켜려면 `KIS_DIVIDEND_ENABLED=true`
- 메타데이터:
  - `POLYGON_METADATA_ENABLED=true`
- 공통:
  - `POLYGON_API_KEY`
  - KIS 배당 enrichment는 `KIS_MARKET_DATA_ENABLED=true`,
    `KIS_APP_KEY`, `KIS_APP_SECRET`가 추가로 필요하다.

## 스케줄

- 배당 enrichment
  - `integration.market-data.dividend-cron`
  - 기본값: `0 30 4 * * *`
- 메타데이터 enrichment
  - `integration.market-data.metadata-cron`
  - 기본값: `0 0 5 * * *`

기본 zone:

- `integration.market-data.zone`
- 기본값: `Asia/Seoul`

## startup sync

- `integration.market-data.run-on-startup=true`이면 앱 기동 후 아래 순서로 실행한다.
  - metadata enrichment
  - dividend enrichment

한 서비스가 실패해도 다른 서비스와 애플리케이션 startup은 계속 진행한다.

## 수동 trigger endpoint

내부 시크릿 헤더가 있어야 호출 가능하다.

- header: `X-Internal-Trigger-Secret`
- config: `app.ops.trigger-secret`

### 배당 enrichment

`POST /api/internal/stock-enrichment/dividends/sync`

body 생략 시:

- priority symbol 대상 sync 실행

body 예시:

```json
{
  "stockSymbolIds": [11, 22, 33]
}
```

### 메타데이터 enrichment

`POST /api/internal/stock-enrichment/metadata/sync`

body 규칙은 배당 endpoint와 동일하다.

### KIS raw payload debug

`POST /api/internal/stock-enrichment/dividends/debug/kis`

목적:

- live KIS 배당 일정 응답의 실제 필드명을 점검한다.
- 응답 본문만 `/tmp/folo-debug/kis-dividend/*.json`에 저장한다.
- access token, app key, app secret 같은 인증값은 저장하지 않는다.

body 예시:

```json
{
  "ticker": "005930",
  "fromDate": "2023-01-01",
  "toDate": "2026-03-22",
  "highGb": "0"
}
```

또는:

```json
{
  "stockSymbolId": 1
}
```

응답에는 저장 경로, row 개수, top-level key, 첫 row key 요약만 넣고
raw payload 전체는 파일에 남긴다.

## 응답 예시

```json
{
  "success": true,
  "data": {
    "scope": "DIVIDEND",
    "mode": "PRIORITY",
    "requestedCount": 0
  },
  "message": "배당 enrichment sync가 실행되었습니다."
}
```

## 운영 메모

- endpoint는 내부 운영용이다.
- `app.ops.trigger-secret`가 비어 있으면 endpoint는 `404`로 동작한다.
- wrong secret은 `403`으로 처리된다.
