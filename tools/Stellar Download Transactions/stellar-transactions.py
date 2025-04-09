import requests
import pandas as pd
from datetime import datetime

# === CONFIGURATION ===
WALLET_ADDRESS = "GA2IC6PXCE7KXVGDECK3W3WZZSVHFCEYIUIPVNAINMPEYHHFB2GE7U2B"
HORIZON_URL = "https://horizon.stellar.org"
YEAR = 2024
OUTPUT_FILE = "stellar_trades_tax.csv"
MAX_PAGES = 200
LIMIT = 200

START_DATE = datetime(YEAR, 1, 1)
END_DATE = datetime(YEAR, 12, 31, 23, 59, 59)

# === HELPER FUNCTIONS ===
def is_within_tax_year(ledger_time_str):
    dt = datetime.strptime(ledger_time_str, "%Y-%m-%dT%H:%M:%SZ")
    return START_DATE <= dt <= END_DATE

def parse_price(price_obj):
    if isinstance(price_obj, dict) and 'n' in price_obj and 'd' in price_obj:
        return int(price_obj['n']) / int(price_obj['d'])
    return None

def fetch_xlm_usd_price(date_obj):
    date_str = date_obj.strftime("%d-%m-%Y")
    url = f"https://api.coingecko.com/api/v3/coins/stellar/history?date={date_str}"
    try:
        r = requests.get(url)
        if r.status_code == 200:
            data = r.json()
            return data.get('market_data', {}).get('current_price', {}).get('usd')
    except Exception:
        return None

def get_trades(wallet_address):
    trades = []
    url = f"{HORIZON_URL}/accounts/{wallet_address}/trades"
    params = {"limit": LIMIT, "order": "desc"}
    page = 0
    records = []
    last_cursor = None

    while url and page < MAX_PAGES:
        response = requests.get(url, params=params)
        if response.status_code != 200:
            print(f"[ERROR] HTTP {response.status_code} - {response.text}")
            break

        data = response.json()
        records = data['_embedded']['records']

        if records:
            times = [datetime.strptime(trade['ledger_close_time'], "%Y-%m-%dT%H:%M:%SZ") for trade in records]
            min_time = min(times).strftime('%Y-%m-%d %H:%M:%S')
            max_time = max(times).strftime('%Y-%m-%d %H:%M:%S')
            print(f"[DEBUG] Fetching page {page + 1} | {len(records)} trades | Dates: {min_time} to {max_time}")
        else:
            next_url = data['_links']['next']['href']
            ledger_seq = next_url.split("cursor=")[-1].split("-")[0]
            # Try to get ledger close time from /ledgers/{seq}
            try:
                ledger_resp = requests.get(f"{HORIZON_URL}/ledgers/{ledger_seq}")
                if ledger_resp.status_code == 200:
                    ledger_time = ledger_resp.json().get("closed_at", "unknown")
                    print(f"[DEBUG] Fetching page {page + 1} | No trades | Approx ledger {ledger_seq} closed at {ledger_time}")
                else:
                    print(f"[DEBUG] Fetching page {page + 1} | No trades | Approx ledger {ledger_seq} (date unknown)")
            except:
                print(f"[DEBUG] Fetching page {page + 1} | No trades | Approx ledger {ledger_seq} (error fetching date)")

        for trade in records:
            if is_within_tax_year(trade['ledger_close_time']):
                trades.append(trade)
            else:
                return trades  # Stop on first trade outside target year

        # Extract cursor to detect pagination loop
        next_url = data['_links']['next']['href']
        current_cursor = next_url.split("cursor=")[-1].split("&")[0]
        if current_cursor == last_cursor:
            print(f"[DEBUG] Cursor {current_cursor} has not changed — breaking to avoid infinite loop.")
            break
        last_cursor = current_cursor

        url = next_url
        params = {}
        page += 1


    return trades

# === MAIN PROCESSING ===
def main():
    raw_trades = get_trades(WALLET_ADDRESS)
    if not raw_trades:
        print("No trades found for the specified year.")
        return

    df = pd.DataFrame(raw_trades)

    df['timestamp'] = pd.to_datetime(df['ledger_close_time'])
    df['date'] = df['timestamp'].dt.strftime('%-m/%-d/%Y %H:%M:%S')
    df['event'] = df['base_is_seller'].apply(lambda x: 'SELL' if x else 'BUY')
    df['asset'] = df.apply(
        lambda row: row['base_asset_code'] if row['event'] == 'SELL' else row['counter_asset_code'],
        axis=1
    )
    df['amount'] = df.apply(
        lambda row: row['base_amount'] if row['event'] == 'SELL' else row['counter_amount'],
        axis=1
    )
    df['price_float'] = df['price'].apply(parse_price)

    # Estimate XLM equivalent for USD value calc
    df['xlm_equivalent'] = df.apply(
        lambda row: row['base_amount'] if row['base_asset_type'] == 'native' and row['base_is_seller']
        else row['counter_amount'] if row['counter_asset_type'] == 'native' and not row['base_is_seller']
        else None, axis=1)

    # Fetch XLM/USD prices for each date
    unique_dates = sorted(df['timestamp'].dt.date.unique())
    print("[DEBUG] Fetching historical XLM/USD prices...")
    price_map = {date: fetch_xlm_usd_price(date) for date in unique_dates}
    df['xlm_usd'] = df['timestamp'].dt.date.map(price_map)

    # Ensure amount and price are numeric for multiplication
    df['amount'] = pd.to_numeric(df['amount'], errors='coerce')
    df['price_float'] = pd.to_numeric(df['price_float'], errors='coerce')
    df['xlm_equivalent'] = pd.to_numeric(df['xlm_equivalent'], errors='coerce')
    df['xlm_usd'] = pd.to_numeric(df['xlm_usd'], errors='coerce')
    
    # Calculate value
    df['value'] = df['xlm_equivalent'] * df['xlm_usd']
    df['value'] = df['value'].fillna(df['price_float'] * df['amount'])

    # Get operation links
    df['operation_href'] = df['_links'].apply(lambda x: x['operation']['href'])

    # Extract operation ID and use it for better traceability
    df['transaction_id'] = df['operation_href'].str.extract(r'/operations/(\d+)')[0]

    # Build working Stellar Expert link for each operation
    df['link'] = df['transaction_id'].apply(
        lambda op_id: f"https://stellar.expert/explorer/public/op/{op_id}" if pd.notnull(op_id) else ''
    )

    # Output final tax format
    tax_df = df[['date', 'event', 'asset', 'amount', 'value', 'transaction_id', 'link']]
    tax_df.to_csv(OUTPUT_FILE, index=False)
    print(f"[✓] Tax-friendly CSV saved to {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
