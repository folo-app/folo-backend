# Phase A 상세 구현 계획

## 목적

Phase A의 목표는 `stock_symbols`에 추가된 배당 관련 필드를 실제 데이터로 채우고,
포트폴리오 API의 `monthlyDividendForecasts`가 더 이상 CSV 우연값이 아니라
배당 이벤트 이력 기반 데이터 위에서 계산되게 만드는 것이다.

현재 기준으로 Phase A는 "배당 데이터 현실화"에 집중한다.
섹터 enrichment는 구조상 연결할 수는 있지만, 실제 외부 API 연동까지 포함하면
범위가 커지므로 Phase B로 미룬다.

## 현재 상태 요약

- `stock_symbols`에 `sector_name`, `annual_dividend_yield`,
  `dividend_months_csv` 컬럼이 추가되는 방향으로 변경 중이다.
- 포트폴리오 응답은 이미 `sectorAllocations`, `monthlyDividendForecasts`,
  보유 종목별 `sectorName`, `annualDividendYield`, `dividendMonths`를 노출한다.
- KIS provider는 CSV에 컬럼이 있으면 읽는 수준이다.
- Polygon provider는 아직 비활성화되어 있고 배당/섹터 필드는 전부 `null`이다.

즉 Phase A의 핵심은 다음 두 가지다.

1. 미국 종목 배당 이벤트를 실제 외부 데이터로 적재
2. 그 이벤트로 `annualDividendYield`, `dividendMonthsCsv`를 파생

## Phase A 범위

### 포함

- 미국 종목 대상 Polygon 배당 이벤트 수집
- `stock_dividend_events` 테이블 도입
- 최근 배당 이력 기반 `dividendMonthsCsv` 계산
- 최근 12개월 배당 기반 `annualDividendYield` 계산
- 포트폴리오 API에서 계산 결과 사용
- sync 이력 및 실패 처리
- 단위/통합 테스트

### 제외

- KRX 배당 일정 실연동
- 미국 종목 sector/industry enrichment
- 새로운 종목 상세 API 확장
- 배당 지급 금액 UI 상세 히스토리 노출
- 전체 종목 full backfill 운영 자동화

## 설계 결정

### 1. 기본 마스터 sync와 배당 enrichment sync를 분리한다

이유:

- 종목 universe 수집과 배당 이벤트 수집의 속도와 quota 특성이 다르다.
- 배당 enrichment 실패가 심볼 검색과 거래 생성에 영향을 주면 안 된다.
- 현재 `StockMasterSyncService`는 심볼 upsert 책임이 명확해서 유지 가치가 있다.

### 2. 배당 원시 이벤트를 먼저 저장하고, 파생값은 그 뒤 계산한다

이유:

- `dividendMonthsCsv`는 원시 이벤트 없이는 신뢰성 있게 계산할 수 없다.
- 나중에 API를 확장할 때 동일한 원시 테이블을 재사용할 수 있다.
- 공급자 데이터가 바뀌어도 파생 규칙만 바꾸면 된다.

### 3. Phase A의 대상 시장은 미국장으로 제한한다

대상:

- `NASDAQ`
- `NYSE`
- `AMEX`

이유:

- Polygon dividends가 가장 명확한 수집 경로다.
- 현재 `dividendMonths` 수요는 미국 배당주/ETF에서 즉시 체감 효과가 크다.
- KRX는 KIS 배당 일정 API 스펙 정리와 예외 케이스 파악이 더 필요하다.

## 작업 스트림

## Stream 1. DB 스키마

### 목표

배당 이벤트와 enrichment 메타를 저장할 최소 스키마를 도입한다.

### 작업

1. 신규 마이그레이션 추가

권장 파일:

- `V7__add_stock_dividend_events.sql`

### 신규 테이블 `stock_dividend_events`

