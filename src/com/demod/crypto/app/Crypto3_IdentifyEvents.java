package com.demod.crypto.app;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.demod.crypto.evm.RPC;
import com.demod.crypto.util.CoinGeckoAPI;
import com.demod.crypto.util.TokenTransferSum;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class Crypto3_IdentifyEvents {

	public static void main(String[] args) throws JSONException, IOException {
		int year = 2021;
		RPC rpc = RPC.byName("Polygon");

		System.out.println("RPC: " + rpc.getName() + " -- " + rpc.getRpcUrl());
		System.out.println("\n-----\n");

		File folder = new File("reports/" + year + "/" + rpc.getName());
		Preconditions.checkState(folder.exists());

		File configFile = new File("data/config.json");
		Preconditions.checkState(configFile.exists());
		JSONObject configJson = new JSONObject(Files.readString(configFile.toPath()));

		File jsonFile = new File(folder, "data.json");
		Preconditions.checkState(jsonFile.exists());
		JSONObject json = new JSONObject(Files.readString(jsonFile.toPath()));

		JSONObject walletsJson = configJson.getJSONObject("wallets");
		System.out.println("Wallets: " + walletsJson.toString(2));

		for (String key : ImmutableList.copyOf(walletsJson.keySet())) {
			walletsJson.put(key.toLowerCase(), walletsJson.getString(key));
		}

		JSONObject stableCoinSymbolsJson = configJson.getJSONObject("stablecoin-symbols");
		System.out.println("Stablecoins: " + stableCoinSymbolsJson.toString(2));

		JSONObject excludeSymbolsJson = configJson.getJSONObject("exclude-symbols");
		System.out.println("Exclude: " + excludeSymbolsJson.toString(2));

		JSONObject renameSymbolsJson = configJson.getJSONObject("rename-symbols");
		System.out.println("Rename: " + renameSymbolsJson.toString(2));

		Map<String, BigDecimal> tokenNet = new HashMap<>();

		DateTimeFormatter fmtDateJson = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		DateTimeFormatter fmtDateCsv = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");

		try (PrintWriter pw = new PrintWriter(new File(folder, year + "_" + rpc.getName() + "_taxevents.csv"))) {
			pw.println("Date,Account,Event,Asset,Amount,Value,TransactionID,Target,Url");

			JSONArray transactionsJson = json.getJSONArray("transactions");
			nextTx: for (int i = 0; i < transactionsJson.length(); i++) {
				JSONObject txJson = transactionsJson.getJSONObject(i);

				if (!txJson.getBoolean("success")) {
					continue;
				}

				String hash = txJson.getString("hash");
				String url = txJson.getString("url");
				String txFromAddress = txJson.getString("from");
				String txAddress = txJson.getString("to");
				LocalDateTime timestamp = LocalDateTime.parse(txJson.getString("timestamp"), fmtDateJson);
				LocalDate date = timestamp.toLocalDate();

				JSONArray tokenTransfersJson = txJson.getJSONArray("token-transfers");

				String walletAddress = null;
				if (walletsJson.has(txFromAddress.toLowerCase())) {
					walletAddress = txFromAddress;
				}

				System.out.println("(" + (i + 1) + "/" + transactionsJson.length() + ") " + hash);// XXX

				String txTarget = "";
				Map<String, TokenTransferSum> tokenSums = new LinkedHashMap<>();
				for (int j = 0; j < tokenTransfersJson.length(); j++) {
					JSONObject ttJson = tokenTransfersJson.getJSONObject(j);

					String ttSymbol = ttJson.getString("token-symbol");
					ttSymbol = renameSymbolsJson.optString(ttSymbol, ttSymbol);
					boolean ttIsStableCoin = stableCoinSymbolsJson.optBoolean(ttSymbol);
					BigDecimal ttAmount = ttJson.getBigDecimal("amount");

					String fromAddress = ttJson.getString("from-address");
					String fromAddressAlias = ttJson.optString("from-address-alias", fromAddress);
					boolean fromMyWallet = walletsJson.has(fromAddress.toLowerCase());

					String toAddress = ttJson.getString("to-address");
					String toAddressAlias = ttJson.optString("to-address-alias", toAddress);
					boolean toMyWallet = walletsJson.has(toAddress.toLowerCase());

					System.out.println("\t\t" + ttAmount.toPlainString() + " " + ttSymbol + "(" + ttIsStableCoin
							+ ") \t" + fromAddressAlias + "(" + fromMyWallet + ") ==> " + toAddressAlias + "("
							+ toMyWallet + ")");// XXX

					// XXX probably should consider a better way of handling these
					if (excludeSymbolsJson.optBoolean(ttSymbol)) {
						continue nextTx;// Excluded by blacklist
					}

					if (fromAddress.equalsIgnoreCase(txAddress)) {
						txTarget = fromAddressAlias;
					}
					if (toAddress.equalsIgnoreCase(txAddress)) {
						txTarget = toAddressAlias;
					}

					if (fromMyWallet) {// Outgoing
						if (walletAddress == null) {
							walletAddress = toAddress;
						}
						tokenNet.put(ttSymbol, tokenNet.getOrDefault(ttSymbol, BigDecimal.ZERO).subtract(ttAmount));
						TokenTransferSum tts = tokenSums.get(ttSymbol);
						if (tts == null) {
							tokenSums.put(ttSymbol, tts = new TokenTransferSum(date, ttIsStableCoin, ttSymbol));
						}
						tts.amountOut(ttAmount);
					}

					if (toMyWallet) {// Incoming
						if (walletAddress == null) {
							walletAddress = toAddress;
						}
						tokenNet.put(ttSymbol, tokenNet.getOrDefault(ttSymbol, BigDecimal.ZERO).add(ttAmount));
						TokenTransferSum tts = tokenSums.get(ttSymbol);
						if (tts == null) {
							tokenSums.put(ttSymbol, tts = new TokenTransferSum(date, ttIsStableCoin, ttSymbol));
						}
						tts.amountIn(ttAmount);
					}

				}

				// Remove any that have cancelled out to zero amount
				for (Entry<String, TokenTransferSum> entry : ImmutableList.copyOf(tokenSums.entrySet())) {
					TokenTransferSum tts = entry.getValue();
					if (tts.isZero()) {
						tokenSums.remove(entry.getKey());
					}
				}

				String dateStrCsv = fmtDateCsv.format(timestamp);
				String account;
				if (walletAddress != null) {
					account = walletsJson.optString(walletAddress.toLowerCase(), walletAddress);
				} else {
					account = "";
				}

				List<TokenTransferSum> tokenList = ImmutableList.copyOf(tokenSums.values());
				if (tokenList.size() == 2 && TokenTransferSum.isBuySell(tokenList.get(0), tokenList.get(1))) {
					TokenTransferSum tts1 = tokenList.get(0);
					TokenTransferSum tts2 = tokenList.get(1);
					TokenTransferSum stablecoin, asset;
					if (tts1.isStablecoin()) {
						stablecoin = tts1;
						asset = tts2;
					} else {
						stablecoin = tts2;
						asset = tts1;
					}
					String line = dateStrCsv + "," + account + "," + (asset.isIncoming() ? "BUY" : "SELL") + ","
							+ asset.getSymbol() + "," + asset.getAmount().abs().toPlainString() + ","
							+ stablecoin.getValue().get().toPlainString() + "," + hash + "," + txTarget + "," + url;
					pw.println(line);
					System.out.println("\t -- " + line);

				} else if (tokenList.size() == 2 && TokenTransferSum.isSwap(tokenList.get(0), tokenList.get(1))) {
					TokenTransferSum tts1 = tokenList.get(0);
					TokenTransferSum tts2 = tokenList.get(1);
					TokenTransferSum sellAsset, buyAsset;
					if (tts1.isOutgoing()) {
						sellAsset = tts1;
						buyAsset = tts2;
					} else {
						sellAsset = tts2;
						buyAsset = tts1;
					}
					TokenTransferSum.matchValue(tts1, tts2);
					String line = dateStrCsv + "," + account + ",SELL," + sellAsset.getSymbol() + ","
							+ sellAsset.getAmount().abs().toPlainString() + ","
							+ sellAsset.getValue().get().toPlainString() + "," + hash + "," + txTarget + "," + url;
					pw.println(line);
					System.out.println("\t -- " + line);
					line = dateStrCsv + "," + account + ",BUY," + buyAsset.getSymbol() + ","
							+ buyAsset.getAmount().abs().toPlainString() + ","
							+ buyAsset.getValue().get().toPlainString() + "," + hash + "," + txTarget + "," + url;
					pw.println(line);
					System.out.println("\t -- " + line);

				} else {
					for (TokenTransferSum asset : tokenList) {
						String line = dateStrCsv + "," + account + "," + (asset.isIncoming() ? "DEPOSIT" : "WITHDRAW")
								+ "," + asset.getSymbol() + "," + asset.getAmount().abs().toPlainString() + ","
								+ asset.getValue().map(BigDecimal::toPlainString).orElse("") + "," + hash + ","
								+ txTarget + "," + url;
						pw.println(line);
						System.out.println("\t -- " + line);
					}
				}

				String feeAccount = walletsJson.optString(txJson.getString("from").toLowerCase(), null);
				if (feeAccount != null) {
					String feeSymbol = txJson.getString("native-symbol");
					BigDecimal feeAmount = txJson.getBigDecimal("fee");
					BigDecimal feeValue = CoinGeckoAPI.getHistoricalPrice(feeSymbol, timestamp.toLocalDate())
							.multiply(feeAmount);

					String line = dateStrCsv + "," + feeAccount + ",FEE," + feeSymbol + "," + feeAmount.toPlainString()
							+ "," + feeValue.toPlainString() + "," + hash + "," + txTarget + "," + url;
					pw.println(line);
					System.out.println("\t -- " + line);
				}

				pw.println();
			}

		}

		System.out.println("Buy/Sell Net:");
		tokenNet.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue()))
				.forEach(e -> System.out.println("\t" + e.getKey() + ": " + e.getValue().toPlainString()));
	}

}
