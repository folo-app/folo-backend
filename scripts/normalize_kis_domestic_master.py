#!/usr/bin/env python3
"""
Normalize raw KIS domestic stock master files into the CSV schema used by FOLO.

Supported inputs:
- `kospi_code.mst`
- `kosdaq_code.mst`
- `kospi_code.mst.zip`
- `kosdaq_code.mst.zip`

The field slicing logic follows the official KIS example scripts published in:
- `stocks_info/kis_kospi_code_mst.py`
- `stocks_info/kis_kosdaq_code_mst.py`
from the `koreainvestment/open-trading-api` repository.
"""

from __future__ import annotations

import argparse
import csv
import io
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


KOSPI_PART2_WIDTHS = [
    2, 1, 4, 4, 4,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 9, 5, 5, 1,
    1, 1, 2, 1, 1,
    1, 2, 2, 2, 3,
    1, 3, 12, 12, 8,
    15, 21, 2, 7, 1,
    1, 1, 1, 1, 9,
    9, 9, 5, 9, 8,
    9, 3, 1, 1, 1,
]

KOSPI_PART2_COLUMNS = [
    "group_code", "market_cap_size", "index_sector_large", "index_sector_medium", "index_sector_small",
    "manufacturing", "low_liquidity", "governance_index", "kospi200_sector", "kospi100",
    "kospi50", "krx", "etp", "elw_issued", "krx100",
    "krx_auto", "krx_semiconductor", "krx_bio", "krx_bank", "spac",
    "krx_energy_chem", "krx_steel", "short_term_overheat", "krx_media_telecom", "krx_construction",
    "non1", "krx_securities", "krx_ship", "krx_insurance", "krx_transport",
    "sri", "base_price", "regular_lot_size", "after_hours_lot_size", "trading_halt",
    "liquidation_trade", "managed_issue", "market_warning", "warning_notice", "poor_disclosure",
    "backdoor_listing", "lock_type", "par_value_change", "capital_increase_type", "margin_rate",
    "credit_available", "credit_period", "previous_volume", "par_value", "listed_at",
    "listed_shares", "capital", "settlement_month", "public_offering_price", "preferred_share",
    "short_sell_overheat", "abnormal_surge", "krx300", "kospi", "sales",
    "operating_income", "ordinary_income", "net_income", "roe", "base_year_month",
    "market_cap", "group_company_code", "credit_limit_exceeded", "secured_loan_available", "loanable",
]

KOSDAQ_PART2_WIDTHS = [
    2, 1,
    4, 4, 4, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 9,
    5, 5, 1, 1, 1,
    2, 1, 1, 1, 2,
    2, 2, 3, 1, 3,
    12, 12, 8, 15, 21,
    2, 7, 1, 1, 1,
    1, 9, 9, 9, 5,
    9, 8, 9, 3, 1,
    1, 1,
]

KOSDAQ_PART2_COLUMNS = [
    "security_group_code", "market_cap_size",
    "index_sector_large", "index_sector_medium", "index_sector_small", "venture", "low_liquidity",
    "krx", "etp", "krx100", "krx_auto", "krx_semiconductor",
    "krx_bio", "krx_bank", "spac", "krx_energy_chem", "krx_steel",
    "short_term_overheat", "krx_media_telecom", "krx_construction", "investment_caution_issue",
    "krx_securities", "krx_ship", "krx_insurance", "krx_transport", "kosdaq150",
    "base_price", "regular_lot_size", "after_hours_lot_size", "trading_halt", "liquidation_trade",
    "managed_issue", "market_warning", "warning_notice", "poor_disclosure", "backdoor_listing",
    "lock_type", "par_value_change", "capital_increase_type", "margin_rate", "credit_available",
    "credit_period", "previous_volume", "par_value", "listed_at", "listed_shares",
    "capital", "settlement_month", "public_offering_price", "preferred_share", "short_sell_overheat",
    "abnormal_surge", "krx300", "sales", "operating_income", "ordinary_income",
    "net_income", "roe", "base_year_month", "market_cap", "group_company_code",
    "credit_limit_exceeded", "secured_loan_available", "loanable",
]


@dataclass
class NormalizedRow:
    ticker: str
    name: str
    asset_type: str
    primary_exchange_code: str
    currency_code: str
    source_identifier: str
    active: str
    sector_name: str
    industry_name: str
    source_payload_version: str


SOURCE_PAYLOAD_VERSION = "kis:domestic-master:v2"

OUTPUT_COLUMNS = [
    "ticker",
    "name",
    "assetType",
    "primaryExchangeCode",
    "currencyCode",
    "sourceIdentifier",
    "active",
    "sectorName",
    "industryName",
    "sourcePayloadVersion",
]

INDUSTRY_FLAG_PRIORITY = [
    ("krx_semiconductor", "Semiconductors"),
    ("krx_bio", "Biotechnology"),
    ("krx_bank", "Banking"),
    ("krx_insurance", "Insurance"),
    ("krx_securities", "Securities"),
    ("krx_auto", "Automobiles"),
    ("krx_energy_chem", "Energy & Chemicals"),
    ("krx_steel", "Steel"),
    ("krx_media_telecom", "Media & Telecom"),
    ("krx_construction", "Construction"),
    ("krx_ship", "Shipbuilding"),
    ("krx_transport", "Transportation"),
]