- `id BIGSERIAL PRIMARY KEY`
- `stock_symbol_id BIGINT NOT NULL`
- `provider VARCHAR(30) NOT NULL`
- `source_event_id VARCHAR(120) NULL`
- `event_type VARCHAR(30) NOT NULL`
- `declared_date DATE NULL`
- `ex_dividend_date DATE NULL`
- `record_date DATE NULL`
- `pay_date DATE NULL`
- `cash_amount NUMERIC(18,6) NULL`
- `currency_code VARCHAR(10) NULL`
- `frequency_raw VARCHAR(30) NULL`
- `created_at TIMESTAMP NOT NULL`
- `updated_at TIMESTAMP NOT NULL`

### 인덱스/제약

- `idx_dividend_events_symbol_pay_date(stock_symbol_id, pay_date)`
- `idx_dividend_events_symbol_ex_date(stock_symbol_id, ex_dividend_date)`
- `idx_dividend_events_provider_source(provider, source_event_id)`
- unique:
  - 1순위: `(provider, source_event_id)`
  - fallback: `(stock_symbol_id, provider, ex_dividend_date, pay_date, cash_amount)`

### 기존 테이블 재사용

- `stock_symbols.annual_dividend_yield`
- `stock_symbols.dividend_months_csv`

Phase A에서는 `sector_name`은 그대로 두고, 실제 enrichment 대상에서 제외한다.

### 완료 기준

- 신규 migration이 PostgreSQL과 테스트 환경에서 모두 통과
- unique/index 설계가 중복 적재를 막음

## Stream 2. 도메인 모델 및 저장소

### 목표

배당 이벤트 적재와 파생값 계산에 필요한 도메인 구조를 추가한다.

### 작업

1. 엔티티 추가

권장 파일:

- `src/main/java/com/folo/stock/StockDividendEvent.java`

필드:

- `stockSymbol`
- `provider`
- `sourceEventId`
- `eventType`
- `declaredDate`
- `exDividendDate`
- `recordDate`
- `payDate`
- `cashAmount`
- `currencyCode`
- `frequencyRaw`

2. repository 추가

권장 파일:

- `StockDividendEventRepository.java`

필수 메서드:

- `findAllByStockSymbolIdAndPayDateBetween(...)`
- `findAllByStockSymbolIdAndExDividendDateBetween(...)`
- `deleteAllByStockSymbolIdAndProvider(...)` 또는
  upsert 기반 충돌 처리 메서드
- `findDistinctStockSymbolIdsByProvider(...)`

3. provider용 DTO 추가

권장 파일:

- `DividendEventRecord.java`

역할:

- 외부 API 응답을 엔티티에 바로 매핑하지 않고 중간 record로 정규화

### 완료 기준

- 이벤트 저장/조회가 JPA 기준으로 동작
- 동일 이벤트가 두 번 적재되지 않음

## Stream 3. Polygon 배당 수집기

### 목표

미국 종목에 대해 Polygon dividends 데이터를 가져오는 전용 enrichment provider를 만든다.

### 작업

1. 신규 provider 추가

권장 파일:

- `PolygonDividendSyncProvider.java`

기능:

- `StockDataProvider.POLYGON` 사용
- market이 `NASDAQ`, `NYSE`, `AMEX`인 경우만 지원
- Polygon dividends endpoint 호출
- 페이지네이션 처리
- `DividendEventRecord` 리스트 반환

2. 요청 전략

권장 기본 전략:

- 개별 종목 단위 호출
- 최근 36개월 범위 조회
- `pay_date` 또는 `ex_dividend_date` 기준 필터

Phase A에서는 전체 미국장 full sync보다 아래 우선순위가 낫다.

1. 현재 보유 종목
2. 최근 거래 종목
3. 검색 상위 종목

3. 설정 추가

권장 프로퍼티:

- `integration.market-data.dividend-enabled`
- `integration.market-data.dividend-cron`
- `integration.market-data.dividend-lookback-months`
- `integration.market-data.polygon.dividend-enabled`

