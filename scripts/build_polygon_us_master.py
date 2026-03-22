#!/usr/bin/env python3
"""
Build a FOLO-compatible US stock master CSV from Polygon reference tickers.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
import urllib.parse
import urllib.error
import urllib.request
from pathlib import Path


OUTPUT_COLUMNS = [
    "ticker",
    "name",
    "market",
    "assetType",
    "primaryExchangeCode",
    "currencyCode",
    "sourceIdentifier",
    "active",
]

EXCHANGE_MARKET_MAP = {
    "XNAS": ("NASDAQ", "NAS"),
    "XNYS": ("NYSE", "NYS"),
    "XASE": ("AMEX", "AMS"),
}

ETF_TYPE_MARKERS = {
    "ETF",
    "ETN",
    "ETV",
    "CEF",
    "FUND",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build the FOLO US master CSV from Polygon reference tickers."
    )
    parser.add_argument(
        "--api-key",
        help="Polygon API key. Falls back to POLYGON_API_KEY environment variable.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Output CSV path.",
    )
    parser.add_argument(
        "--page-limit",
        type=int,
        default=1000,
        help="Polygon page size. Max 1000.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    api_key = args.api_key or os.getenv("POLYGON_API_KEY")
    if not api_key:
        raise SystemExit("error: missing Polygon API key")

    output_path = Path(args.output).expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows_by_ticker: dict[tuple[str, str], list[str]] = {}
    next_url: str | None = build_initial_url(api_key=api_key, limit=args.page_limit)
    fetched_pages = 0

    while next_url:
        payload = fetch_json(next_url)
        fetched_pages += 1

        for result in payload.get("results", []):
            row = to_row(result)
            if row is None:
                continue
            key = (row[2], row[0])
            rows_by_ticker[key] = row

        next_url = payload.get("next_url")
        if next_url:
            separator = "&" if "?" in next_url else "?"
            next_url = f"{next_url}{separator}apiKey={urllib.parse.quote(api_key)}"

    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(OUTPUT_COLUMNS)
        for _, row in sorted(rows_by_ticker.items(), key=lambda item: (item[0][0], item[0][1])):
            writer.writerow(row)

    print(f"Wrote {len(rows_by_ticker)} rows to {output_path} across {fetched_pages} pages")
    return 0


def build_initial_url(api_key: str, limit: int) -> str:
    query = urllib.parse.urlencode({
        "market": "stocks",
        "active": "true",
        "order": "asc",
        "sort": "ticker",
        "limit": min(limit, 1000),
        "apiKey": api_key,
    })
    return f"https://api.polygon.io/v3/reference/tickers?{query}"


def fetch_json(url: str) -> dict:
    attempts = 0
    while True:
        attempts += 1
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/json",
                "User-Agent": "folo-stock-master-builder/1.0",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code not in {429, 500, 502, 503, 504} or attempts >= 10:
                raise
            retry_after = error.headers.get("Retry-After")
            wait_seconds = float(retry_after) if retry_after else min(60.0, 2 ** attempts)
            time.sleep(wait_seconds)


def to_row(result: dict) -> list[str] | None:
    ticker = normalize_text(result.get("ticker"))
    name = truncate_text(normalize_text(result.get("name")), 100)
    primary_exchange = normalize_text(result.get("primary_exchange"))
    if not ticker or not name or primary_exchange not in EXCHANGE_MARKET_MAP:
        return None

    market, primary_exchange_code = EXCHANGE_MARKET_MAP[primary_exchange]
    security_type = normalize_text(result.get("type")).upper()
    name_upper = name.upper()
    asset_type = (
        "ETF"
        if security_type in ETF_TYPE_MARKERS
        or " ETF" in name_upper
        or "ETN" in name_upper
        else "STOCK"
    )

    currency_code = normalize_text(result.get("currency_symbol")).upper() or "USD"
    source_identifier = truncate_text(
        normalize_text(result.get("composite_figi")) or ticker,
        100,
    )
    active = "true" if result.get("active") is not False else "false"

    return [
        ticker.upper(),
        name,
        market,
        asset_type,
        primary_exchange_code,
        currency_code,
        source_identifier,
        active,
    ]


def normalize_text(value: object) -> str:
    if value is None:
        return ""
    return str(value).strip()


def truncate_text(value: str, limit: int) -> str:
    if len(value) <= limit:
        return value
    return value[:limit].rstrip()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
    except Exception as error:  # pragma: no cover - script entrypoint
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
