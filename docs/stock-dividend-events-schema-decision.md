# stock_dividend_events 스키마 확정안

## 결론

`stock_dividend_events`는 아래 원칙으로 확정한다.

1. 원시 배당 이벤트를 저장하는 테이블로 둔다.
2. DB primary key는 surrogate key `id`를 사용한다.
3. 중복 방지는 nullable composite unique 대신 `dedupe_key`를 사용한다.
4. 최종 unique key는 `(provider, dedupe_key)`다.
5. `source_event_id`는 디버깅 및 추적용으로 유지하되, 유일성의 단일 근거로
   사용하지 않는다.

이 결정은 Phase A에서 가장 안정적이다.

## 왜 `(provider, source_event_id)`만으로 가지 않는가

이유는 세 가지다.

- 공급자마다 원본 이벤트 ID 제공 여부가 다를 수 있다.
- 같은 공급자라도 일부 이벤트에서 ID가 비어 있을 수 있다.
- ID 포맷이 바뀌거나 회수 정책이 달라지면 DB unique가 깨질 수 있다.

즉 `source_event_id`는 있으면 좋은 식별자지만, 운영 유일성의 절대 기준으로
놓기에는 약하다.

## 왜 nullable composite unique를 쓰지 않는가

예를 들어 아래 같은 unique는 겉보기에는 맞아 보인다.

- `(stock_symbol_id, provider, event_type, ex_dividend_date, pay_date, cash_amount)`

하지만 실제 운영에서는 다음 문제가 있다.

- `pay_date`가 null일 수 있다.
- `cash_amount`가 null일 수 있다.
- 같은 배당을 provider가 나중에 record_date만 보강해 다시 주는 경우가 있다.
- PostgreSQL unique는 null을 같은 값으로 보지 않아서 dedupe가 불안정해진다.

부분 unique index와 `coalesce` 표현식으로도 해결할 수 있지만,
Phase A 기준으로는 복잡도 대비 이득이 작다.

## 최종 스키마

### 테이블

```sql
CREATE TABLE stock_dividend_events (
    id BIGSERIAL PRIMARY KEY,
    stock_symbol_id BIGINT NOT NULL REFERENCES stock_symbols(id),
    provider VARCHAR(30) NOT NULL,
    source_event_id VARCHAR(120),
    dedupe_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    declared_date DATE,
    ex_dividend_date DATE,
    record_date DATE,
    pay_date DATE,
    cash_amount NUMERIC(19, 6),
    currency_code VARCHAR(10),
    frequency_raw VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_dividend_events_provider
        CHECK (provider IN ('KIS', 'POLYGON')),
    CONSTRAINT chk_stock_dividend_events_event_type
        CHECK (event_type IN ('CASH', 'SPECIAL_CASH', 'STOCK', 'OTHER')),
    CONSTRAINT chk_stock_dividend_events_cash_amount
        CHECK (cash_amount IS NULL OR cash_amount >= 0),
    CONSTRAINT chk_stock_dividend_events_has_any_date
        CHECK (
            declared_date IS NOT NULL
            OR ex_dividend_date IS NOT NULL
            OR record_date IS NOT NULL
            OR pay_date IS NOT NULL
        ),
    CONSTRAINT uk_stock_dividend_events_provider_dedupe
        UNIQUE (provider, dedupe_key)
);
```

## 컬럼별 결정

### `stock_symbol_id`

- 필수
- `stock_symbols`와 직접 연결
- analytics 계산과 portfolio 응답 조합에 필요

### `provider`

- 필수
- 현재 허용값:
  - `KIS`
  - `POLYGON`

Phase A 실제 사용은 `POLYGON` 위주다.

### `source_event_id`

- nullable
- 공급자가 직접 주는 이벤트 식별자 보존
- 운영 로그와 추적에 사용

### `dedupe_key`

- 필수
- 길이 `64`
- SHA-256 hex 문자열 저장

이 컬럼이 핵심이다. 모든 적재 이벤트는 항상 `dedupe_key`를 가져야 한다.

### `event_type`

- 필수
- Phase A 정규화 값:
  - `CASH`
  - `SPECIAL_CASH`
  - `STOCK`
  - `OTHER`

초기 구현은 대부분 `CASH`만 써도 된다.
그래도 스키마는 미래 배당 유형 확장을 허용하는 편이 낫다.

### 날짜 컬럼

- `declared_date`
- `ex_dividend_date`
- `record_date`
- `pay_date`

모두 nullable이지만, 적어도 하나는 있어야 한다.

