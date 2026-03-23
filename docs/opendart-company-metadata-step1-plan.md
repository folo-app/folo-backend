# Step 1: OPENDART 회사 메타 수집 스키마/구현 계획

작성일: 2026-03-23  
범위: `KRX 로고 수집 Phase 1`의 첫 단계인 `회사 메타 수집`

## 목적

국내주식 로고 수집과 섹터 raw 코드 축을 만들기 위해, 먼저
`stock_symbol(KRX)` 기준의 회사 메타를 공식 소스에서 안정적으로 수집한다.

이 단계에서 해결하려는 문제는 아래 두 가지다.

1. `005930 -> 삼성전자 -> OPENDART corp_code -> 공식 홈페이지 URL` 연결
2. `005930 -> OPENDART induty_code` raw 축 저장

즉 Step 1은 `이미지 수집`이 아니라, 그 전단계인 `회사 메타 정규화`다.

## 공식 소스와 역할

### 1. OPENDART `corpCode.xml`

역할:
- 상장사 `stock_code`와 OPENDART `corp_code`를 연결한다.

문서:
- [OPENDART corpCode.xml](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019018)

활용:
- `stock_symbols.ticker`와 OPENDART 회사 식별자를 연결하는 1차 매핑

### 2. OPENDART `company.json`

역할:
- 회사 개황 정보 조회
- `corp_name`, `stock_code`, `corp_cls`, `hm_url`, `ir_url`,
  `induty_code` 등을 제공

문서:
- [OPENDART company.json](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019002)

활용:
- 로고 수집용 URL source
- 공식 raw 산업코드 축

### 3. 현재 FOLO 내부 기준

현재 국내 섹터 표시값은 KIS master 기반이다.

관련 구현:
- [KisDomesticMasterMetadataEnrichmentProvider.java](/Users/godten/.codex/worktrees/a784/folo-backend/src/main/java/com/folo/stock/KisDomesticMasterMetadataEnrichmentProvider.java)
- [kis-domestic-master-normalization.md](/Users/godten/.codex/worktrees/a784/folo-backend/docs/kis-domestic-master-normalization.md)

Step 1에서는 이 표시용 섹터를 바꾸지 않는다.
대신 `OPENDART induty_code`를 추가 저장해 향후 국내 섹터 기준축으로 쓴다.

## 구현 범위

### 포함

- OPENDART API key 설정 추가
- `corpCode.xml` 다운로드/파싱
- KRX active symbol 대상 `corp_code` 매핑 적재
- `company.json` 조회
- `corp_name`, `corp_cls`, `hm_url`, `ir_url`, `induty_code` 적재
- sync 이력 저장
- 실패/재시도 전략 정의
- 테스트 추가

### 제외

- 실제 로고 다운로드
- favicon/og:image 파싱
- S3/CloudFront 저장
- `induty_code -> display sector` 자동 매핑
- KRX Data Marketplace 백필

## 설계 결정

### 1. `stock_symbol_enrichments`에 다 넣지 않고 별도 테이블을 둔다

이유:
- `stock_symbol_enrichments`는 provider별 display metadata용 구조에 가깝다.
- `corp_code`, `hm_url`, `ir_url`, `induty_code`는 회사 메타 성격이 강하다.
- 로고 수집기와 섹터 raw code가 같은 축을 공유하므로 별도 lifecycle이 필요하다.

결론:
- `stock_issuer_profiles` 신규 테이블 추가

### 2. OPENDART 회사 메타는 `KRX active STOCK`만 우선 수집한다

이유:
- ETF/ETN은 회사 로고와 상품 로고가 다를 수 있다.
- ETP까지 한 번에 넣으면 로고와 섹터 의미가 흐려진다.
- Phase 1의 목표는 `국내 개별주` 경험 개선이다.

결론:
- 대상: `market=KRX`, `assetType=STOCK`, `active=true`

### 3. `corpCode.xml`은 전체 스냅샷, `company.json`은 종목별 조회로 분리한다

이유:
- `corpCode.xml`은 전체 목록 스냅샷 성격이다.
- `company.json`은 `corp_code` 기반 상세 조회다.
- 실패 지점과 캐시 전략이 다르다.

결론:
- Step 1 sync는 아래 2단계 파이프라인으로 구현
  1. corp code dictionary refresh
  2. company detail upsert

## 스키마 제안

### 신규 테이블: `stock_issuer_profiles`

목적:
- 회사 메타 canonical 저장소

컬럼 제안:

