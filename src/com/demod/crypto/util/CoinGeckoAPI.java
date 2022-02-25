package com.demod.crypto.util;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;

public class CoinGeckoAPI {
	public static final long API_RATE_MS = 60000L / 50L + 100L; // 50 per minute, plus 100ms padding

	private static final DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private static volatile long lastApiMillis = System.currentTimeMillis() - API_RATE_MS;

	private static Multimap<String, String> symbolToID = null;

	private static JSONObject historicalPriceCacheJson = null;

	private static synchronized JSONObject callApi(String urlStr) {
		try {
			long currentMillis = System.currentTimeMillis();
			long needToWait = lastApiMillis + API_RATE_MS - currentMillis;
			if (needToWait >= 5) {
				Uninterruptibles.sleepUninterruptibly(needToWait, TimeUnit.MILLISECONDS);
			}
			lastApiMillis = System.currentTimeMillis();

			return new JSONObject(Resources.toString(new URL(urlStr), Charsets.UTF_8));
		} catch (JSONException | IOException e) {
			System.err.println("URL: " + urlStr);
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	public static BigDecimal getHistoricalPrice(String symbol, LocalDate date) {
		String id = getSymbolId(symbol);
		String dateStr = fmtDate.format(date);

		if (id == null) {
			return null;
		}

		if (historicalPriceCacheJson == null) {
			File file = new File("data/coingecko-historical-price-cache.json");
			if (file.exists()) {
				try {
					historicalPriceCacheJson = new JSONObject(Files.readString(file.toPath()));
				} catch (JSONException | IOException e) {
					e.printStackTrace();
					System.exit(-1);
					return null;
				}
			} else {
				historicalPriceCacheJson = new JSONObject();
			}
		}

		if (historicalPriceCacheJson.has(id)) {
			JSONObject datesJson = historicalPriceCacheJson.getJSONObject(id);
			if (datesJson.has(dateStr)) {
				return datesJson.optBigDecimal(dateStr, null);
			}
		}

		BigDecimal price = getHistoricalPrice_Fetch(id, dateStr);

		JSONObject datesJson;
		if (historicalPriceCacheJson.has(id)) {
			datesJson = historicalPriceCacheJson.getJSONObject(id);
		} else {
			historicalPriceCacheJson.put(id, datesJson = new JSONObject());
		}
		if (price != null) {
			datesJson.put(dateStr, price);
		} else {
			datesJson.put(dateStr, false);
		}
		File file = new File("data/coingecko-historical-price-cache.json");
		try {
			Files.writeString(file.toPath(), historicalPriceCacheJson.toString(2));
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			System.exit(-1);
			return null;
		}

		return price;
	}

	private static BigDecimal getHistoricalPrice_Fetch(String id, String dateStr) {
		String urlStr = "https://api.coingecko.com/api/v3/coins/" + id + "/history?date=" + dateStr;
		JSONObject json = callApi(urlStr);
		try {
			if (!json.has("market_data")) {
				return null;
			}
			return json.getJSONObject("market_data").getJSONObject("current_price").getBigDecimal("usd");
		} catch (JSONException e) {
			System.err.println("Url: " + urlStr);
			System.err.println("Json: " + json.toString());
			throw e;
		}
	}

	private static synchronized String getSymbolId(String symbol) {
		if (symbolToID == null) {
			loadSymbolData();
		}

		Collection<String> ids = symbolToID.get(symbol.toUpperCase());
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("Symbol " + symbol + " has no CoinGecko id!");
		} else if (ids.size() > 1) {
			throw new IllegalArgumentException("Symbol " + symbol + " has multiple ids! "
					+ ids.stream().collect(Collectors.joining(",", "[", "]")));
		}

		return ids.iterator().next();
	}

	public static boolean hasSymbol(String symbol) {
		if (symbolToID == null) {
			loadSymbolData();
		}

		return symbolToID.get(symbol.toUpperCase()).size() > 0;
	}

	private static void loadSymbolData() {
		System.out.println("Loading CoinGecko Symbol Data...");// XXX
		try {
			ArrayListMultimap<String, String> map = ArrayListMultimap.create();

			{
				File file = new File("data/coingecko-coins-list.json");
				Preconditions.checkState(file.exists());
				JSONArray json = new JSONArray(Files.readString(file.toPath()));
				for (int i = 0; i < json.length(); i++) {
					JSONObject coinJson = json.getJSONObject(i);
					String id = coinJson.getString("id");
					String symbol = coinJson.getString("symbol");
					map.put(symbol.toUpperCase(), id);
				}
			}
			{
				File file = new File("data/coingecko-coins-top250.json");
				Preconditions.checkState(file.exists());
				JSONArray json = new JSONArray(Files.readString(file.toPath()));
				for (int i = json.length() - 1; i >= 0; i--) {
					JSONObject coinJson = json.getJSONObject(i);
					String id = coinJson.getString("id");
					String symbol = coinJson.getString("symbol");
					map.removeAll(symbol.toUpperCase());
					map.put(symbol.toUpperCase(), id);
				}
			}
			{
				File file = new File("data/coingecko-symbol-pref.json");
				Preconditions.checkState(file.exists());
				JSONObject json = new JSONObject(Files.readString(file.toPath()));
				for (String symbol : json.keySet()) {
					String id = json.optString(symbol, null);
					map.removeAll(symbol.toUpperCase());
					map.put(symbol.toUpperCase(), id);
				}
			}

			symbolToID = map;

		} catch (JSONException | IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(String[] args) {
		System.out.println(getHistoricalPrice("ETH", LocalDate.of(2020, 1, 1)));
	}
}