### `cash_amount`

- nullable
- stock dividend 등 현금이 아닌 이벤트를 허용하기 위해 nullable 유지
- 음수는 금지

### `currency_code`

- nullable
- 원시 이벤트 보존용
- 미국 종목은 보통 `USD`

### `frequency_raw`

- nullable
- 공급자가 준 원문 빈도 저장
- 예:
  - `monthly`
  - `quarterly`
  - `annual`

파생 계산은 이 값에 직접 의존하지 않는다.

## dedupe_key 생성 규칙

`dedupe_key`는 앱에서 생성한다.

### 규칙

1. `source_event_id`가 있으면 아래 문자열을 만든다.

```text
provider={provider}|source_event_id={source_event_id}
```

2. `source_event_id`가 없으면 아래 문자열을 만든다.

```text
provider={provider}|symbol={stock_symbol_id}|type={event_type}|ex={ex_dividend_date}|pay={pay_date}|amount={cash_amount}|currency={currency_code}
```

3. 위 문자열을 정규화한 뒤 SHA-256 hex로 저장한다.

### 정규화 규칙

- null은 빈 문자열이 아니라 `-`로 치환
- `cash_amount`는 scale을 고정해 직렬화
  - 예: `1.250000`
- 날짜는 ISO-8601 형식 사용
- `currency_code`는 대문자

## 최종 unique key

### 확정안

```sql
UNIQUE (provider, dedupe_key)
```

### 의미

- 같은 공급자에서 같은 이벤트를 두 번 적재하지 않는다.
- 공급자별 식별 정책 차이를 수용한다.
- null 컬럼 조합 문제를 피한다.

## 권장 인덱스

```sql
CREATE INDEX idx_stock_dividend_events_symbol_pay_date
    ON stock_dividend_events(stock_symbol_id, pay_date DESC);

CREATE INDEX idx_stock_dividend_events_symbol_ex_dividend_date
    ON stock_dividend_events(stock_symbol_id, ex_dividend_date DESC);

CREATE INDEX idx_stock_dividend_events_symbol_provider
    ON stock_dividend_events(stock_symbol_id, provider);
```

## upsert 전략

Phase A에서는 아래 방식이 가장 단순하다.

```sql
INSERT INTO stock_dividend_events (...)
VALUES (...)
ON CONFLICT (provider, dedupe_key)
DO UPDATE SET
    source_event_id = EXCLUDED.source_event_id,
    event_type = EXCLUDED.event_type,
    declared_date = EXCLUDED.declared_date,
    ex_dividend_date = EXCLUDED.ex_dividend_date,
    record_date = EXCLUDED.record_date,
    pay_date = EXCLUDED.pay_date,
    cash_amount = EXCLUDED.cash_amount,
    currency_code = EXCLUDED.currency_code,
    frequency_raw = EXCLUDED.frequency_raw,
    updated_at = CURRENT_TIMESTAMP;
```

이유:

- 공급자가 나중에 `record_date`나 `pay_date`를 보강해서 다시 보내도 반영 가능
- 중복 적재는 방지
- source event correction을 수용 가능

## analytics 계산과의 관계

`stock_symbols`의 아래 필드는 이 raw table에서 파생한다.

- `annual_dividend_yield`
- `dividend_months_csv`

즉 `stock_dividend_events`는 원시 저장소이고,
`stock_symbols`는 조회용 캐시/정규화 결과다.

## 보류한 선택지

### 선택지 A

`UNIQUE (provider, source_event_id)` + 보조 partial unique

보류 이유:

- source_event_id null 케이스 처리 복잡
- migration과 repository 로직이 더 복잡해짐

### 선택지 B

raw 컬럼 composite unique만 사용

보류 이유:

- null 처리 취약
- 공급자 보정 업데이트에 약함
- schema가 raw payload 변화에 과도하게 민감해짐

## 구현 시 체크포인트

1. `dedupe_key` 생성 규칙을 테스트로 고정할 것
2. `cash_amount` scale 직렬화 규칙을 고정할 것
3. `source_event_id`가 없는 응답도 중복 없이 처리할 것
4. upsert 후 `updated_at`이 갱신되는지 확인할 것

## 확정안 요약

- 테이블 이름: `stock_dividend_events`
- 최종 unique key: `(provider, dedupe_key)`
- `dedupe_key`는 SHA-256 hex
- `source_event_id`는 참고용 보존 필드
- `stock_symbols` 배당 필드는 이 테이블에서 파생 계산
