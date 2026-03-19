# 미국장 마스터 CSV 스키마와 생성 절차

## 목적

- KIS 해외 종목정보 원본을 FOLO 백엔드가 읽는 미국장 마스터 CSV로 정리한다.
- 정리 결과는 `KIS_OVERSEAS_MASTER_FILE_URL`에 연결한다.

## 현재 지원 범위

- `NASDAQ`
- `NYSE`
- `AMEX`

현재 `MarketType`은 위 3개 미국 거래소만 지원한다.
홍콩, 일본, 중국 등 다른 해외 시장은 별도 enum 확장이 필요하다.

## 출력 스키마

```csv
ticker,name,market,assetType,primaryExchangeCode,currencyCode,sourceIdentifier,active
```

## 컬럼 정의

- `ticker`: 거래 심볼. 예: `AAPL`
- `name`: 회사명 또는 ETF명
- `market`: `NASDAQ`, `NYSE`, `AMEX`
- `assetType`: `STOCK` 또는 `ETF`
- `primaryExchangeCode`: 거래소 코드. 예: `NAS`, `NYS`, `AMS`
- `currencyCode`: 현재 미국장은 `USD`
- `sourceIdentifier`: 공급자 원본 식별자. 표준 코드가 있으면 우선 사용
- `active`: 상장 상태 기준 `true` 또는 `false`

## 예시

```csv
ticker,name,market,assetType,primaryExchangeCode,currencyCode,sourceIdentifier,active
AAPL,Apple Inc.,NASDAQ,STOCK,NAS,USD,AAPL,true
NVDA,NVIDIA Corporation,NASDAQ,STOCK,NAS,USD,NVDA,true
VOO,Vanguard S&P 500 ETF,NYSE,ETF,NYS,USD,VOO,true
```

예시 파일:
- `docs/examples/kis-overseas-master.sample.csv`

## 생성 절차

1. KIS에서 해외 종목정보 원본 파일을 준비한다.
2. 원본에서 미국 거래소 종목만 남긴다.
3. 거래소 식별값을 아래 규칙으로 매핑한다.
4. 심볼을 대문자로 정규화한다.
5. 종목명, 자산 유형, 거래소 코드, 상장 상태를 채운다.
6. 최종 CSV를 `KIS_OVERSEAS_MASTER_FILE_URL` 경로에 저장한다.

## 거래소 매핑 규칙

- `NASDAQ` -> `market=NASDAQ`, `primaryExchangeCode=NAS`
- `NYSE` -> `market=NYSE`, `primaryExchangeCode=NYS`
- `AMEX` -> `market=AMEX`, `primaryExchangeCode=AMS`

원본 파일에 `market`이 없고 거래소 약어만 있으면 위 규칙으로
`market`과 `primaryExchangeCode`를 같이 채운다.

## 자산 유형 규칙

- 보통주, ADR 등 일반 주식: `STOCK`
- ETF, ETN, index fund 계열: `ETF`

## 상장 상태 규칙

- 상장 상태면 `active=true`
- 상장폐지, 거래종료, 비활성 상태면 `active=false`

## 환경 변수 연결

```env
KIS_OVERSEAS_MASTER_FILE_URL=/Users/godten/folo-backend/data/kis/kis-us-master.csv
```

## 운영 메모

- 종목 검색과 시세는 KIS를 사용한다.
- 회사 로고는 KIS가 제공하지 않으므로 Twelve Data를 1순위로 쓰고,
  미국 종목은 Polygon을 fallback으로 쓴다.
- 미국장 전체 universe를 운영하려면 CSV 생성 주기와 sync cron을 같이 관리하는 편이 안전하다.