INDUSTRY_TO_SECTOR = {
    "Semiconductors": "Technology",
    "Biotechnology": "Healthcare",
    "Banking": "Financials",
    "Insurance": "Financials",
    "Securities": "Financials",
    "SPAC": "Financials",
    "Automobiles": "Consumer Discretionary",
    "Energy & Chemicals": "Materials",
    "Steel": "Materials",
    "Media & Telecom": "Communication Services",
    "Construction": "Industrials",
    "Shipbuilding": "Industrials",
    "Transportation": "Industrials",
    "Manufacturing": "Industrials",
    "Venture": "Venture",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Normalize raw KIS domestic master files into FOLO CSV schema."
    )
    parser.add_argument(
        "--input",
        dest="inputs",
        nargs="+",
        required=True,
        help="One or more .mst or .zip files. Example: kospi_code.mst.zip kosdaq_code.mst.zip",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Output CSV path.",
    )
    parser.add_argument(
        "--include-etp",
        action="store_true",
        help="Include ETP rows and mark them as ETF. Default is to exclude them.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_path = Path(args.output).expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows: dict[str, NormalizedRow] = {}
    for input_value in args.inputs:
        for row in normalize_input(Path(input_value), include_etp=args.include_etp):
            rows[row.ticker] = row

    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(OUTPUT_COLUMNS)
        for row in sorted(rows.values(), key=lambda item: item.ticker):
            writer.writerow([
                row.ticker,
                row.name,
                row.asset_type,
                row.primary_exchange_code,
                row.currency_code,
                row.source_identifier,
                row.active,
                row.sector_name,
                row.industry_name,
                row.source_payload_version,
            ])

    print(f"Wrote {len(rows)} rows to {output_path}")
    return 0


def normalize_input(path: Path, include_etp: bool) -> Iterable[NormalizedRow]:
    file_name = path.name.lower()
    if "kospi" in file_name:
        parser = parse_kospi_rows
    elif "kosdaq" in file_name:
        parser = parse_kosdaq_rows
    else:
        raise ValueError(
            f"Cannot determine market from filename: {path.name}. "
            "Expected kospi or kosdaq in the filename."
        )

    raw_text = read_source_text(path)
    return parser(raw_text, include_etp=include_etp)


def read_source_text(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(path)

    if path.suffix.lower() == ".zip":
        with zipfile.ZipFile(path) as archive:
            mst_names = [name for name in archive.namelist() if name.lower().endswith(".mst")]
            if not mst_names:
                raise ValueError(f"No .mst file found inside {path}")
            with archive.open(mst_names[0]) as handle:
                return handle.read().decode("cp949")

    return path.read_text(encoding="cp949")


def parse_kospi_rows(raw_text: str, include_etp: bool) -> Iterable[NormalizedRow]:
    for line in split_lines(raw_text):
        part1 = line[:-228]
        part2 = line[-228:]

        ticker = part1[0:9].rstrip()
        standard_code = part1[9:21].rstrip()
        name = part1[21:].strip()
        details = slice_fixed_width_fields(part2, KOSPI_PART2_WIDTHS, KOSPI_PART2_COLUMNS)

        row = build_row(
            ticker=ticker,
            name=name,
            standard_code=standard_code,
            details=details,
            include_etp=include_etp,
        )
        if row:
            yield row


def parse_kosdaq_rows(raw_text: str, include_etp: bool) -> Iterable[NormalizedRow]:
    for line in split_lines(raw_text):
        part1 = line[:-222]
        part2 = line[-222:]

        ticker = part1[0:9].rstrip()
        standard_code = part1[9:21].rstrip()
        name = part1[21:].strip()
        details = slice_fixed_width_fields(part2, KOSDAQ_PART2_WIDTHS, KOSDAQ_PART2_COLUMNS)

        row = build_row(
            ticker=ticker,
            name=name,
            standard_code=standard_code,
            details=details,
            include_etp=include_etp,
        )
        if row:
            yield row


def split_lines(raw_text: str) -> list[str]:
    return [line for line in raw_text.splitlines() if line.strip()]


def slice_fixed_width_fields(raw_text: str, widths: list[int], columns: list[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    offset = 0
    for width, column in zip(widths, columns):
        values[column] = raw_text[offset:offset + width].strip()
        offset += width
    return values


def build_row(
    ticker: str,
    name: str,
    standard_code: str,
    details: dict[str, str],
    include_etp: bool,
) -> NormalizedRow | None:
    normalized_ticker = ticker.strip()
    normalized_name = name.strip()
    if not normalized_ticker or not normalized_name:
        return None

    is_etf = details.get("etp", "") == "Y" or "ETF" in normalized_name.upper()
    if is_etf and not include_etp:
        return None

    active = "false" if details.get("liquidation_trade", "") == "Y" else "true"
    industry_name = derive_industry_name(details)
    sector_name = derive_sector_name(details, industry_name)

    return NormalizedRow(
        ticker=normalized_ticker,
        name=normalized_name,
        asset_type="ETF" if is_etf else "STOCK",
        primary_exchange_code="XKRX",
        currency_code="KRW",
        source_identifier=standard_code or normalized_ticker,
        active=active,
        sector_name=sector_name or "",
        industry_name=industry_name or "",
        source_payload_version=SOURCE_PAYLOAD_VERSION,
    )


def derive_industry_name(details: dict[str, str]) -> str | None:
    for column, label in INDUSTRY_FLAG_PRIORITY:
        if is_enabled(details.get(column)):
            return label

    if is_enabled(details.get("spac")):
        return "SPAC"

    if is_enabled(details.get("venture")):
        return "Venture"

    if is_enabled(details.get("manufacturing")):
        return "Manufacturing"

    return None


def derive_sector_name(details: dict[str, str], industry_name: str | None) -> str | None:
    if industry_name:
        return INDUSTRY_TO_SECTOR.get(industry_name, industry_name)

    if is_enabled(details.get("manufacturing")):
        return "Industrials"

    return None


def is_enabled(raw: str | None) -> bool:
    if raw is None:
        return False

    normalized = raw.strip().upper()
    return normalized in {"Y", "1", "TRUE"}


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
