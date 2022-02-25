package com.demod.crypto.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

public class TokenTransferSum {
	public static final double SWAP_VALUE_ACCURACY = 0.75;

	// Exchanged token for stablecoin
	public static boolean isBuySell(TokenTransferSum tts1, TokenTransferSum tts2) {
		tts1.checkPrice();
		tts2.checkPrice();

		// Exactly one is stablecoin
		if (tts1.stablecoin == tts2.stablecoin) {
			return false;
		}

		// Need both to have amounts
		if (tts1.isZero() || tts2.isZero()) {
			return false;
		}

		// Need an incoming and outgoing
		if (tts1.amount.signum() == tts2.amount.signum()) {
			return false;
		}

		return true;
	}

	// Exchanged token for token
	public static boolean isSwap(TokenTransferSum tts1, TokenTransferSum tts2) {
		tts1.checkPrice();
		tts2.checkPrice();

		// No stablecoins
		if (tts1.stablecoin || tts2.stablecoin) {
			return false;
		}

		// Need both prices to detect a swap
		if (!tts1.price.isPresent() || !tts2.price.isPresent()) {
			return false;
		}

		// Need both to have amounts
		if (tts1.isZero() || tts2.isZero()) {
			return false;
		}

		// Need an incoming and outgoing
		if (tts1.amount.signum() == tts2.amount.signum()) {
			return false;
		}

		BigDecimal val1 = tts1.getValue().get().abs();
		BigDecimal val2 = tts2.getValue().get().abs();
		BigDecimal min = val1.min(val2);
		BigDecimal max = val1.max(val2);
		// Assume a swap if the values are close enough
		return min.divide(max, 18, RoundingMode.DOWN).doubleValue() >= SWAP_VALUE_ACCURACY;
	}

	// Make sure the value is associated correctly for the buy/sell/swap
	// Returns true if the values are matched successfully
	public static boolean matchValue(TokenTransferSum tts1, TokenTransferSum tts2) {
		tts1.checkPrice();
		tts2.checkPrice();

		// Can't match if neither have a price
		if (!tts1.price.isPresent() && !tts2.price.isPresent()) {
			return false;
		}

		// If both are stablecoin, have to compare values to check if matched
		if (tts1.stablecoin && tts2.stablecoin) {
			return tts1.getValue().get().compareTo(tts2.getValue().get()) == 0;
		}

		// Assume same value if one price is missing, or a stablecoin
		if (!tts1.price.isPresent() || tts2.stablecoin) {
			tts1.price = Optional.of(tts2.getValue().get().divide(tts1.amount.abs(), 18, RoundingMode.DOWN));
			return true;
		}
		if (!tts2.price.isPresent() || tts1.stablecoin) {
			tts2.price = Optional.of(tts1.getValue().get().divide(tts2.amount.abs(), 18, RoundingMode.DOWN));
			return true;
		}

		BigDecimal val1 = tts1.getValue().get();
		BigDecimal val2 = tts2.getValue().get();

		// Already matched if the values are equal
		if (val1.compareTo(val2) == 0) {
			return true;
		}

		// Pick the lower value between the two coins
		// XXX Assuming isSwap has passed, the values are close enough

		if (val1.compareTo(val2) < 0) {
			tts2.price = Optional.of(tts1.getValue().get().divide(tts2.amount.abs(), 18, RoundingMode.DOWN));
			return true;
		} else {
			tts1.price = Optional.of(tts2.getValue().get().divide(tts1.amount.abs(), 18, RoundingMode.DOWN));
			return true;
		}
	}

	private final LocalDate date;
	private final boolean stablecoin;
	private final String symbol;

	private BigDecimal amount = BigDecimal.ZERO;

	private Optional<BigDecimal> price = null;

	public TokenTransferSum(LocalDate date, boolean stablecoin, String symbol) {
		this.date = date;
		this.stablecoin = stablecoin;
		this.symbol = symbol;
	}

	public void amountIn(BigDecimal in) {
		amount = amount.add(in);
	}

	public void amountOut(BigDecimal out) {
		amount = amount.subtract(out);
	}

	private void checkPrice() {
		if (price == null) {
			if (stablecoin) {
				price = Optional.of(BigDecimal.ONE);
			} else if (date != null && CoinGeckoAPI.hasSymbol(symbol)) {
				price = Optional.ofNullable(CoinGeckoAPI.getHistoricalPrice(symbol, date));
			} else {
				price = Optional.empty();
			}
		}
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getSymbol() {
		return symbol;
	}

	public Optional<BigDecimal> getValue() {
		checkPrice();
		return price.map(p -> p.multiply(amount.abs()));
	}

	public boolean isIncoming() {
		return amount.compareTo(BigDecimal.ZERO) > 0;
	}

	public boolean isOutgoing() {
		return amount.compareTo(BigDecimal.ZERO) < 0;
	}

	public boolean isStablecoin() {
		return stablecoin;
	}

	public boolean isZero() {
		return amount.compareTo(BigDecimal.ZERO) == 0;
	}
}