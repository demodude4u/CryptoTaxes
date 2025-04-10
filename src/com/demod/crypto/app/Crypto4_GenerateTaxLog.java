package com.demod.crypto.app;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.demod.crypto.tax.LotStrategy;
import com.demod.crypto.tax.TaxEvent;
import com.demod.crypto.tax.TaxEvent.TaxEventType;
import com.demod.crypto.tax.TaxLot;
import com.demod.crypto.tax.TaxLot.AccrualType;
import com.demod.crypto.util.CoinGeckoAPI;
import com.demod.crypto.util.ConsoleArgs;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class Crypto4_GenerateTaxLog {

	private static final String[] EXPECTED_HEADERS = { "Date", "Account", "Event", "Asset", "Amount", "Value",
			"TransactionID" };

	private static final List<TaxEventType> SORT_TYPE_ORDER = Arrays.asList(new TaxEventType[] { TaxEventType.FEE,
			TaxEventType.DEPOSIT, TaxEventType.SELL, TaxEventType.BUY, TaxEventType.REWARD, TaxEventType.WITHDRAW });

	private static final DateTimeFormatter FMT_DATE_CSV = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss");
	private static final DateTimeFormatter FMT_DATE_CSV2 = DateTimeFormatter.ofPattern("M/d/yyyy H:mm");

	private static TaxLot createUnknownLot(TaxEvent event, BigDecimal amount) {
		return new TaxLot(AccrualType.UNKNOWN,
				new TaxEvent(event.getDateTime(), event.getAccount(), TaxEventType.UNKNOWN, event.getAsset(), amount,
						BigDecimal.ZERO, event.getTransactionId(), event.getOriginFile(),
						event.getOriginFileLineNumber()),
				event.getDateTime(), amount, BigDecimal.ZERO);
	}

	public static void main(String[] args) throws IOException {
		int year = ConsoleArgs.argInt("Script4", "Tax Year", args, 0, LocalDate.now().getYear() - 1);
		LotStrategy lotStrategy = LotStrategy.valueOf(ConsoleArgs.argStringChoice("Script4", "Lot Strategy", args, 1,
				"LGUT", Arrays.stream(LotStrategy.values()).map(e -> e.name()).sorted().toArray(String[]::new)));
		boolean rewardAsIncome = ConsoleArgs.argBoolean("Script4", "Rewards As Income", args, 2, false);

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
					String[] cells = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
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
					LocalDateTime date;
					try {
						date = LocalDateTime.parse(cells[0].trim(), FMT_DATE_CSV);
					} catch (DateTimeParseException e) {
						date = LocalDateTime.parse(cells[0].trim(), FMT_DATE_CSV2);
					}
					String account = cells[1].trim();
					TaxEventType type = TaxEventType.valueOf(cells[2].trim());
					String asset = cells[3].trim();
					BigDecimal amount = new BigDecimal(cells[4].trim().replace("\"", "").replace(",", ""));
					BigDecimal value;
					if (!cells[5].isBlank()) {
//						System.out.println("DEBUG " + cells[5]);// XXX
						value = new BigDecimal(cells[5].trim().replace("$", "").replace("\"", "").replace(",", ""));
					} else {
						BigDecimal price = CoinGeckoAPI.getHistoricalPrice(asset, date.toLocalDate());
						if (price == null) {
							value = BigDecimal.ZERO;
						} else {
							value = price.multiply(amount);
						}
					}
					String transactionId = cells[6].trim();

					if (amount.compareTo(BigDecimal.ZERO) == 0) {
						continue;
					}

					asset = renameSymbolsJson.optString(asset, asset);

					if (stableCoinSymbolsJson.optBoolean(asset)) {
						continue;
					}
					if (excludeSymbolsJson.optBoolean(asset)) {
						continue;
					}

					if (type == TaxEventType.CARRYOVER) {
						Verify.verify(date.getYear() < year,
								"Carryover event not before " + year + "! " + originFile + "#" + originLineNumber);
					} else {
						Verify.verify(date.getYear() == year,
								"Event is not from " + year + "! " + originFile + "#" + originLineNumber);
					}

					allEvents.add(new TaxEvent(date, account, type, asset, amount, value, transactionId, originFile,
							originLineNumber));
				}
			}
		}

		Map<String, List<TaxEvent>> eventsByFile = allEvents.stream()
				.collect(Collectors.groupingBy(e -> e.getOriginFile()));

		List<Entry<String, List<TaxEvent>>> transactionGroups = eventsByFile.values()
				.stream().<Map.Entry<String, List<TaxEvent>>>flatMap(
						l -> l.stream().filter(e -> e.getTransactionId() != null && !e.getTransactionId().isBlank())
								.collect(Collectors.groupingBy(e -> e.getTransactionId())).entrySet().stream())
				.collect(Collectors.toList());

		for (Entry<String, List<TaxEvent>> entry : transactionGroups) {
			String transactionId = entry.getKey();
			List<TaxEvent> events = entry.getValue();

			// Set all to the earliest dateTime
			LocalDateTime dateTime = events.stream().map(e -> e.getDateTime()).sorted().findFirst().get();
			for (TaxEvent event : events) {
				Verify.verify(Duration.between(dateTime, event.getDateTime()).getSeconds() < 60 * 60,
						"DateTime too different! " + FMT_DATE_CSV.format(dateTime) + " \n"
								+ events.stream().map(Object::toString).collect(Collectors.joining("\n")));
				event.setDateTime(dateTime);
			}

			// Combine same-typed events within the same transaction
			events.stream().collect(Collectors.groupingBy(e -> e.getType().name() + "-" + e.getAsset()))
					.forEach((combineType, combineEvents) -> {
						if (combineEvents.size() > 1) {
							TaxEvent firstEvent = combineEvents.stream()
									.sorted(Comparator.<TaxEvent, Integer>comparing(e -> e.getOriginFileLineNumber()))
									.findFirst().get();
							TaxEventType type = firstEvent.getType();
							String account = firstEvent.getAccount();
							String asset = firstEvent.getAsset();

							Verify.verify(combineEvents.stream().map(e -> e.getAsset()).distinct().count() == 1);

							BigDecimal amountSum = combineEvents.stream().map(e -> e.getAmount())
									.reduce(BigDecimal.ZERO, BigDecimal::add);
							BigDecimal valueSum = combineEvents.stream().map(e -> e.getValue()).reduce(BigDecimal.ZERO,
									BigDecimal::add);
							String originFile = firstEvent.getOriginFile();
							int originFileLineNumber = firstEvent.getOriginFileLineNumber();

							TaxEvent combinedEvent = new TaxEvent(dateTime, account, type, asset, amountSum, valueSum,
									transactionId, originFile, originFileLineNumber);

							allEvents.removeAll(combineEvents);
							allEvents.add(combinedEvent);
						}
					});

		}

		// XXX Shift all buy/reward/deposit back one day, to accomodate for time zone
		for (TaxEvent event : allEvents) {
			switch (event.getType()) {
			case BUY:
			case DEPOSIT:
			case UNKNOWN:
			case REWARD:
				event.setDateTime(event.getDateTime().minusDays(1));
			default:
				break;
			}
		}

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

