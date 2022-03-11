package com.demod.crypto.evm;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import com.google.common.base.Preconditions;

//Find some at https://rpc.info/
public class RPC {
	private static Map<String, RPC> rpcByName;

	public static synchronized RPC byName(String name) {
		if (rpcByName == null) {
			loadRPCFile();
		}

		return rpcByName.get(name);
	}

	public static synchronized String[] getNames() {
		if (rpcByName == null) {
			loadRPCFile();
		}

		return rpcByName.keySet().stream().sorted().toArray(String[]::new);
	}

	private static void loadRPCFile() {
		try {
			File jsonFile = new File("data/rpc.json");
			Preconditions.checkState(jsonFile.exists());

			JSONObject json = new JSONObject(Files.readString(jsonFile.toPath()));

			JSONObject evmJson = json.getJSONObject("evm");
			Map<String, RPC> map = new HashMap<>();
			for (String name : evmJson.keySet()) {
				JSONObject rpcJson = evmJson.getJSONObject(name);
				map.put(name, new RPC(//
						name, //
						rpcJson.getString("rpc-url"), //
						rpcJson.getInt("chain-id"), //
						rpcJson.getString("currency-symbol"), //
						rpcJson.getInt("currency-decimals"), //
						rpcJson.getString("explorer-url"), //
						rpcJson.getInt("batch-size")//
				));
			}
			rpcByName = map;
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private final String name;
	private final String rpcUrl;
	private final int chainId;
	private final String currencySymbol;
	private final int currencyDecimals;
	private final String explorerUrl;
	private final int batchSize;

	private RPC(String name, String rpcUrl, int chainId, String currencySymbol, int currencyDecimals,
			String explorerUrl, int batchSize) {
		this.name = name;
		this.rpcUrl = rpcUrl;
		this.chainId = chainId;
		this.currencySymbol = currencySymbol;
		this.currencyDecimals = currencyDecimals;
		this.explorerUrl = explorerUrl;
		this.batchSize = batchSize;
	}

	public Web3j createWeb3() {
		HttpService web3jService = new HttpService(getRpcUrl(),
				HttpService.getOkHttpClientBuilder().readTimeout(120, TimeUnit.SECONDS).build());
		return Web3j.build(web3jService);
	}

	public String fmtBalance(BigInteger balance) {
		return new BigDecimal(balance, 18) + " " + getCurrencySymbol();
	}

	public int getBatchSize() {
		return batchSize;
	}

	public int getChainId() {
		return chainId;
	}

	public int getCurrencyDecimals() {
		return currencyDecimals;
	}

	public String getCurrencySymbol() {
		return currencySymbol;
	}

	public String getExplorerUrl() {
		return explorerUrl;
	}

	public String getName() {
		return name;
	}

	public String getRpcUrl() {
		return rpcUrl;
	}

	public BigDecimal nativeDecimal(BigInteger amountRaw) {
		return new BigDecimal(amountRaw, currencyDecimals);
	}
}
