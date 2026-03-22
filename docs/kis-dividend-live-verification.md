# KIS Dividend Live Verification

## 목적

- KRX 배당 enrichment가 실제 KIS credential로 동작하는지 검증한다.
- `KisDomesticDividendSyncProvider`의 필드 alias가 live payload와 맞는지 확인한다.
- 문제가 생겼을 때 다시 재현할 수 있는 절차를 남긴다.

## 검증 환경

- 앱 실행 포트: `8081`
- DB: 임시 Postgres DB `folo_livecheck`
- profile: `local`
- 설정:
  - `KIS_MARKET_DATA_ENABLED=true`
  - `KIS_DIVIDEND_ENABLED=true`
  - `MARKET_DATA_SYNC_ENABLED=true`
  - `MARKET_DATA_SYNC_RUN_ON_STARTUP=false`
  - `OPS_TRIGGER_SECRET=local-stock-enrichment-secret`

기존 `folo` DB는 Flyway 이력이 달라 checksum mismatch가 날 수 있으므로,
live 검증은 별도 DB에서 돌리는 것을 권장한다.

## 검증 절차

1. 임시 DB `folo_livecheck`를 만든다.
2. worktree 서버를 임시 DB 기준으로 `8081`에서 기동한다.
3. `stock_symbols`에 KRX 심볼 `005930`을 넣는다.
4. 아래 internal endpoint를 호출한다.

```http
POST /api/internal/stock-enrichment/dividends/sync
X-Internal-Trigger-Secret: local-stock-enrichment-secret
Content-Type: application/json

{
  "stockSymbolIds": [1]
}
```

## 실제 확인 결과

- `stock_symbol_sync_runs`
  - `provider=KIS`
  - `market=KRX`
  - `sync_scope=DIVIDEND`
  - `status=COMPLETED`
  - `fetched_count=12`
  - `upserted_count=12`
- `stock_dividend_events`
  - `record_date` 적재 확인
  - `per_sto_divi_amt` 적재 확인
  - `divi_kind` 적재 확인
  - `event_type=CASH` 정상 판정
- `stock_symbols`
  - `annual_dividend_yield=2.2480`
  - `dividend_months_csv=3,6,9,12`

즉 trailing yield와 dividend month 계산에 필요한 핵심 필드는
실 응답 기준으로 검증됐다.

## 남은 확인 포인트

- 이번 검증에서는 `pay_date`가 비어 있었다.
- KIS historical payload가 실제로 값을 비우는 케이스인지,
  다른 필드명으로 내려주는지 추가 확인이 필요하다.
- 현재 provider에는 아래 alias fallback이 들어가 있다.
  - `divi_pay_dt`
  - `stk_div_pay_dt`
  - `odd_pay_dt`

## raw payload debug 경로

live payload를 다시 확인해야 하면 아래 internal endpoint를 사용한다.

```http
POST /api/internal/stock-enrichment/dividends/debug/kis
X-Internal-Trigger-Secret: local-stock-enrichment-secret
Content-Type: application/json

{
  "ticker": "005930",
  "fromDate": "2023-01-01"
}
```

동작:

- KIS raw JSON 응답을 `/tmp/folo-debug/kis-dividend/*.json`에 저장
- 응답 body에는 저장 경로와 key 요약만 반환
- token, app key, app secret은 저장하지 않음

## 참고

- KIS 포털:
  [KIS Open API 포털](https://apiportal.koreainvestment.com/intro)
- 공식 예제:
  [koreainvestment/open-trading-api](https://github.com/koreainvestment/open-trading-api)