4. 에러 처리

- 종목 단위 실패는 전체 배치 중단 대신 실패 로그 후 다음 종목 진행
- 429/5xx는 backoff 또는 다음 배치로 미룸

### 완료 기준

- 테스트용 stub 응답으로 이벤트 적재 확인
- 실제 API key가 있을 때 최소 1종목의 이벤트 수집 확인

## Stream 4. 배당 파생 계산기

### 목표

저장된 배당 이벤트에서 `dividendMonthsCsv`와 `annualDividendYield`를 계산한다.

### 작업

1. 신규 서비스 추가

권장 파일:

- `DividendAnalyticsService.java`

핵심 메서드:

- `refreshAnalytics(Long stockSymbolId)`
- `refreshAnalyticsBatch(List<Long> stockSymbolIds)`

2. `dividendMonthsCsv` 계산 규칙

- 조회 범위: 최근 36개월
- 기준일: `pay_date` 우선, 없으면 `ex_dividend_date`
- 월별 출현 횟수 계산
- 2회 이상 반복된 월만 채택
- 결과를 오름차순 CSV로 저장
- 결과가 없으면 `null`

3. `annualDividendYield` 계산 규칙

- 조회 범위: 최근 12개월
- `cash_amount` 합산
- 현재 가격은 `price_snapshots.current_price` 우선
- snapshot이 없으면 `null`
- 계산식:
  - `trailing_12m_dividend / current_price * 100`
- scale은 현재 컬럼에 맞춰 `4`

4. ETF/보통주 공통 처리

- ETF도 같은 규칙으로 계산
- 비정상치 방어:
  - 현재가 0 이하 -> `null`
  - 배당 합계 0 이하 -> `null`
  - yield 100 초과 -> 경고 후 `null` 또는 capped 정책 검토

### 완료 기준

- 이벤트 이력만 있으면 `stock_symbols` 파생 필드가 업데이트됨
- 포트폴리오 응답에서 `dividendMonths`와 `annualDividendYield`가 실제 값으로 노출됨

## Stream 5. enrichment orchestration

### 목표

누가, 언제, 어떤 종목을 배당 enrichment할지 정한다.

### 작업

1. 신규 서비스 추가

권장 파일:

- `StockDividendEnrichmentService.java`

책임:

- 대상 심볼 선정
- provider 호출
- raw event upsert
- analytics refresh

2. 대상 심볼 선정 규칙

Phase A 기본:

- `HoldingRepository`에서 현재 보유 심볼
- `TradeRepository`에서 최근 거래 심볼
- 시장 필터는 미국장만

3. 실행 트리거

우선순위:

- 수동 실행용 서비스 메서드
- 스케줄러
- 필요 시 startup에서 비활성

4. 기존 scheduler와 분리

권장:

- `StockMasterSyncScheduler`는 그대로 둠
- 신규 `StockDividendSyncScheduler` 추가

이유:

- 마스터 sync와 enrichment sync는 실패 성격이 다르다.

### 완료 기준

- 특정 심볼 리스트에 대해 end-to-end 실행 가능
- 한 종목 실패가 배치 전체를 죽이지 않음

## Stream 6. API 영향 정리

### 목표

기존 API shape는 유지하되, 의미를 명확히 한다.

### 작업

1. 포트폴리오 문서 정리

- `monthlyDividendForecasts`는 예측치라고 명시
- `dividendMonths`는 과거 패턴 기반 예상 월이라고 명시

2. 필요 시 stock detail 응답 확장 준비

Phase A에서는 구현하지 않더라도 아래 shape는 설계만 확정한다.

- `sectorName`
- `annualDividendYield`
- `dividendMonths`
- `dividendMetadataStatus`

3. OpenAPI/문서 업데이트 범위 정의

- 포트폴리오 응답 필드 설명 보강
- 미국 종목에 한해 배당 데이터가 먼저 채워진다는 점 명시

