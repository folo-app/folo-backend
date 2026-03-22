# FOLO 섹터·배당 데이터 수집 설계안

## 목적

- 종목 상세 및 포트폴리오 분석에서 사용할 `sectorName`, `annualDividendYield`,
  `dividendMonths` 데이터를 운영 가능한 방식으로 수집한다.
- 국내 주식과 미국 주식을 같은 API shape로 노출하되, 원본 데이터의 의미 차이를
  보존한다.
- 현재 구현처럼 공급자 응답에 우연히 컬럼이 있기를 기대하지 않고, 공급자별 수집
  경로와 파생 규칙을 명시한다.

## 배경

현재 백엔드는 `stock_symbols`에 아래 필드를 저장하려고 한다.

- `sector_name`
- `annual_dividend_yield`
- `dividend_months_csv`

현재 구현 상태는 다음과 같다.

- KIS 마스터 파일 sync는 CSV에 해당 컬럼이 있으면 읽는다.
- Polygon sync는 아직 비활성화되어 있고 해당 필드를 `null`로 저장한다.
- 포트폴리오의 월별 배당은 공급자 원본 데이터가 아니라,
  `평가금액 x 연간 배당수익률 / 배당월 개수`로 계산한 forecast다.

즉, API 모델은 생겼지만 운영 기준의 수집 파이프라인은 아직 없다.

## 설계 원칙

1. `stock_symbols`는 조회용 정규화 테이블로 유지한다.
2. 공급자 원본 데이터는 별도 enrichment 단계에서 수집한다.
3. `sectorName`은 공급자마다 의미가 다를 수 있으므로 "표준화된 GICS 섹터"라고
   가정하지 않는다.
4. `annualDividendYield`는 원시값과 계산값을 구분한다.
5. `dividendMonths`는 가능하면 원본 배당 이벤트 이력에서 파생한다.
6. 국내와 미국은 수집 경로를 분리한다.

## 용어 정의

### 1. 원본 값

공급자가 직접 주는 값이다.

- 예: Polygon dividends endpoint의 `cash_amount`, `pay_date`, `frequency`
- 예: KIS 배당 일정 API의 배당 기준일 또는 배당 일정

### 2. 정규화 값

FOLO가 화면과 분석에 맞게 변환한 값이다.

- 예: `sectorName`
- 예: `annualDividendYield`
- 예: `dividendMonthsCsv`

### 3. 파생 값

원본 이벤트나 시세를 이용해 FOLO 내부 규칙으로 계산한 값이다.

- 예: `monthlyDividendForecasts`
- 예: 최근 3년 배당 이벤트를 보고 만든 `3,6,9,12`

## 공급자별 역할 분리

### KRX 종목

- 기본 종목 마스터: KIS 종목정보파일
- 섹터/업종: KIS 국내 종목정보 계열 API 또는 업종 관련 API
- 배당 일정: KIS `예탁원정보(배당일정)` 계열 API

### 미국 종목

- 기본 종목 마스터: KIS 해외 종목정보파일 또는 Polygon reference tickers
- 섹터/산업 분류: Polygon ticker overview/details 계열
- 배당 이벤트: Polygon dividends endpoint

## 외부 공급자 적합성

### KIS

KIS 포털에는 아래 API 카테고리가 노출된다.

- 국내주식 종목정보
  - `상품기본조회`
  - `주식기본조회`
  - `예탁원정보(배당일정)`
- 해외주식 기본시세/분석
  - `해외주식 상품기본정보`
  - `해외주식 업종별코드조회`
  - `해외주식 업종별시세`
  - `해외주식 기간별권리조회`
  - `해외주식 권리종합`

따라서 KIS는 "가능성이 있는 공급자"다. 다만 현재 구조처럼 마스터 CSV 하나만
읽어서 섹터와 배당 정보를 모두 채우는 방식은 안정적이지 않다.

설계 결론:

- KIS는 KRX 배당 일정 수집의 1순위 공급자로 사용한다.
- KIS는 KRX 섹터/업종 보강의 보조 공급자로 사용한다.
- 미국장 섹터/배당의 주 공급자는 KIS보다 Polygon이 더 적합하다.

### Polygon / Massive

Massive 공식 문서 기준으로 다음이 가능하다.

- ticker overview/details 계열에서 회사 reference metadata 조회
- dividends endpoint에서 배당 이벤트 이력 조회

설계 결론:

- 미국 주식 섹터/산업 분류는 Polygon을 1순위로 사용한다.
- 미국 주식 `dividendMonths`는 Polygon dividend history에서 파생한다.
- `annualDividendYield`는 Polygon에서 직접 주는 값이 항상 일정하지 않을 수
  있으므로, 있으면 사용하고 없으면 이벤트 이력 기반 계산 또는 별도 필드로 둔다.

## 현재 구조의 문제점

### 1. KIS 마스터 파일 의존이 과하다

현재 KIS provider는 CSV 컬럼명 후보를 여러 개 시도해
`sectorName`, `annualDividendYield`, `dividendMonths`를 읽는다.