- `id BIGSERIAL PRIMARY KEY`
- `stock_symbol_id BIGINT NOT NULL`
- `provider VARCHAR(30) NOT NULL`
  - 초기값: `OPENDART`
- `corp_code VARCHAR(8) NOT NULL`
- `corp_name VARCHAR(200) NOT NULL`
- `stock_code VARCHAR(12) NOT NULL`
- `corp_cls VARCHAR(10) NULL`
- `hm_url VARCHAR(500) NULL`
- `ir_url VARCHAR(500) NULL`
- `induty_code VARCHAR(20) NULL`
- `source_payload_version VARCHAR(60) NULL`
- `last_synced_at TIMESTAMP NOT NULL`
- `created_at TIMESTAMP NOT NULL`
- `updated_at TIMESTAMP NOT NULL`

제약/인덱스:

- `uk_stock_issuer_profiles_symbol_provider(stock_symbol_id, provider)`
- `uk_stock_issuer_profiles_corp_code(provider, corp_code)`
- `idx_stock_issuer_profiles_induty_code(induty_code)`
- `idx_stock_issuer_profiles_last_synced_at(last_synced_at)`

메모:
- `corp_code`는 회사 단위 unique여서 향후 다중 심볼 이슈가 있을 수 있다.
- Phase 1에서는 `stock_symbol_id + provider`와 `provider + corp_code` 둘 다 unique로 두는 편이 안전하다.

### sync run 재사용 여부

현재 [StockSymbolSyncRun.java](/Users/godten/.codex/worktrees/a784/folo-backend/src/main/java/com/folo/stock/StockSymbolSyncRun.java) 가 있다.

옵션:

1. 기존 `stock_symbol_sync_runs` 재사용
- 장점: 구현 단순
- 단점: master/dividend/enrichment와 issuer sync가 섞임

2. 별도 `issuer_profile_sync_runs` 추가
- 장점: 의미 명확
- 단점: 테이블 하나 더 필요

권장:
- Step 1은 기존 `stock_symbol_sync_runs` 재사용
- `sync_scope`에 `ISSUER_PROFILE` 추가

이유:
- 운영 대시보드가 아직 복잡하지 않다.
- 먼저 움직이는 것이 중요하다.

## 코드 구조 제안

### 1. 설정

파일:
- `src/main/java/com/folo/config/MarketDataSyncProperties.java`

추가 제안:

```java
public record Opendart(
    boolean enabled,
    String apiKey,
    String baseUrl,
    String issuerProfileCron
) {}
```

또는 `MarketDataSyncProperties` 아래에 아래 필드를 추가:

- `integration.market-data.opendart.enabled`
- `integration.market-data.opendart.api-key`
- `integration.market-data.opendart.base-url`
- `integration.market-data.opendart.issuer-profile-cron`

기본값 제안:

- `base-url=https://opendart.fss.or.kr/api`
- cron은 우선 문서화만 하고 Step 1 구현은 수동 실행 중심으로 시작 가능

### 2. 엔티티

파일 제안:
- `src/main/java/com/folo/stock/StockIssuerProfile.java`

역할:
- `stock_issuer_profiles` 매핑

### 3. 리포지토리

파일 제안:
- `StockIssuerProfileRepository.java`

필수 메서드:

- `findByStockSymbolId(Long stockSymbolId)`
- `findByProviderAndCorpCode(...)`
- `findAllByStockSymbolIdIn(...)`

### 4. OPENDART client

파일 제안:
- `OpendartClient.java`

역할:
- `corpCode.xml` fetch
- `company.json` fetch
- provider 응답 DTO 파싱

필수 메서드:

- `Map<String, OpendartCorpCodeRecord> fetchCorpCodes()`
- `OpendartCompanyRecord fetchCompany(String corpCode)`

DTO 제안:

- `OpendartCorpCodeRecord`
  - `corpCode`
  - `corpName`
  - `stockCode`
- `OpendartCompanyRecord`
  - `corpCode`
  - `corpName`
  - `stockCode`
  - `corpCls`
  - `hmUrl`
  - `irUrl`
  - `indutyCode`

메모:
- `company.json` 응답 필드명은 구현 시 실제 문서/응답 기준으로 다시 확인
- 현재 계획은 문서 기준 가정이다

### 5. sync service

파일 제안:
- `StockIssuerProfileSyncService.java`

핵심 메서드:

- `syncPrioritySymbols()`
- `syncSymbols(Collection<Long> stockSymbolIds)`
- `syncAllActiveKrxStocks()`

내부 흐름:

