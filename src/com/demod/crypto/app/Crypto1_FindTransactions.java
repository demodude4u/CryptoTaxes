package com.demod.crypto.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;

import com.demod.crypto.evm.RPC;
import com.demod.crypto.evm.Web3Helper;
import com.demod.crypto.evm.Web3Helper.FindTransactionsResult;
import com.demod.crypto.util.ConsoleArgs;
import com.google.common.base.Preconditions;
import com.google.common.collect.ContiguousSet;

public class Crypto1_FindTransactions {

	private static JSONObject loadJson(File jsonFile) {
		try {
			return new JSONObject(Files.readString(jsonFile.toPath()));
		} catch (NoSuchFileException e) {
			System.out.println("Creating new json file...");
			return new JSONObject();
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			return new JSONObject();
		}
	}

	public static void main(String[] args) throws IOException {
		int year = ConsoleArgs.argInt("Script1", "Tax Year", args, 0, LocalDate.now().getYear() - 1);
		RPC rpc = RPC.byName(ConsoleArgs.argStringChoice("Script1", "RPC", args, 1, "Ethereum", RPC.getNames()));

		Web3j web3 = rpc.createWeb3();
		Web3Helper web3Helper = new Web3Helper(web3, rpc);

		System.out.println("RPC: " + rpc.getName() + " -- " + rpc.getRpcUrl());

		Web3ClientVersion version = web3.web3ClientVersion().send();
		System.out.println(version.getWeb3ClientVersion());

		System.out.println("\n-----\n");

		File configFile = new File("data/config.json");
		Preconditions.checkState(configFile.exists());
		JSONObject configJson = new JSONObject(Files.readString(configFile.toPath()));

		List<String> accounts = configJson.getJSONObject("wallets").keySet().stream().collect(Collectors.toList());
		for (String account : accounts) {
			EthGetBalance balance = web3.ethGetBalance(account, DefaultBlockParameterName.LATEST).send();
			System.out.println("Balance " + account.substring(0, 6) + ": " + rpc.fmtBalance(balance.getBalance()));
		}

		System.out.println("\n-----\n");

		File folder = new File("reports/" + year + "/" + rpc.getName());
		folder.mkdirs();

		File jsonFile = new File(folder, "data.json");
		JSONObject json = loadJson(jsonFile);

		ContiguousSet<Long> yearRange;
		{
			if (json.has("year-range")) {
				JSONObject yearRangeJson = json.getJSONObject("year-range");
				yearRange = Web3Helper.contiguousSet(yearRangeJson.getLong("start"), yearRangeJson.getLong("end"));
			} else {
				yearRange = web3Helper.findBlockRangeForYear(year);

				JSONObject yearRangeJson = new JSONObject();
				yearRangeJson.put("start", yearRange.first().toString());
				yearRangeJson.put("end", yearRange.last().toString());
				json.put("year-range", yearRangeJson);
				saveJson(jsonFile, json);
			}
		}

		System.out.println(yearRange + " " + yearRange.size());

		{
			ContiguousSet<Long> searchRange;
			if (json.has("lastProcessedBlock")) {
				long lastProcessedBlock = json.getLong("lastProcessedBlock");
				if (lastProcessedBlock >= yearRange.last()) {
					System.out.println("Transactions have already been processed! " + lastProcessedBlock + " >= "
							+ yearRange.last());
					return;
				}
				searchRange = Web3Helper.contiguousSet(lastProcessedBlock + 1, yearRange.last());
				System.out.println("Searching partial year... " + searchRange.first());
			} else {
				searchRange = yearRange;
				System.out.println("Searching full year...");
			}

			Thread forceStopThread = new Thread(() -> {
				try {
					System.out.println("Press <enter> to force stop.");
					System.in.read();
					System.out.println("Force stop detected!");
					web3Helper.forceStop();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			forceStopThread.setDaemon(true);
			forceStopThread.start();

			FindTransactionsResult result = web3Helper.findTransactionsWithAccounts(searchRange, accounts,
					rpc.getBatchSize());

			if (result.lastProcessedBlock != -1) {
				json.put("lastProcessedBlock", result.lastProcessedBlock);
			}

			if (result.partialResult) {
				if (result.lastProcessedBlock != -1) {
					System.out.println("Saving partial results at " + result.lastProcessedBlock);
				} else {
					System.out.println("Did not fetch any results!");
				}
			} else {
				System.out.println("Search complete!");
			}

			try (PrintWriter pw = new PrintWriter(new FileOutputStream(new File(folder, "transactions.csv"), true))) {
				for (Entry<Block, Transaction> entry : result.foundTransactions) {
					Transaction tx = entry.getValue();
					pw.println(tx.getHash());
				}
			}

			Files.writeString(jsonFile.toPath(), json.toString(2));

			System.out.println("Located " + result.foundTransactions.size() + " transactions.");
		}
	}

	private static void saveJson(File jsonFile, JSONObject json) {
		try {
			Files.writeString(jsonFile.toPath(), json.toString(2));
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			System.out.println("Failed to save json file!");
		}
	}

}
