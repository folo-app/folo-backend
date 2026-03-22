# Stock Master Sync

## 목적

- `stock_symbols`를 수동 seed에서 운영 가능한 종목 마스터로 확장한다.
- 공급자별 동기화 이력과 실패 사유를 저장한다.
- 프론트 검색(`/stocks/search`)과 거래 생성(`/trades`)이 같은 심볼 마스터를 사용하도록 유지한다.

## 현재 구조

- 종목 마스터: `KisStockMasterFileSyncProvider`
  - `KRX`, `NASDAQ`, `NYSE`, `AMEX` 지원
  - KIS 종목 마스터를 CSV로 정규화한 국내/해외 파일 또는 URL을 읽어 `stock_symbols`를 upsert
- 시세: `KisQuoteService`
  - 국내는 `주식현재가 시세`
  - 해외는 `해외주식 복수종목 시세조회`
  - `/stocks/search`, `/stocks/discover`, `/stocks/{ticker}/price`가 KIS 시세를 우선 사용
- 로고: `StockBrandingService`
  - Twelve Data logo API를 1순위 로고 공급자로 사용
  - 미국 종목은 Twelve Data 우선, 실패 시 Polygon branding fallback
  - 국내 종목은 Twelve Data 시도 후 실패하면 initials fallback
  - KIS는 회사 로고를 제공하지 않으므로 검색/시세와 로고 공급자를 분리한다.

## 2026-03-19 기준 구현 상태

- `V4__add_stock_master_sync_metadata.sql`로 종목 마스터 메타데이터와 sync run 테이블이 추가됐다.
- `@EnableScheduling`과 `StockMasterSyncScheduler`가 들어가 있어 nightly master sync를 실행할 수 있다.
- KIS 앱키/시크릿이 있으면 live quote가 가능하다.
- 검색 가능한 전체 종목 universe는 KIS 종목정보파일을 미리 sync 해 둬야 한다.
- Polygon master sync는 더 이상 기본 경로가 아니며, 로고 전용 보조 소스로만 유지한다.

## 스케줄

- `integration.market-data.enabled=true`일 때만 동작
- 기본 cron: `0 0 4 * * *`
- 기본 zone: `Asia/Seoul`

## 스키마 변경

### `stock_symbols`

- `primary_exchange_code`
- `currency_code`
- `source_provider`
- `source_identifier`
- `last_master_synced_at`

### `stock_symbol_sync_runs`

- provider
- market
- sync_scope
- status
- fetched_count
- upserted_count
- deactivated_count
- request_cursor
- next_cursor
- error_message
- started_at / completed_at

## 필요한 환경 변수

- `KIS_MARKET_DATA_ENABLED`
- `KIS_DIVIDEND_ENABLED`
- `MARKET_DATA_SYNC_ENABLED`
- `MARKET_DATA_SYNC_RUN_ON_STARTUP`
- `MARKET_DATA_MASTER_CRON`
- `MARKET_DATA_SYNC_ZONE`
- `MARKET_DATA_BATCH_SIZE`
- `KIS_API_BASE_URL`
- `KIS_APP_KEY`
- `KIS_APP_SECRET`
- `KIS_DOMESTIC_MASTER_FILE_URL`
- `KIS_OVERSEAS_MASTER_FILE_URL`
- `TWELVE_DATA_LOGO_ENABLED`
- `TWELVE_DATA_API_KEY`
- `TWELVE_DATA_BASE_URL`
- `POLYGON_LOGO_ENABLED`
- `POLYGON_API_KEY`
- `POLYGON_BASE_URL`

## 운영 메모

- KIS 검색 API는 “종목코드를 알 때 상세조회” 성격이 강하다. 따라서 앱 검색 UX를 위해서는 국내/해외 종목정보파일을 먼저 DB에 적재해야 한다.
- 국내 파일은 `ticker,name,...` 형태의 정규화 CSV면 충분하다.
  KRX metadata enrichment까지 쓰려면 `sectorName`, `industryName`,
  `sourcePayloadVersion` 컬럼을 함께 포함한 정규화 CSV를 권장한다.
- 해외 파일은 `market` 또는 `primaryExchangeCode`가 반드시 있어야 `NASDAQ/NYSE/AMEX`를 구분할 수 있다.
- KIS 시세는 앱키/시크릿만으로 live 조회가 가능하고, 조회 결과는 `price_snapshots`에도 upsert 된다.
- Twelve Data는 `TWELVE_DATA_LOGO_ENABLED=true`일 때 로고 조회에 사용한다.
- 미국 종목은 Twelve Data 실패 시 `POLYGON_LOGO_ENABLED=true`일 경우 Polygon fallback이 동작한다.
- KIS만으로는 국내/해외 회사 로고를 전부 해결할 수 없다.

## 관련 문서

- 국내 원본 종목정보파일 정규화:
  `docs/kis-domestic-master-normalization.md`
- 미국장 마스터 CSV 스키마와 생성 절차:
  `docs/us-master-schema.md`
- 국내 CSV 예시:
  `docs/examples/kis-stock-master.sample.csv`
- 미국장 CSV 예시:
  `docs/examples/kis-overseas-master.sample.csv`