### 완료 기준

- 프론트가 `null`과 forecast 의미를 오해하지 않음

## Stream 7. 테스트

### 목표

파생 계산과 적재 중복 방지를 신뢰할 수 있게 만든다.

### 단위 테스트

1. `DividendAnalyticsServiceTest`

- 분기 배당 패턴 -> `3,6,9,12`
- 월 배당 패턴 -> `1..12`
- 불규칙 배당 -> `null`
- snapshot 없음 -> `annualDividendYield = null`

2. `PolygonDividendSyncProviderTest`

- 페이지네이션 처리
- event mapping 정확성
- null field 방어

3. `StockDividendEnrichmentServiceTest`

- 동일 이벤트 재실행 시 중복 없음
- 일부 종목 실패 시 나머지 종목 진행

### 통합 테스트

1. migration + repository 테스트
2. 이벤트 적재 후 analytics 반영 테스트
3. portfolio API 응답에 배당 데이터 노출 테스트

### 완료 기준

- 핵심 파생 규칙 테스트 커버
- 중복 적재와 null 처리 회귀 방지

## Stream 8. 운영 및 롤아웃

### 단계 1. 개발 환경

- feature flag off 기본
- mock/stub 응답으로 end-to-end 검증

### 단계 2. 제한된 실제 호출

- 실 API key로 3~5개 미국 배당주만 수동 실행
- 적재 데이터와 포트폴리오 응답 검증

### 단계 3. 점진 확대

- 보유 종목 전체
- 최근 거래 종목 전체

### 모니터링 포인트

- 호출 수 / 실패율
- 종목당 평균 적재 이벤트 수
- yield null 비율
- dividendMonths null 비율

## 권장 구현 순서

1. `V7__add_stock_dividend_events.sql`
2. `StockDividendEvent` / repository
3. `DividendAnalyticsService`
4. `PolygonDividendSyncProvider`
5. `StockDividendEnrichmentService`
6. scheduler / property wiring
7. 포트폴리오 및 문서 검증
8. 테스트 보강

이 순서가 좋은 이유는 파생 계산기를 먼저 고정하면,
provider 연결 이후 데이터가 들어왔을 때 결과를 바로 검증할 수 있기 때문이다.

## 커밋 분리 권장안

1. `feat: 배당 이벤트 저장 스키마를 추가한다`
2. `feat: 배당 analytics 계산기를 추가한다`
3. `feat: Polygon 배당 enrichment를 추가한다`
4. `test: 배당 enrichment 회귀 테스트를 보강한다`
5. `docs: 배당 forecast 의미를 문서화한다`

## 리뷰 체크포인트

### Checkpoint 1

- 신규 테이블 스키마와 unique key 검토

### Checkpoint 2

- `dividendMonthsCsv` 파생 규칙 검토

### Checkpoint 3

- Polygon 응답 매핑과 rate limit 전략 검토

### Checkpoint 4

- 포트폴리오 API 의미와 문서 검토

## 오픈 이슈

1. `annualDividendYield`를 `stock_symbols`에 저장할지,
   계산 캐시 테이블로 분리할지
2. `dividendMonthsCsv`의 최소 반복 횟수를 2회로 둘지 3회로 둘지
3. 미국 REIT/ETF처럼 특수 배당 패턴을 일반 규칙으로 충분히 설명할 수 있는지
4. 향후 KRX Phase C에서 국내 배당 일정과 같은 필드 모델을 유지할지

## 제안

구현은 아래 범위로 자르는 것이 가장 안전하다.

- Phase A-1:
  - 스키마
  - 엔티티/리포지토리
  - 파생 계산기
- Phase A-2:
  - Polygon provider
  - enrichment orchestration
- Phase A-3:
  - 스케줄러
  - 문서
  - 회귀 테스트

이렇게 가면 각 단계마다 데이터 모델, 계산 규칙, 외부 연동을 분리해서 검토할 수 있다.
