package com.demod.crypto.app;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.demod.crypto.tax.LotStrategy;
import com.demod.crypto.tax.TaxEvent;
import com.demod.crypto.tax.TaxEvent.TaxEventType;
import com.demod.crypto.tax.TaxLot;
import com.demod.crypto.tax.TaxLot.AccrualType;
import com.demod.crypto.tax.TaxLot.DisposeType;
import com.demod.crypto.util.CoinGeckoAPI;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class Crypto4_GenerateTaxes {

	private static final String[] EXPECTED_HEADERS = { "Date", "Account", "Event", "Asset", "Amount", "Value",
			"TransactionID" };

	private static final List<TaxEventType> SORT_TYPE_ORDER = Arrays.asList(new TaxEventType[] { TaxEventType.FEE,
			TaxEventType.DEPOSIT, TaxEventType.SELL, TaxEventType.BUY, TaxEventType.REWARD, TaxEventType.WITHDRAW });

	private static final DateTimeFormatter FMT_DATE_CSV = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss");

	private static TaxLot createUnknownLot(TaxEvent event, BigDecimal amount) {
		return new TaxLot(AccrualType.UNKNOWN,
				new TaxEvent(event.getDateTime(), event.getAccount(), TaxEventType.UNKNOWN, event.getAsset(), amount,
						BigDecimal.ZERO, event.getTransactionId(), event.getExtraData(), event.getOriginFile(),
						event.getOriginFileLineNumber()),
				event.getDateTime(), amount, BigDecimal.ZERO);
	}

	public static void main(String[] args) throws IOException {
		int year = 2021;
		LotStrategy lotStrategy = LotStrategy.LGUT;
		boolean rewardAsIncome = false;

		if (args.length == 3) {
			year = Integer.parseInt(args[0]);
			lotStrategy = LotStrategy.valueOf(args[1]);
			rewardAsIncome = Boolean.parseBoolean(args[2]);
		}

		File dataFolder = new File("data/" + year);
		Preconditions.checkState(dataFolder.exists());

		File configFile = new File("data/config.json");
		Preconditions.checkState(configFile.exists());
		JSONObject configJson = new JSONObject(Files.readString(configFile.toPath()));

		JSONObject stableCoinSymbolsJson = configJson.getJSONObject("stablecoin-symbols");
		JSONObject excludeSymbolsJson = configJson.getJSONObject("exclude-symbols");
		JSONObject renameSymbolsJson = configJson.getJSONObject("rename-symbols");

		System.out.println("Loading Events for year " + year + "...");

		List<TaxEvent> allEvents = new ArrayList<>();

		// Load all events from all csv files
		for (File file : dataFolder.listFiles()) {
			if (file.getName().endsWith(".csv")) {
				System.out.println("\t" + file.getName());
				List<String> lines = Files.readAllLines(file.toPath());

				String[] headers = lines.remove(0).split(",");
				for (int i = 0; i < headers.length; i++) {
					headers[i] = headers[i].trim();
				}

				int mismatchIndex = Arrays.mismatch(headers, EXPECTED_HEADERS);
				Verify.verify(mismatchIndex == EXPECTED_HEADERS.length || mismatchIndex == -1,
						"Incorrect headers: " + Arrays.toString(headers));

				String originFile = file.getName();
				int originLineNumber = 1;
				for (String line : lines) {
					originLineNumber++;
					System.out.println("\t\t" + originFile + "(Line " + originLineNumber + "): " + line);
					if (line.isBlank()) {
						continue;
					}
					String[] cells = line.split(",");
					if (cells.length < 7) {
						cells = Arrays.copyOf(cells, 7);
						for (int i = 0; i < cells.length; i++) {
							if (cells[i] == null) {
								cells[i] = "";
							}
						}
					}
					if (cells[0].isBlank()) {// This can happen when processing another export but keeping the original
												// in the extra data
						continue;
					}
					LocalDateTime date = LocalDateTime.parse(cells[0].trim(), FMT_DATE_CSV);
					String account = cells[1].trim();
					TaxEventType type = TaxEventType.valueOf(cells[2].trim());
					String asset = cells[3].trim();
					BigDecimal amount = new BigDecimal(cells[4].trim());
					BigDecimal value;
					if (!cells[5].isBlank()) {
						value = new BigDecimal(cells[5].trim());
					} else {
						BigDecimal price = CoinGeckoAPI.getHistoricalPrice(asset, date.toLocalDate());
						if (price == null) {
							value = BigDecimal.ZERO;
						} else {
							value = price.multiply(amount);
						}
					}
					String transactionId = cells[6].trim();
					Map<String, String> extraData = new LinkedHashMap<>();
					for (int i = 7; i < cells.length; i++) {
						extraData.put(headers[i], cells[i].trim());
					}

					asset = renameSymbolsJson.optString(asset, asset);

					if (stableCoinSymbolsJson.optBoolean(asset)) {
						continue;
					}
					if (excludeSymbolsJson.optBoolean(asset)) {
						continue;
					}

					allEvents.add(new TaxEvent(date, account, type, asset, amount, value, transactionId, extraData,
							originFile, originLineNumber));
				}
			}
		}

		// Ensure all transactions have the same datetime
		allEvents.stream().collect(Collectors.groupingBy(e -> e.getTransactionId())).forEach((txId, events) -> {
			if (txId == null || txId.isBlank()) {
				return;
			}
			// Set all to the earliest dateTime
			LocalDateTime dateTime = events.stream().map(e -> e.getDateTime()).sorted().findFirst().get();
			for (TaxEvent event : events) {
				Verify.verify(Duration.between(dateTime, event.getDateTime()).getSeconds() < 60 * 60,
						"DateTime too different! " + FMT_DATE_CSV.format(dateTime) + " " + event);
				event.setDateTime(dateTime);
			}
		});

		// Sort by datetime, and then by type order
		allEvents.sort(Comparator.<TaxEvent, LocalDateTime>comparing(e -> e.getDateTime())
				.thenComparingInt(e -> SORT_TYPE_ORDER.indexOf(e.getType())));

		System.out.println("Events Loaded: " + allEvents.size());

		System.out.println("\n-----------------------------\n");

		ListMultimap<String, TaxLot> allLots = ArrayListMultimap.create();
		ListMultimap<String, TaxLot> allSoldLots = ArrayListMultimap.create();
		ListMultimap<String, TaxLot> allPendingLots = ArrayListMultimap.create();

		File strategyFolder = new File("reports/" + year + "/taxes/" + lotStrategy.name());
		strategyFolder.mkdirs();

		try (PrintWriter pw = new PrintWriter(new File(strategyFolder, year + "_" + lotStrategy.name() + "_log.csv"))) {
			pw.println("Date,Type,Asset,Amount,Cost Basis,Proceeds,Net Profit,Age,Buy ID,Sell ID");

			File assetsFolder = new File(strategyFolder, "assets");
			assetsFolder.mkdir();
			Map<String, PrintWriter> assetWriters = new HashMap<>();

			BiConsumer<PrintWriter, TaxLot> printDisposalRow = (pwc, l) -> {
				Verify.verify(l.isDisposed());
				BigDecimal netProfit = l.getProceeds().subtract(l.getCostBasis());
				long days = Duration.between(l.getBuyEvent().getDateTime(), l.getDisposeEvent().getDateTime()).toDays();
				pwc.println(FMT_DATE_CSV.format(l.getDisposeEvent().getDateTime()) + "," + l.getDisposeType().name()
						+ "," + l.getBuyEvent().getAsset() + "," + l.getAmount().toPlainString() + ","
						+ l.getCostBasis().setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ l.getProceeds().setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ netProfit.setScale(2, RoundingMode.HALF_UP).toPlainString() + "," + days + ","
						+ l.getBuyEvent().getId() + "," + l.getDisposeEvent().getId());
			};
			BiConsumer<PrintWriter, TaxLot> printAccrualRow = (pwc, l) -> {
				pwc.println(FMT_DATE_CSV.format(l.getBuyEvent().getDateTime()) + "," + l.getAccrualType().name() + ","
						+ l.getBuyEvent().getAsset() + "," + l.getAmount().toPlainString() + ","
						+ l.getCostBasis().setScale(2, RoundingMode.HALF_UP).toPlainString() + ",,,,"
						+ l.getBuyEvent().getId() + ",");
			};

			for (TaxEvent event : allEvents) {
				List<TaxLot> pendingLots = allPendingLots.get(event.getAsset());

				String asset = event.getAsset();
				PrintWriter assetWriter = assetWriters.get(asset);
				if (assetWriter == null) {
					assetWriter = new PrintWriter(new File(assetsFolder,
							year + "_" + lotStrategy.name() + "_" + asset.replaceAll("\\W+", "") + "_log.csv"));
					assetWriter.println("Date,Type,Asset,Amount,Cost Basis,Proceeds,Net Profit,Age,Buy ID,Sell ID");
					assetWriters.put(asset, assetWriter);
				}

				if (event.getType() == TaxEventType.CARRYOVER) {
					Verify.verify(event.getDateTime().getYear() < year,
							"Carryover event not before " + year + "! " + event.toString());
				} else {
					Verify.verify(event.getDateTime().getYear() == year,
							"Event is not from " + year + "! " + event.toString());
				}

				switch (event.getType()) {

				// Approach with option #1: amount is removed, but cost basis remains the same
				// https://koinly.io/blog/deducting-crypto-trading-transfer-fees/
				case REMOVED:
				case FEE:
					BigDecimal feeAmount = event.getAmount();
					while (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
						TaxLot pickLot;
						if (pendingLots.size() > 0) {
							pickLot = lotStrategy.pickLot(pendingLots, event.getDateTime(), feeAmount, BigDecimal.ZERO);
						} else {
							pickLot = createUnknownLot(event, feeAmount);
							allLots.put(event.getAsset(), pickLot);
							printAccrualRow.accept(pw, pickLot);
							printAccrualRow.accept(assetWriter, pickLot);
						}

						if (feeAmount.compareTo(pickLot.getAmount()) < 0) {
							pickLot = pickLot.split(feeAmount, BigDecimal.ZERO);
							allLots.put(event.getAsset(), pickLot);
						}

						Verify.verify(pickLot.getAmount().compareTo(feeAmount) <= 0);

						pickLot.setRemoved(event);
						allPendingLots.remove(event.getAsset(), pickLot);
						allSoldLots.put(event.getAsset(), pickLot);
						printDisposalRow.accept(pw, pickLot);
						printDisposalRow.accept(assetWriter, pickLot);

						feeAmount = feeAmount.subtract(pickLot.getAmount());
					}
					break;

				case SELL:
					BigDecimal sellAmount = event.getAmount();
					BigDecimal sellProceeds = event.getValue();
					while (sellAmount.compareTo(BigDecimal.ZERO) > 0) {
						TaxLot sellLot;
						if (pendingLots.size() > 0) {
							sellLot = lotStrategy.pickLot(pendingLots, event.getDateTime(), sellAmount, sellProceeds);
						} else {
							sellLot = createUnknownLot(event, sellAmount);
							allLots.put(event.getAsset(), sellLot);
							printAccrualRow.accept(pw, sellLot);
							printAccrualRow.accept(assetWriter, sellLot);
						}

						if (sellAmount.compareTo(sellLot.getAmount()) < 0) {
							sellLot = sellLot.splitProportionally(sellAmount);
							allLots.put(event.getAsset(), sellLot);
						}

						Verify.verify(sellLot.getAmount().compareTo(sellAmount) <= 0);

						BigDecimal proceeds;
						if (sellLot.getAmount().compareTo(sellAmount) < 0) {
							proceeds = sellLot.getAmount().divide(sellAmount, 18, RoundingMode.HALF_UP)
									.multiply(sellProceeds);
						} else {
							proceeds = sellProceeds;
						}
						sellProceeds = sellProceeds.subtract(proceeds);

						sellLot.setSold(event, proceeds);
						allPendingLots.remove(event.getAsset(), sellLot);
						allSoldLots.put(event.getAsset(), sellLot);
						printDisposalRow.accept(pw, sellLot);
						printDisposalRow.accept(assetWriter, sellLot);

						sellAmount = sellAmount.subtract(sellLot.getAmount());
					}
					Verify.verify(sellProceeds.compareTo(BigDecimal.ZERO) == 0);
					break;

				case BUY:
					TaxLot buyLot = new TaxLot(AccrualType.BUY, event, event.getDateTime(), event.getAmount(),
							event.getValue());
					allPendingLots.put(event.getAsset(), buyLot);
					allLots.put(event.getAsset(), buyLot);
					printAccrualRow.accept(pw, buyLot);
					printAccrualRow.accept(assetWriter, buyLot);
					break;

				case CARRYOVER:
					TaxLot carryoverLot = new TaxLot(AccrualType.CARRYOVER, event, event.getDateTime(),
							event.getAmount(), event.getValue());
					allPendingLots.put(event.getAsset(), carryoverLot);
					allLots.put(event.getAsset(), carryoverLot);
					break;

				case REWARD:
					TaxLot rewardLot;
					if (rewardAsIncome) {
						rewardLot = new TaxLot(AccrualType.INCOME, event, event.getDateTime(), event.getAmount(),
								event.getValue());
					} else {
						rewardLot = new TaxLot(AccrualType.BUY, event, event.getDateTime(), event.getAmount(),
								BigDecimal.ZERO);
					}
					allPendingLots.put(event.getAsset(), rewardLot);
					allLots.put(event.getAsset(), rewardLot);
					printAccrualRow.accept(pw, rewardLot);
					printAccrualRow.accept(assetWriter, rewardLot);
					break;

				case DEPOSIT:
				case WITHDRAW:
				case UNKNOWN:
					// Ignored
					continue;
				}
			}

			assetWriters.forEach((a, pwc) -> pwc.close());
		}

		try (PrintWriter pw = new PrintWriter(
				new File(strategyFolder, year + "_" + lotStrategy.name() + "_summary.csv"))) {
			pw.println("Asset,Income,Short Term,Long Term,Amount EOY " + (year - 1) + ",Amount Unknown,"
					+ "Amount Bought,Amount Rewards,Amount Sold,Amount Fees,Amount EOY " + year + ",Cost Basis EOY "
					+ (year - 1) + "," + "Cost Basis Sold " + year + ",Cost Basis EOY " + year
					+ ",Proceeds,Net Profit");

			List<String> assetOrder = allEvents.stream().map(e -> e.getAsset()).distinct().sorted()
					.collect(Collectors.toList());
			BigDecimal incomeTotal = BigDecimal.ZERO;
			BigDecimal shortTermTotal = BigDecimal.ZERO;
			BigDecimal longTermTotal = BigDecimal.ZERO;
			BigDecimal costBasisLeftoverTotal = BigDecimal.ZERO;
			BigDecimal costBasisTotal = BigDecimal.ZERO;
			BigDecimal costBasisCarryoverTotal = BigDecimal.ZERO;
			BigDecimal proceedsTotal = BigDecimal.ZERO;
			BigDecimal netProfitTotal = BigDecimal.ZERO;
			for (String asset : assetOrder) {
				List<TaxEvent> events = allEvents.stream().filter(e -> e.getAsset().equals(asset))
						.collect(Collectors.toList());
				List<TaxLot> lots = allLots.get(asset);
				List<TaxLot> soldLots = allSoldLots.get(asset);
				List<TaxLot> carryoverLots = allPendingLots.get(asset);

				BigDecimal income = lots.stream().filter(l -> l.getAccrualType() == AccrualType.INCOME)
						.map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO, BigDecimal::add);
				incomeTotal = incomeTotal.add(income);
				BigDecimal shortTerm = soldLots.stream().filter(l -> l.getDisposeType() == DisposeType.SHORT_TERM)
						.map(l -> l.getProceeds().subtract(l.getCostBasis())).reduce(BigDecimal.ZERO, BigDecimal::add);
				shortTermTotal = shortTermTotal.add(shortTerm);
				BigDecimal longTerm = soldLots.stream().filter(l -> l.getDisposeType() == DisposeType.LONG_TERM)
						.map(l -> l.getProceeds().subtract(l.getCostBasis())).reduce(BigDecimal.ZERO, BigDecimal::add);
				longTermTotal = longTermTotal.add(longTerm);

				BigDecimal amountLeftover = lots.stream().filter(l -> l.getAccrualType() == AccrualType.CARRYOVER)
						.map(l -> l.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountUnknown = soldLots.stream()
						.filter(l -> l.getBuyEvent().getType() == TaxEventType.UNKNOWN).map(l -> l.getAmount())
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountBought = events.stream().filter(e -> e.getType() == TaxEventType.BUY)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountRewards = events.stream().filter(e -> e.getType() == TaxEventType.REWARD)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountSold = events.stream().filter(e -> e.getType() == TaxEventType.SELL)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountFees = events.stream().filter(e -> e.getType() == TaxEventType.FEE)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountCarryover = carryoverLots.stream().map(l -> l.getAmount()).reduce(BigDecimal.ZERO,
						BigDecimal::add);

				BigDecimal costBasisLeftover = lots.stream().filter(l -> l.getAccrualType() == AccrualType.CARRYOVER)
						.map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO, BigDecimal::add);
				costBasisLeftoverTotal = costBasisLeftoverTotal.add(costBasisLeftover);
				BigDecimal costBasis = soldLots.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				costBasisTotal = costBasisTotal.add(costBasis);
				BigDecimal costBasisCarryover = carryoverLots.stream().map(l -> l.getCostBasis())
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				costBasisCarryoverTotal = costBasisCarryoverTotal.add(costBasisCarryover);
				BigDecimal proceeds = soldLots.stream().map(l -> l.getProceeds()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				proceedsTotal = proceedsTotal.add(proceeds);
				BigDecimal netProfit = proceeds.subtract(costBasis);
				netProfitTotal = netProfitTotal.add(netProfit);

				pw.println(asset + "," + income.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ shortTerm.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ longTerm.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ amountLeftover.toPlainString() + "," + amountUnknown.toPlainString() + ","
						+ amountBought.toPlainString() + "," + amountRewards.toPlainString() + ","
						+ amountSold.toPlainString() + "," + amountFees.toPlainString() + ","
						+ amountCarryover.toPlainString() + ","
						+ costBasisLeftover.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ costBasis.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ costBasisCarryover.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ proceeds.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ netProfit.setScale(2, RoundingMode.HALF_UP).toPlainString());
			}
			pw.println("(Totals)," + incomeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ shortTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ longTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ",,,,,,,,"
					+ costBasisLeftoverTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ costBasisTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ costBasisCarryoverTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ proceedsTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ netProfitTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());

			System.out.println("Income: $" + incomeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
			System.out.println("Short Term: $" + shortTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
			System.out.println("Long Term: $" + longTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
			System.out.println("Net Profit: $" + netProfitTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
		}

		try (PrintWriter pw = new PrintWriter(
				new File(strategyFolder, year + "_" + lotStrategy.name() + "_carryover.csv"))) {
			pw.println("Date,Account,Event,Asset,Amount,Value,TransactionID,Original Buy ID");
			for (TaxLot pendingLot : allPendingLots.values().stream().sorted(Comparator.comparing(l -> l.getDateTime()))
					.collect(Collectors.toList())) {
				LocalDateTime date = pendingLot.getDateTime();
				String account = pendingLot.getBuyEvent().getAccount();
				String asset = pendingLot.getBuyEvent().getAsset();
				BigDecimal amount = pendingLot.getAmount();
				BigDecimal value = pendingLot.getCostBasis();
				String transactionId = pendingLot.getBuyEvent().getTransactionId();
				pw.println(FMT_DATE_CSV.format(date) + "," + account + ",CARRYOVER," + asset + ","
						+ amount.toPlainString() + "," + value.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ transactionId + "," + pendingLot.getBuyEvent().getId());
			}
		}

		final int year_f = year;
		try (PrintWriter pw = new PrintWriter(
				new File(strategyFolder, year + "_" + lotStrategy.name() + "_monthly.csv"))) {
			pw.println("Asset,Category," + year + ",Jan " + year + ",Feb " + year + ",Mar " + year + ",Apr " + year
					+ ",May " + year + ",Jun " + year + ",Jul " + year + ",Aug " + year + ",Sep " + year + ",Oct "
					+ year + ",Nov " + year + ",Dec " + year);

			List<String> assetOrder = allEvents.stream().map(e -> e.getAsset()).distinct().sorted()
					.collect(Collectors.toList());

			for (String asset : assetOrder) {
				String line = asset + ",Net Profit";

				List<TaxLot> yearSoldLots = allSoldLots.get(asset).stream()
						.filter(l -> l.getDisposeEvent().getDateTime().getYear() == year_f)
						.collect(Collectors.toList());

				BigDecimal yearCostBasis = yearSoldLots.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearProceeds = yearSoldLots.stream().map(l -> l.getProceeds()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearNetProfit = yearProceeds.subtract(yearCostBasis);

				line += "," + yearNetProfit.setScale(2, RoundingMode.HALF_UP).toPlainString();

				for (int month = 1; month <= 12; month++) {
					final int month_f = month;
					List<TaxLot> monthSoldLots = yearSoldLots.stream()
							.filter(l -> l.getDisposeEvent().getDateTime().getMonthValue() == month_f)
							.collect(Collectors.toList());

					BigDecimal monthCostBasis = monthSoldLots.stream().map(l -> l.getCostBasis())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal monthProceeds = monthSoldLots.stream().map(l -> l.getProceeds()).reduce(BigDecimal.ZERO,
							BigDecimal::add);
					BigDecimal monthNetProfit = monthProceeds.subtract(monthCostBasis);

					line += "," + monthNetProfit.setScale(2, RoundingMode.HALF_UP).toPlainString();
				}

				pw.println(line);
			}

			pw.println();
			for (String asset : assetOrder) {
				String line = asset + ",Net Basis";

				List<TaxLot> yearBuyLots = allLots.get(asset).stream().filter(l -> l.getDateTime().getYear() == year_f)
						.collect(Collectors.toList());
				List<TaxLot> yearSoldLots = allSoldLots.get(asset).stream()
						.filter(l -> l.getDisposeEvent().getDateTime().getYear() == year_f)
						.collect(Collectors.toList());

				BigDecimal yearBuyCostBasis = yearBuyLots.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearSellCostBasis = yearSoldLots.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearNetCostBasis = yearBuyCostBasis.subtract(yearSellCostBasis);

				line += "," + yearNetCostBasis.setScale(2, RoundingMode.HALF_UP).toPlainString();

				for (int month = 1; month <= 12; month++) {
					final int month_f = month;
					List<TaxLot> monthBuyLots = yearBuyLots.stream()
							.filter(l -> l.getDateTime().getMonthValue() == month_f).collect(Collectors.toList());
					List<TaxLot> monthSoldLots = yearSoldLots.stream()
							.filter(l -> l.getDisposeEvent().getDateTime().getMonthValue() == month_f)
							.collect(Collectors.toList());

					BigDecimal monthBuyCostBasis = monthBuyLots.stream().map(l -> l.getCostBasis())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal monthSellCostBasis = monthSoldLots.stream().map(l -> l.getCostBasis())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal monthNetCostBasis = monthBuyCostBasis.subtract(monthSellCostBasis);

					line += "," + monthNetCostBasis.setScale(2, RoundingMode.HALF_UP).toPlainString();
				}

				pw.println(line);
			}

		}

//		System.out.println("\n-----------------------------\n");
//
//		Map<String, BigDecimal> netAssets = new HashMap<>();
//		for (TaxEvent event : allEvents) {
//			switch (event.getType()) {
//			case BUY:
//			case REWARD:
//				netAssets.put(event.getAsset(),
//						netAssets.getOrDefault(event.getAsset(), BigDecimal.ZERO).add(event.getAmount()));
//				break;
//			case FEE:
//			case SELL:
//				netAssets.put(event.getAsset(),
//						netAssets.getOrDefault(event.getAsset(), BigDecimal.ZERO).subtract(event.getAmount()));
//				break;
//			default:
//				break;
//			}
//		}
//		System.out.println("Asset Net " + year + ":");
//		netAssets.entrySet().stream().sorted(Comparator.comparing(e -> e.getValue()))
//				.forEach(e -> System.out.println("\t" + e.getKey() + ": " + e.getValue().toPlainString()));

	}

}