//		PrintWriter debug = new PrintWriter(new File(strategyFolder, "debug.txt"));// XXX

		try (PrintWriter pw = new PrintWriter(new File(strategyFolder, year + "_" + lotStrategy.name() + "_log.csv"))) {
			pw.println("Date,Type,Asset,Amount,Cost Basis,Proceeds,Buy ID,Sell ID,Account,TransactionID");

			BiConsumer<PrintWriter, TaxLot> printDisposalRow = (pwc, l) -> {
				Verify.verify(l.isDisposed());
				pwc.println(FMT_DATE_CSV.format(l.getDisposeEvent().getDateTime()) + "," + l.getDisposeType().name()
						+ "," + l.getBuyEvent().getAsset() + "," + l.getAmount().toPlainString() + ","
						+ l.getCostBasis().setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ l.getProceeds().setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ l.getBuyEvent().getId() + "," + l.getDisposeEvent().getId() + ","
						+ l.getDisposeEvent().getAccount() + "," + l.getDisposeEvent().getTransactionId());
			};
			BiConsumer<PrintWriter, TaxLot> printAccrualRow = (pwc, l) -> {
				pwc.println(FMT_DATE_CSV.format(l.getBuyEvent().getDateTime()) + "," + l.getAccrualType().name() + ","
						+ l.getBuyEvent().getAsset() + "," + l.getAmount().toPlainString() + ","
						+ l.getCostBasis().setScale(2, RoundingMode.HALF_UP).toPlainString() + ",,"
						+ l.getBuyEvent().getId() + ",," + l.getBuyEvent().getAccount() + ","
						+ l.getBuyEvent().getTransactionId());
			};

			for (TaxEvent event : allEvents) {
//				if (event.getAsset().equals("XLM")) {// XXX
//					debug.println(event);
//				}

				List<TaxLot> pendingLots = allPendingLots.get(event.getAsset());

				switch (event.getType()) {

				// Approach with option #1: amount is removed, but cost basis remains the same
				// https://koinly.io/blog/deducting-crypto-trading-transfer-fees/
				// If this results in removing an entire lot that has a cost basis, consider it
				// a total loss (sold for $0)
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
						}

						if (feeAmount.compareTo(pickLot.getAmount()) < 0) {
							pickLot = pickLot.split(feeAmount, BigDecimal.ZERO);
							allLots.put(event.getAsset(), pickLot);
						}

						Verify.verify(pickLot.getAmount().compareTo(feeAmount) <= 0);

						if (pickLot.getCostBasis().compareTo(BigDecimal.ZERO) > 0) {
							pickLot.setSold(event, BigDecimal.ZERO);
						} else {
							pickLot.setRemoved(event);
						}

						allPendingLots.remove(event.getAsset(), pickLot);
						allSoldLots.put(event.getAsset(), pickLot);
						printDisposalRow.accept(pw, pickLot);

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
					break;

				case CARRYOVER:
					TaxLot carryoverLot = new TaxLot(AccrualType.CARRYOVER, event, event.getDateTime(),
							event.getAmount(), event.getValue());
					allPendingLots.put(event.getAsset(), carryoverLot);
					allLots.put(event.getAsset(), carryoverLot);
					printAccrualRow.accept(pw, carryoverLot);
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
					break;

				case DEPOSIT:
				case WITHDRAW:
				case UNKNOWN:
					// Ignored
					continue;
				}
			}
		}

//		debug.close();// XXX

		Crypto5_VerifyTaxes.main(new String[] { Integer.toString(year), lotStrategy.name() });

	}

}