1. `KRX active STOCK` 대상 심볼 조회
2. `corpCode.xml` fetch
3. `ticker -> corp_code` 매핑
4. 각 `corp_code`에 대해 `company.json` fetch
5. `stock_issuer_profiles` upsert
6. sync run 기록

### 6. 수동 trigger endpoint

파일 제안:
- 기존 [StockEnrichmentOpsController.java](/Users/godten/.codex/worktrees/a784/folo-backend/src/main/java/com/folo/stock/StockEnrichmentOpsController.java)
  확장

예시:

- `POST /api/internal/stock-enrichment/issuer-profiles/sync`
- 헤더: `X-Internal-Trigger-Secret`

이유:
- Step 1은 운영 크론보다 수동 검증이 먼저다.

## 구현 순서

### Phase 1-1. 스키마

작업:
- `V9__add_stock_issuer_profiles.sql`
- `StockSymbolSyncScope.ISSUER_PROFILE` 추가

완료 기준:
- migration 통과
- 엔티티/리포지토리 컴파일 통과

### Phase 1-2. corpCode dictionary

작업:
- `OpendartClient.fetchCorpCodes()`
- XML unzip/parse
- `stock_code -> corp_code` map 생성

완료 기준:
- `005930`, `000660`, `035420` 매핑 확인

### Phase 1-3. company detail upsert

작업:
- `company.json` 호출
- `hm_url`, `ir_url`, `induty_code` 저장

완료 기준:
- 상위 KRX 종목 회사 메타 적재 확인

### Phase 1-4. 수동 sync endpoint

작업:
- ops controller 연동
- internal secret 보호

완료 기준:
- 수동 endpoint로 특정 symbol set 동기화 가능

## 국내 섹터 데이터 적용 전략

### 현재 유지할 것

- `stock_symbols.sectorName`
- `stock_symbol_enrichments`의 `KIS_MASTER` 표시값

이유:
- 이미 프론트/포트폴리오/추천에서 사용 중이다.
- 사용자에게 보이는 분류명으로 충분히 읽기 좋다.

### Step 1에서 새로 추가할 것

- `stock_issuer_profiles.induty_code`

이 값은 당장 앱에 노출하지 않아도 된다.
우선 canonical raw code로 저장하는 것이 중요하다.

### 향후 매핑 전략

향후 `induty_code -> display sector` 매핑은 아래 우선순위로 만든다.

1. KIS thematic flag로 분류 가능한 종목은 기존 `sectorName` 유지
2. KIS 분류가 없거나 애매한 종목은 `induty_code` 기반 매핑 테이블 사용
3. KRX Data Marketplace export와 월 단위 검증

즉 Step 1에서는 섹터 표시는 건드리지 않고,
섹터의 공식 raw 기준축만 추가하는 것이 맞다.

## 에러 처리

### corpCode.xml 실패

- 전체 sync run 실패 처리
- 기존 issuer profile 데이터는 유지

### company.json 단건 실패

- 종목 단위 실패로 누적
- 전체 배치 중단 없이 계속 진행
- sync run `FAILED` 또는 `COMPLETED_WITH_ERRORS`에 준하는 메시지 저장

### URL 데이터 이상

- `hm_url`, `ir_url`가 비어 있거나 잘못된 경우 null 허용
- Step 1에서는 저장만 하고, 로고 수집은 Step 2에서 판별

## 테스트 계획

### 단위 테스트

- corpCode.xml 파서
- company.json 응답 파서
- `ticker -> corp_code` 매핑
- upsert 로직

### 통합 테스트

- 수동 sync endpoint 호출
- `stock_issuer_profiles` 적재 확인
- 기존 KIS master 기반 metadata와 충돌 없이 공존 확인

### 수동 검증 종목

- `005930` 삼성전자
- `000660` SK하이닉스
- `035420` NAVER
- `005380` 현대차
- `051910` LG화학

## 완료 기준

- `stock_issuer_profiles` 테이블이 생성된다.
- 상위 KRX 종목에 대해 `corp_code`, `hm_url`, `ir_url`, `induty_code`가 적재된다.
- 수동 sync endpoint가 동작한다.
- 기존 검색/추천/포트폴리오 API에는 영향이 없다.
- 로고 수집 Step 2가 바로 이어질 수 있는 구조가 마련된다.

## 다음 단계

Step 1 완료 후 바로 이어질 작업:

1. `Step 2: favicon / rel=icon / og:image 수집기`
2. `StockBrandingService`에 KRX internal asset first 규칙 추가
3. `induty_code` 기반 국내 섹터 보정 전략 설계
