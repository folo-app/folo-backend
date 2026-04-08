# KRX Domestic Sector Map

국내주 `기타` 비중을 줄이기 위한 2차 섹터 매핑 파일은
`data/kis/kis-domestic-sector-map.csv`를 사용한다.

## 파일 형식

```csv
ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
005930,,Information Technology,Semiconductors,krx:sector-map:v2
,212,Health Care,Pharmaceuticals,krx:sector-map:v2
```

- `ticker`
  - 특정 종목만 예외 처리할 때 사용한다.
  - `ticker`가 채워진 row가 가장 우선한다.
- `indutyCode`
  - OPENDART `stock_issuer_profiles.induty_code` 기준 공통 매핑이다.
  - 같은 업종 코드가 broad sector로 안정적으로 수렴할 때 쓴다.
- `sectorName`
  - broad sector의 원문 기준축이다.
  - 영어 canonical 값을 권장한다.
  - 예: `Information Technology`, `Health Care`, `Materials`
- `industryName`
  - 더 구체적인 industry 힌트다.
  - 정규화는 최종적으로 `sectorName`과 `industryName`을 함께 참고한다.
- `sourcePayloadVersion`
  - 파일 버전 추적용이다.

## 채우는 원칙

1. 먼저 `indutyCode` 공통 row를 추가한다.
   - 제약/보험/증권/반도체/건설처럼 업종 코드와 sector의 대응이 안정적인 경우
2. 그다음 `ticker` 예외 row를 추가한다.
   - 지주사, 복합 사업자, 브랜드명만으로는 애매한 종목
   - 예: `NAVER`, `CJ`, `HD현대` 같은 케이스
3. 둘 다 애매하면 파일에 무리해서 넣지 않는다.
   - 이 경우 코드의 이름 기반 heuristic이 마지막 fallback으로 동작한다.

## 추천 작업 순서

1. 현재 `기타` 종목을 조회한다.
2. `stock_issuer_profiles.induty_code`가 있는지 본다.
3. 동일 `indutyCode` 종목들이 같은 sector로 읽히면 `indutyCode` row를 추가한다.
4. 그렇지 않으면 `ticker` row로만 예외 처리한다.
5. 서버 재시작 또는 sector backfill 실행 후 결과를 확인한다.

## 검증 쿼리 예시

```sql
select ticker, name, sector_code, sector_name
from stock_symbols
where market = 'KRX'
  and asset_type = 'STOCK'
  and active = true
  and sector_code = 'OTHER'
order by name;
```

```sql
select p.induty_code, count(*) as symbols
from stock_issuer_profiles p
join stock_symbols s on s.id = p.stock_symbol_id
where s.market = 'KRX'
  and s.asset_type = 'STOCK'
  and p.induty_code is not null
group by p.induty_code
order by symbols desc;
```
