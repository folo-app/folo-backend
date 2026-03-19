# Stock Master Sync

## 목적

- `stock_symbols`를 수동 seed에서 운영 가능한 종목 마스터로 확장한다.
- 공급자별 동기화 이력과 실패 사유를 저장한다.
- 프론트 검색(`/stocks/search`)과 거래 생성(`/trades`)이 같은 심볼 마스터를 사용하도록 유지한다.

## 현재 구조

- 미국장: `PolygonStockMasterSyncProvider`
  - `NASDAQ`, `NYSE`, `AMEX` 지원
  - Polygon `reference/tickers`를 페이지네이션하며 `stock_symbols`를 upsert
- 국장: `KisStockMasterFileSyncProvider`
  - `KRX` 지원
  - KIS 종목 마스터를 CSV로 정규화한 파일 또는 URL을 읽어 `stock_symbols`를 upsert

## 2026-03-19 기준 구현 상태

- `V4__add_stock_master_sync_metadata.sql`로 종목 마스터 메타데이터와 sync run 테이블이 추가됐다.
- `@EnableScheduling`과 `StockMasterSyncScheduler`가 들어가 있어 nightly master sync를 실행할 수 있다.
- Polygon은 live REST provider로 연결되어 있고 `POLYGON_API_KEY`가 있으면 바로 사용 가능하다.
- KRX는 아직 live REST provider가 아니라 file/url ingest 방식이다. 즉 KIS 앱키/시크릿만 받아도 바로 sync 되는 상태는 아니다.

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

- `MARKET_DATA_SYNC_ENABLED`
- `MARKET_DATA_MASTER_CRON`
- `MARKET_DATA_SYNC_ZONE`
- `MARKET_DATA_BATCH_SIZE`
- `POLYGON_MASTER_ENABLED`
- `POLYGON_API_KEY`
- `POLYGON_BASE_URL`
- `KIS_MASTER_ENABLED`
- `KIS_MASTER_FILE_URL`

## 운영 메모

- 지금 단계에서는 종목 마스터 sync만 넣었고, 시세 snapshot 자동 갱신 잡은 별도로 추가해야 한다.
- 미국장은 Polygon API key가 있으면 바로 돌릴 수 있다.
- 국장은 두 가지 선택지가 있다.
  - 빠른 적용: 종목정보파일을 CSV로 정규화해서 `KIS_MASTER_FILE_URL`에 연결
  - 정식 연동: KIS 앱키/시크릿 기반 토큰 발급, 종목 마스터 다운로드, 파일 파싱까지 수행하는 live provider 추가
- 따라서 운영 준비물은 `Polygon API key`는 거의 확정이고, KRX를 live로 자동화하려면 `KIS 앱키/시크릿`과 추가 구현이 함께 필요하다.
