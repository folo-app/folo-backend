# KIS 국내 종목정보파일 정규화

## 목적

- KIS 원본 `kospi_code.mst`, `kosdaq_code.mst` 파일을
  FOLO 백엔드가 읽는 CSV 스키마로 변환한다.
- 변환 결과는 `KIS_DOMESTIC_MASTER_FILE_URL`에 연결한다.

## 입력 파일

- `kospi_code.mst`
- `kospi_code.mst.zip`
- `kosdaq_code.mst`
- `kosdaq_code.mst.zip`

스크립트는 파일명에 `kospi`, `kosdaq`가 포함돼 있어야
올바른 파서를 선택한다.

## 출력 스키마

```csv
ticker,name,assetType,primaryExchangeCode,currencyCode,sourceIdentifier,active,sectorName,industryName,sourcePayloadVersion
```

- `ticker`: 6자리 국내 종목코드
- `name`: 종목명
- `assetType`: `STOCK` 또는 `ETF`
- `primaryExchangeCode`: 항상 `XKRX`
- `currencyCode`: 항상 `KRW`
- `sourceIdentifier`: 표준코드가 있으면 표준코드, 없으면 종목코드
- `active`: 정리매매 여부 기준 `true` 또는 `false`
- `sectorName`: KIS thematic flag 기반 대표 섹터명
- `industryName`: KIS thematic flag 기반 세부 업종명
- `sourcePayloadVersion`: enrichment 생성 버전

## 사용 방법

```bash
cd /Users/godten/folo-backend
python3 scripts/normalize_kis_domestic_master.py \
  --input raw/kis/kospi_code.mst.zip raw/kis/kosdaq_code.mst.zip \
  --output data/kis/kis-domestic-master.csv
```

ETF까지 포함하려면 `--include-etp`를 추가한다.

```bash
cd /Users/godten/folo-backend
python3 scripts/normalize_kis_domestic_master.py \
  --input raw/kis/kospi_code.mst.zip raw/kis/kosdaq_code.mst.zip \
  --output data/kis/kis-domestic-master.csv \
  --include-etp
```

## 정규화 규칙

- 기본값은 주식 종목만 남기고 ETP는 제외한다.
- `etp=Y` 이거나 종목명에 `ETF`가 들어가면 `ETF`로 표기한다.
- `liquidation_trade=Y`면 `active=false`로 저장한다.
- KOSPI, KOSDAQ는 모두 `XKRX`로 통합한다.
- sector/industry는 원본 master의 thematic flag를 우선 사용한다.
  - `krx_semiconductor=Y` -> `Technology / Semiconductors`
  - `krx_bio=Y` -> `Healthcare / Biotechnology`
  - `krx_bank=Y` -> `Financials / Banking`
  - `krx_insurance=Y` -> `Financials / Insurance`
  - `krx_securities=Y` -> `Financials / Securities`
  - `krx_auto=Y` -> `Consumer Discretionary / Automobiles`
  - `krx_energy_chem=Y` -> `Materials / Energy & Chemicals`
  - `krx_steel=Y` -> `Materials / Steel`
  - `krx_media_telecom=Y` -> `Communication Services / Media & Telecom`
  - `krx_construction=Y`, `krx_ship=Y`, `krx_transport=Y` -> `Industrials`
  - 위 분류가 없고 `manufacturing=Y`면 `Industrials / Manufacturing`

기존처럼 기본 컬럼만 있는 CSV도 master sync에는 계속 사용할 수 있다.
다만 KRX metadata enrichment까지 사용하려면 새 컬럼이 포함된 CSV를 권장한다.

## 환경 변수 연결

```env
KIS_DOMESTIC_MASTER_FILE_URL=/Users/godten/folo-backend/data/kis/kis-domestic-master.csv
```

## 참고

- 필드 slicing 로직은 KIS 공식 예제
  `kis_kospi_code_mst.py`, `kis_kosdaq_code_mst.py` 구조를 따른다.
- 현재 백엔드는 국내 종목을 하나의 `KRX` universe로 취급한다.
