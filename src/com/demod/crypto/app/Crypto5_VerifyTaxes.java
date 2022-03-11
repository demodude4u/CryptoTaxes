package com.demod.crypto.app;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.demod.crypto.tax.LotStrategy;
import com.demod.crypto.tax.TaxLot.AccrualType;
import com.demod.crypto.tax.TaxLot.DisposeType;
import com.demod.crypto.tax.VerifyAccrual;
import com.demod.crypto.tax.VerifyCarryover;
import com.demod.crypto.tax.VerifyDisposal;
import com.demod.crypto.util.CoinGeckoAPI;
import com.demod.crypto.util.ConsoleArgs;
import com.google.common.base.Verify;

public class Crypto5_VerifyTaxes {

	private static final DateTimeFormatter FMT_DATE_CSV = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss");

	public static void main(String[] args) throws IOException {
		int year = ConsoleArgs.argInt("Script5", "Tax Year", args, 0, LocalDate.now().getYear() - 1);
		LotStrategy lotStrategy = LotStrategy.valueOf(ConsoleArgs.argStringChoice("Script5", "Lot Strategy", args, 1,
				"LGUT", Arrays.stream(LotStrategy.values()).map(e -> e.name()).sorted().toArray(String[]::new)));

		File strategyFolder = new File("reports/" + year + "/taxes/" + lotStrategy.name());
		strategyFolder.mkdirs();

		List<VerifyAccrual> accruals = new ArrayList<>();
		Map<String, VerifyAccrual> accrualsId = new LinkedHashMap<>();
		List<VerifyDisposal> disposals = new ArrayList<>();
		List<String> logLines = Files
				.readAllLines(new File(strategyFolder, year + "_" + lotStrategy.name() + "_log.csv").toPath());
		logLines.remove(0);// header
		for (int i = 0; i < logLines.size(); i++) {
			String line = logLines.get(i);
			System.out.println("Line " + (i + 2) + ": " + line);

			String[] cells = line.split(",");
			for (int j = 0; j < cells.length; j++) {
				cells[j] = cells[j].trim();
			}
			if (cells.length < 10) {
				cells = Arrays.copyOf(cells, 10);
				for (int j = 0; j < cells.length; j++) {
					if (cells[j] == null) {
						cells[j] = "";
					}
				}
			}
			LocalDateTime date = LocalDateTime.parse(cells[0], FMT_DATE_CSV);
			String asset = cells[2];
			BigDecimal amount = new BigDecimal(cells[3]);
			BigDecimal costBasis = new BigDecimal(cells[4]);
			String buyId = cells[6];
			String account = cells[8];
			String transactionId = cells[9];
			try {
				AccrualType type = AccrualType.valueOf(cells[1]);

				Verify.verify(!accrualsId.containsKey(buyId), "Buy ID is already taken!");
				if (type == AccrualType.CARRYOVER) {
					Verify.verify(date.getYear() < year, "Carryover must be before " + year + "!");
				} else {
					Verify.verify(date.getYear() == year, "Accrual must be in " + year + "!");
				}

				VerifyAccrual accrual = new VerifyAccrual(date, type, asset, amount, costBasis, buyId, account,
						transactionId);
				accruals.add(accrual);
				accrualsId.put(buyId, accrual);

			} catch (IllegalArgumentException e) {// FIXME could do this more elegantly
				DisposeType type = DisposeType.valueOf(cells[1]);

				BigDecimal proceeds = new BigDecimal(cells[5]);
				String sellId = cells[7];

				Verify.verify(date.getYear() == year, "Disposal must be in " + year + "!");

				VerifyAccrual accrual = accrualsId.get(buyId);
				Verify.verify(accrual != null, "BuyID not found!");
				Verify.verify(accrual.getAsset().equals(asset), "Asset does not match BuyID asset!");

				VerifyDisposal disposal = new VerifyDisposal(accrual, date, type, asset, amount, costBasis, proceeds,
						sellId, account, transactionId);
				disposals.add(disposal);

				accrual.getDisposals().add(disposal);

				BigDecimal deductedAmount = accrual.getDisposals().stream().map(d -> d.getAmount())
						.reduce(accrual.getAmount(), BigDecimal::subtract);
				BigDecimal deductedCostBasis = accrual.getDisposals().stream().map(d -> d.getCostBasis())
						.reduce(accrual.getCostBasis(), BigDecimal::subtract);

				Verify.verify(deductedAmount.compareTo(BigDecimal.ZERO) >= 0,
						"Lot overdisposed amount by " + deductedAmount.negate().toPlainString());
				Verify.verify(deductedCostBasis.compareTo(BigDecimal.ONE.negate()) >= 0,
						"Lot overdisposed cost basis by $"
								+ deductedCostBasis.negate().setScale(2, RoundingMode.HALF_UP).toPlainString());
			}
		}

		List<VerifyCarryover> carryovers = new ArrayList<>();
		try (PrintWriter pw = new PrintWriter(
				new File(strategyFolder, year + "_" + lotStrategy.name() + "_carryover.csv"))) {
			pw.println("Date,Account,Event,Asset,Amount,Value,TransactionID,Original Buy ID");

			for (VerifyAccrual accrual : accrualsId.values()) {

				BigDecimal disposedAmount = accrual.getDisposals().stream().map(d -> d.getAmount())
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal disposedCostBasis = accrual.getDisposals().stream().map(d -> d.getCostBasis())
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				if (disposedAmount.compareTo(accrual.getAmount()) < 0) {
					LocalDateTime date = accrual.getDate();
					String account = accrual.getAccount();
					String asset = accrual.getAsset();
					BigDecimal amount = accrual.getAmount().subtract(disposedAmount);
					BigDecimal value = accrual.getCostBasis().subtract(disposedCostBasis);
					String transactionId = accrual.getTransactionId();

					VerifyCarryover carryover = new VerifyCarryover(date, account, asset, amount, value, transactionId);
					carryovers.add(carryover);

					pw.println(FMT_DATE_CSV.format(date) + "," + account + ",CARRYOVER," + asset + ","
							+ amount.toPlainString() + "," + value.setScale(2, RoundingMode.HALF_UP).toPlainString()
							+ "," + transactionId + "," + accrual.getBuyId());
				}
			}
		}

		try (PrintWriter pw = new PrintWriter(
				new File(strategyFolder, year + "_" + lotStrategy.name() + "_summary.csv"))) {
			pw.println("Asset,Income,Short Term,Long Term,Amount EOY " + (year - 1) + ",Amount Unknown,"
					+ "Amount Bought,Amount Income,Amount Sold,Amount Removed,Amount EOY " + year + ",Cost Basis EOY "
					+ (year - 1) + "," + "Cost Basis Sold,Cost Basis EOY " + year + ",Proceeds,Net Profit");

			List<String> assetOrder = accruals.stream().map(e -> e.getAsset()).distinct().sorted()
					.collect(Collectors.toList());
			BigDecimal incomeTotal = BigDecimal.ZERO;
			BigDecimal shortTermTotal = BigDecimal.ZERO;
			BigDecimal longTermTotal = BigDecimal.ZERO;
			BigDecimal costBasisLeftoverTotal = BigDecimal.ZERO;
			BigDecimal costBasisSoldTotal = BigDecimal.ZERO;
			BigDecimal costBasisCarryoverTotal = BigDecimal.ZERO;
			BigDecimal proceedsTotal = BigDecimal.ZERO;
			BigDecimal netProfitTotal = BigDecimal.ZERO;
			for (String asset : assetOrder) {
				List<VerifyAccrual> assetAccruals = accruals.stream().filter(l -> l.getAsset().equals(asset))
						.collect(Collectors.toList());
				List<VerifyDisposal> assetDisposals = disposals.stream().filter(l -> l.getAsset().equals(asset))
						.collect(Collectors.toList());
				List<VerifyCarryover> assetCarryovers = carryovers.stream().filter(l -> l.getAsset().equals(asset))
						.collect(Collectors.toList());

				BigDecimal income = assetAccruals.stream().filter(l -> l.getType() == AccrualType.INCOME)
						.map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO, BigDecimal::add);
				incomeTotal = incomeTotal.add(income);
				BigDecimal shortTerm = assetDisposals.stream().filter(l -> l.getType() == DisposeType.SHORT_TERM)
						.map(l -> l.getProceeds().subtract(l.getCostBasis())).reduce(BigDecimal.ZERO, BigDecimal::add);
				shortTermTotal = shortTermTotal.add(shortTerm);
				BigDecimal longTerm = assetDisposals.stream().filter(l -> l.getType() == DisposeType.LONG_TERM)
						.map(l -> l.getProceeds().subtract(l.getCostBasis())).reduce(BigDecimal.ZERO, BigDecimal::add);
				longTermTotal = longTermTotal.add(longTerm);

				BigDecimal amountLeftover = assetAccruals.stream().filter(l -> l.getType() == AccrualType.CARRYOVER)
						.map(l -> l.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountUnknown = assetAccruals.stream().filter(l -> l.getType() == AccrualType.UNKNOWN)
						.map(l -> l.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountBought = assetAccruals.stream().filter(l -> l.getType() == AccrualType.BUY)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountIncome = assetAccruals.stream().filter(l -> l.getType() == AccrualType.INCOME)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountSold = assetDisposals.stream()
						.filter(l -> l.getType() == DisposeType.SHORT_TERM || l.getType() == DisposeType.LONG_TERM)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountRemoved = assetDisposals.stream().filter(l -> l.getType() == DisposeType.REMOVED)
						.map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
				BigDecimal amountCarryover = assetCarryovers.stream().map(l -> l.getAmount()).reduce(BigDecimal.ZERO,
						BigDecimal::add);

				BigDecimal costBasisLeftover = assetAccruals.stream().filter(l -> l.getType() == AccrualType.CARRYOVER)
						.map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO, BigDecimal::add);
				costBasisLeftoverTotal = costBasisLeftoverTotal.add(costBasisLeftover);
				BigDecimal costBasisSold = assetDisposals.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				costBasisSoldTotal = costBasisSoldTotal.add(costBasisSold);
				BigDecimal costBasisCarryover = assetCarryovers.stream().map(l -> l.getValue()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				costBasisCarryoverTotal = costBasisCarryoverTotal.add(costBasisCarryover);
				BigDecimal proceeds = assetDisposals.stream().map(l -> l.getProceeds()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				proceedsTotal = proceedsTotal.add(proceeds);
				BigDecimal netProfit = proceeds.subtract(costBasisSold);
				netProfitTotal = netProfitTotal.add(netProfit);

				pw.println(asset + "," + income.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ shortTerm.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ longTerm.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ amountLeftover.toPlainString() + "," + amountUnknown.toPlainString() + ","
						+ amountBought.toPlainString() + "," + amountIncome.toPlainString() + ","
						+ amountSold.toPlainString() + "," + amountRemoved.toPlainString() + ","
						+ amountCarryover.toPlainString() + ","
						+ costBasisLeftover.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ costBasisSold.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ costBasisCarryover.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ proceeds.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
						+ netProfit.setScale(2, RoundingMode.HALF_UP).toPlainString());
			}
			pw.println("(Totals)," + incomeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ shortTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ longTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ",,,,,,,,"
					+ costBasisLeftoverTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ costBasisSoldTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ costBasisCarryoverTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ proceedsTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
					+ netProfitTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());

			System.out.println();
			System.out.println("Income: $" + incomeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
			System.out.println("Short Term: $" + shortTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
			System.out.println("Long Term: $" + longTermTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
			System.out.println("Net Profit: $" + netProfitTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
		}

		final int year_f = year;
		try (PrintWriter pw = new PrintWriter(
				new File(strategyFolder, year + "_" + lotStrategy.name() + "_monthly.csv"))) {
			pw.println("Asset,Category," + year + ",Jan " + year + ",Feb " + year + ",Mar " + year + ",Apr " + year
					+ ",May " + year + ",Jun " + year + ",Jul " + year + ",Aug " + year + ",Sep " + year + ",Oct "
					+ year + ",Nov " + year + ",Dec " + year);

			List<String> assetOrder = accruals.stream().map(e -> e.getAsset()).distinct().sorted()
					.collect(Collectors.toList());

			for (String asset : assetOrder) {
				String line = asset + ",Net Profit";

				List<VerifyDisposal> yearDisposals = disposals.stream().filter(l -> l.getAsset().equals(asset))
						.filter(l -> l.getDate().getYear() == year_f).collect(Collectors.toList());

				BigDecimal yearCostBasis = yearDisposals.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearProceeds = yearDisposals.stream().map(l -> l.getProceeds()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearNetProfit = yearProceeds.subtract(yearCostBasis);

				line += "," + yearNetProfit.setScale(2, RoundingMode.HALF_UP).toPlainString();

				for (int month = 1; month <= 12; month++) {
					final int month_f = month;
					List<VerifyDisposal> monthDisposals = yearDisposals.stream()
							.filter(l -> l.getDate().getMonthValue() == month_f).collect(Collectors.toList());

					BigDecimal monthCostBasis = monthDisposals.stream().map(l -> l.getCostBasis())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal monthProceeds = monthDisposals.stream().map(l -> l.getProceeds()).reduce(BigDecimal.ZERO,
							BigDecimal::add);
					BigDecimal monthNetProfit = monthProceeds.subtract(monthCostBasis);

					line += "," + monthNetProfit.setScale(2, RoundingMode.HALF_UP).toPlainString();
				}

				pw.println(line);
			}

			pw.println();
			for (String asset : assetOrder) {
				String line = asset + ",Net Basis";

				List<VerifyAccrual> yearAccruals = accruals.stream().filter(l -> l.getAsset().equals(asset))
						.filter(l -> l.getDate().getYear() == year_f).collect(Collectors.toList());
				List<VerifyDisposal> yearDisposals = disposals.stream().filter(l -> l.getAsset().equals(asset))
						.filter(l -> l.getDate().getYear() == year_f).collect(Collectors.toList());

				BigDecimal yearBuyCostBasis = yearAccruals.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearSellCostBasis = yearDisposals.stream().map(l -> l.getCostBasis()).reduce(BigDecimal.ZERO,
						BigDecimal::add);
				BigDecimal yearNetCostBasis = yearBuyCostBasis.subtract(yearSellCostBasis);

				line += "," + yearNetCostBasis.setScale(2, RoundingMode.HALF_UP).toPlainString();

				for (int month = 1; month <= 12; month++) {
					final int month_f = month;
					List<VerifyAccrual> monthAccruals = yearAccruals.stream()
							.filter(l -> l.getDate().getMonthValue() == month_f).collect(Collectors.toList());
					List<VerifyDisposal> monthDisposals = yearDisposals.stream()
							.filter(l -> l.getDate().getMonthValue() == month_f).collect(Collectors.toList());

					BigDecimal monthBuyCostBasis = monthAccruals.stream().map(l -> l.getCostBasis())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal monthSellCostBasis = monthDisposals.stream().map(l -> l.getCostBasis())
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal monthNetCostBasis = monthBuyCostBasis.subtract(monthSellCostBasis);

					line += "," + monthNetCostBasis.setScale(2, RoundingMode.HALF_UP).toPlainString();
				}

				pw.println(line);
			}

		}
		System.out.println();
		System.out.println("Tax log verified!");

		if (!CoinGeckoAPI.failedSymbols.isEmpty()) {
			System.out.println();
			System.out.println();
			System.out.println("WARNING! CoinGecko failed to determine price history for the following tokens:");
			CoinGeckoAPI.failedSymbols.forEach(s -> System.out.println("\t" + s));
			System.out.println();
			System.out.println("Modify coingecko-symbol-pref.json to point CoinGecko in the right direction.");
		}
	}

}
