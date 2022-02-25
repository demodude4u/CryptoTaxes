package com.demod.crypto.app;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.demod.crypto.evm.RPC;
import com.demod.crypto.evm.Web3Helper;
import com.demod.crypto.explorer.BlockExplorerHelper;
import com.demod.crypto.explorer.TokenTransfer;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Uninterruptibles;

public class Crypto2_ScrapeTokens {

	public static void main(String[] args) throws JSONException, IOException {
		int year = 2020;
		RPC rpc = RPC.byName("Ethereum");

		Web3j web3 = rpc.createWeb3();
		BlockExplorerHelper explorerHelper = new BlockExplorerHelper(rpc);

		System.out.println("RPC: " + rpc.getName() + " -- " + rpc.getRpcUrl());
		System.out.println("\n-----\n");

		File folder = new File("reports/" + year + "/" + rpc.getName());
		Preconditions.checkState(folder.exists());

		File transactionsCsvFile = new File(folder, "transactions.csv");
		Preconditions.checkState(transactionsCsvFile.exists());

		File jsonFile = new File(folder, "data.json");
		Preconditions.checkState(jsonFile.exists());

		JSONObject json = new JSONObject(Files.readString(jsonFile.toPath()));
		List<String> txHashes = Files.readAllLines(transactionsCsvFile.toPath());

		DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		JSONArray transactionsJson = new JSONArray();
		for (int i = 0; i < txHashes.size(); i++) {
			String txHash = txHashes.get(i);
			System.out.println("(" + (i + 1) + "/" + txHashes.size() + ") " + txHash);

			// txHash =
			// "0x868d764312553ecef95cfd4cc301d9864ea69abaa7178207d8a3e3634640bb17";// XXX

			Transaction tx;
			TransactionReceipt receipt;
			Block block;
			while (true) {
				try {
					tx = web3.ethGetTransactionByHash(txHash).send().getTransaction().get();
					receipt = web3.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().get();
					block = web3.ethGetBlockByHash(tx.getBlockHash(), false).send().getBlock();
					break;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Trying again in 15 seconds...");
					Uninterruptibles.sleepUninterruptibly(15, TimeUnit.SECONDS);
					web3.shutdown();
					web3 = rpc.createWeb3();
				}
			}

			List<TokenTransfer> tokenTransfers = null;
			while (true) {
				try {
					tokenTransfers = explorerHelper.fetchTokenTransfers(txHash);
					break;
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Trying again in 5 seconds...");
					Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
				}
			}

			JSONObject txJson = new JSONObject();
			terribleHackToHaveOrderedJSONObject(txJson);

			txJson.put("hash", tx.getHash());
			txJson.put("success", receipt.isStatusOK());
			txJson.put("url", rpc.getExplorerUrl() + "/tx/" + tx.getHash());
			txJson.put("timestamp", fmtDate.format(Web3Helper.convertTimestamp(block.getTimestamp())));
			txJson.put("from", tx.getFrom());
			txJson.put("to", tx.getTo());
			txJson.put("input", tx.getInput());
			txJson.put("native-symbol", rpc.getCurrencySymbol());
			txJson.put("value", rpc.nativeDecimal(tx.getValue()));
			txJson.put("fee", rpc.nativeDecimal(tx.getGasPrice().multiply(receipt.getGasUsed())));

			if (!tx.getValue().equals(BigInteger.ZERO)) {
				TokenTransfer tt = new TokenTransfer();
				tt.fromAddress = tx.getFrom();
				tt.toAddress = tx.getTo();
				tt.amount = rpc.nativeDecimal(tx.getValue());
				tt.tokenSymbol = rpc.getCurrencySymbol();
				tokenTransfers.add(tt);
			}

			JSONArray tokenTransfersJson = new JSONArray();
			for (TokenTransfer tokenTransfer : tokenTransfers) {
				JSONObject tokenTransferJson = new JSONObject();
				tokenTransferJson.put("from-address", tokenTransfer.fromAddress);
				tokenTransferJson.put("from-address-alias", tokenTransfer.fromAddressAlias);
				tokenTransferJson.put("to-address", tokenTransfer.toAddress);
				tokenTransferJson.put("to-address-alias", tokenTransfer.toAddressAlias);
				tokenTransferJson.put("amount", tokenTransfer.amount);
				tokenTransferJson.put("amount-current-USD", tokenTransfer.amountCurrentUSD);
				tokenTransferJson.put("token-symbol", tokenTransfer.tokenSymbol);
				tokenTransferJson.put("token-name", tokenTransfer.tokenName);
				tokenTransferJson.put("token-address", tokenTransfer.tokenAddress);

				tokenTransfersJson.put(tokenTransferJson);
			}
			txJson.put("token-transfers", tokenTransfersJson);

			transactionsJson.put(txJson);

			if (tokenTransfers.size() > 0) {
				System.out.println("\t" + tokenTransfers.size() + " Token Transfers");
			}

//			System.out.println(txJson.toString(2));
//			System.exit(0);
		}
		json.put("transactions", transactionsJson);

		Files.writeString(jsonFile.toPath(), json.toString(2));
	}

	public static void terribleHackToHaveOrderedJSONObject(JSONObject json) {
		try {
			Field map = json.getClass().getDeclaredField("map");
			map.setAccessible(true);// because the field is private final...
			map.set(json, new LinkedHashMap<>());
			map.setAccessible(false);// return flag
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(0); // Oh well...
		}
	}

}