이 방식은 아래 문제가 있다.

- KIS 원본 파일에 해당 컬럼이 없으면 데이터가 항상 비게 된다.
- 같은 컬럼명이라도 의미가 공급자 문서와 다를 수 있다.
- 국내와 해외의 스키마 차이를 코드가 제대로 표현하지 못한다.

### 2. Polygon 구현이 아직 enrichment를 하지 않는다

현재 Polygon provider는 `StockMasterSymbolRecord`에
`sectorName`, `annualDividendYield`, `dividendMonthsCsv`를 전부 `null`로 넣는다.

즉 현재는 "가능한 공급자"일 뿐 "연결된 공급자"는 아니다.

### 3. 월별 배당의 의미가 예측인지 실측인지 불명확하다

현재 포트폴리오 서비스는 보유 평가금액에 연간 배당수익률을 곱한 뒤,
배당 월 수로 균등 분할한다.

이 값은 다음 중 어느 것도 아니다.

- 실제 지급 예정 금액
- 실제 기업 가이던스
- 과거 배당 캘린더 그대로

따라서 API 명세에서 이 필드는 `monthlyDividendForecast`로 명확히 다루는 편이
맞다.

## 권장 아키텍처

### 1단계: 기본 마스터 sync

목적:

- 종목 검색, 거래 생성, 시세 조회를 위한 기본 심볼 마스터 확보

입력:

- KIS 국내 종목정보파일
- KIS 해외 종목정보파일 또는 Polygon reference tickers

출력:

- `stock_symbols`
  - `market`
  - `ticker`
  - `name`
  - `assetType`
  - `primaryExchangeCode`
  - `currencyCode`
  - `sourceProvider`
  - `sourceIdentifier`
  - `lastMasterSyncedAt`

이 단계에서는 섹터와 배당을 비워도 된다.

### 2단계: 종목 메타 enrichment sync

목적:

- 종목별 섹터/산업 분류와 배당 메타데이터 보강

입력:

- KRX: KIS 종목정보 API, KIS 배당 일정 API
- US: Polygon ticker overview, Polygon dividends

출력:

- `stock_symbols.sector_name`
- `stock_symbols.annual_dividend_yield`
- `stock_symbols.dividend_months_csv`
- 공급자별 raw 메타 테이블

권장 신규 테이블:

### `stock_symbol_enrichments`

- `id`
- `stock_symbol_id`
- `provider`
- `sector_name_raw`
- `industry_name_raw`
- `classification_scheme`
- `annual_dividend_yield_raw`
- `dividend_frequency_raw`
- `dividend_months_csv`
- `last_enriched_at`
- `source_payload_version`

용도:

- 공급자 원본 의미를 보존
- `stock_symbols` 정규화 필드의 근거 저장
- 공급자 전환 시 재계산 가능

### `stock_dividend_events`

- `id`
- `stock_symbol_id`
- `provider`
- `event_type`
- `declared_date`
- `ex_dividend_date`
- `record_date`
- `pay_date`
- `cash_amount`
- `currency_code`
- `frequency_raw`
- `source_event_id`
- `created_at`
- `updated_at`

유니크 키:

- `(provider, source_event_id)` 또는
- `(stock_symbol_id, provider, ex_dividend_date, pay_date, cash_amount)`

용도:

- 실제 배당 이력 저장
- `dividendMonthsCsv` 재계산 근거 확보
- 나중에 종목 상세의 배당 히스토리 UI로 확장 가능

## 필드별 설계

### `sectorName`

정의:

- FOLO 화면에 보여줄 대표 분류명

수집 규칙:

- KRX:
  - KIS에서 업종/테마/시장구분 계열 값을 받을 수 있으면 사용
  - 값이 여러 개면 업종 > 테마 > 기타 분류 순으로 우선순위 적용
- US:
  - Polygon overview/details의 산업/분류 설명값을 사용
  - 가능한 경우 sector와 industry를 분리 저장하고,
    UI용 대표 라벨만 `sectorName`으로 노출

주의:

- KIS 업종명과 Polygon SIC description은 동일한 taxonomy가 아니다.
- `sectorName`은 분석용 표준 분류라기보다 UI 대표 라벨로 보는 것이 안전하다.

### `annualDividendYield`

정의:

- 배당수익률의 대표값

우선순위:

1. 공급자가 직접 제공하는 trailing annual dividend yield
2. 최근 12개월 배당 이벤트 합계 / 현재가
3. 없으면 `null`

권장 저장:

- `stock_symbols.annual_dividend_yield`: 화면/조회용 정규화 값
- `stock_symbol_enrichments.annual_dividend_yield_raw`: 원시값 보존

주의:

- yield는 시세 변동에 따라 바뀌므로 마스터 sync 시점 값이라는 점을 문서화해야 한다.
- 실시간 가격과 결합해 재계산하려면 price snapshot 시점과 분리해서 봐야 한다.

### `dividendMonthsCsv`

정의:

