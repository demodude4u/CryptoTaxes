package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VerifyCarryover {

	private final LocalDateTime date;
	private final String account;
	private final String asset;
	private final BigDecimal amount;
	private final BigDecimal value;
	private final String transactionId;

	public VerifyCarryover(LocalDateTime date, String account, String asset, BigDecimal amount, BigDecimal value,
			String transactionId) {
		this.date = date;
		this.account = account;
		this.asset = asset;
		this.amount = amount;
		this.value = value;
		this.transactionId = transactionId;
	}

	public String getAccount() {
		return account;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getAsset() {
		return asset;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public BigDecimal getValue() {
		return value;
	}

}