- 최근 배당 패턴 기준으로 배당이 자주 발생하는 월 목록
- 예: `3,6,9,12`

생성 규칙:

1. 최근 24~36개월 `stock_dividend_events` 조회
2. `pay_date` 우선, 없으면 `ex_dividend_date` 사용
3. 월별 출현 횟수를 센다
4. 최소 2회 이상 반복된 월만 채택한다
5. 오름차순 CSV로 저장한다

예:

- 월 배당 ETF: `1,2,3,4,5,6,7,8,9,10,11,12`
- 분기 배당주: `3,6,9,12`
- 불규칙 배당주: `null`

주의:

- 이 값은 기업이 "앞으로 반드시 그 달에 배당한다"는 의미가 아니다.
- 화면 문구도 `배당 경향 월` 또는 `예상 배당 월`로 두는 편이 안전하다.

## 동기화 전략

### A. 마스터 sync와 enrichment sync 분리

권장한다.

이유:

- 심볼 universe는 커지고, 섹터/배당 수집은 느리고 API quota를 많이 쓴다.
- 거래 생성과 검색은 enrichment 없이도 동작해야 한다.
- 실패 시 심볼 마스터까지 막히면 안 된다.

### B. 우선순위 기반 enrichment

전체 종목을 한 번에 다 돌리지 말고 아래 순서로 진행한다.

1. 사용자가 보유한 종목
2. 최근 30일 내 거래된 종목
3. 검색 상위 종목
4. 나머지 활성 종목

### C. 갱신 주기

- 섹터/업종:
  - 일 1회 또는 주 1회
- 배당 이벤트:
  - 미국장: 일 1회
  - 국내: 영업일 기준 일 1회
- `annualDividendYield`, `dividendMonthsCsv`:
  - 배당 이벤트 갱신 직후 재계산

## API 모델 권장안

### 종목 상세

- `sectorName`
- `annualDividendYield`
- `dividendMonths`
- `dividendMetadataStatus`
  - `READY`
  - `PARTIAL`
  - `UNAVAILABLE`

### 포트폴리오 보유 종목

- `sectorName`
- `annualDividendYield`
- `dividendMonths`

### 포트폴리오 월별 배당

현재 API 이름이 `monthlyDividend` 계열이면 아래 중 하나로 명확히 하는 편이 맞다.

- `monthlyDividendForecasts`
- `estimatedMonthlyDividends`

응답 문구 기준도 다음처럼 맞춘다.

- "과거 배당 패턴과 현재 평가금액 기준 예측치"

## 장애 대응과 품질 규칙

### 공급자 장애

- enrichment 실패는 경고와 재시도로 처리한다.
- 종목 상세 API는 기존 저장값을 반환한다.
- enrichment 실패가 거래 생성이나 피드 조회를 막으면 안 된다.

### 데이터 품질

- `sectorName`이 100자 초과면 truncate하지 말고 저장 전 정규화 규칙을 둔다.
- `annualDividendYield`가 음수거나 100 초과면 비정상치로 처리한다.
- `dividendMonthsCsv`는 `1..12`만 허용한다.

### 감사 가능성

- 어떤 공급자에서 언제 어떤 값으로 계산했는지 남겨야 한다.
- 원시 배당 이벤트를 저장하지 않으면 나중에 예측 결과를 설명하기 어렵다.

## 단계별 구현 권장 순서

### Phase A

- `stock_symbols` 필드는 유지
- 미국장 Polygon dividends 수집 추가
- `stock_dividend_events` 테이블 추가
- `dividendMonthsCsv` 파생 로직 추가

효과:

- 미국 종목 월별 배당은 빠르게 현실화 가능

### Phase B

- Polygon overview/details로 미국 종목 sector/industry enrichment 추가
- `stock_symbol_enrichments` 테이블 추가
- `sectorName` 정규화 규칙 도입

효과:

- 미국 종목 섹터 분석 완성도 상승

### Phase C

- KIS 국내 배당 일정 API 연동
- KRX 업종/기본정보 enrichment 추가
- 국내 종목 `sectorName`, `dividendMonthsCsv` 채우기

효과:

- 국내 포트폴리오 분석까지 확장

## 현재 레포 기준 결론

현재 추가한 필드는 방향은 맞다. 다만 지금 구현은 다음처럼 해석해야 한다.

- `sectorName`: 현재는 "원본 파일에 있으면 채움"
- `annualDividendYield`: 현재는 "원본 파일에 있으면 채움"
- `dividendMonthsCsv`: 현재는 "원본 파일에 있으면 채움"

운영 기준으로는 부족하다. FOLO에서 실제로 믿고 쓸 수 있는 값으로 만들려면,
기본 마스터 sync와 별도의 enrichment sync를 나누는 것이 맞다.

## 참고 자료

- Massive Stocks Overview
  - https://massive.com/docs/rest/stocks/tickers/ticker-overview
- Massive Dividends
  - https://massive.com/docs/rest/stocks/corporate-actions/dividends
- KIS Open API 포털
  - https://apiportal.koreainvestment.com/apiservice
