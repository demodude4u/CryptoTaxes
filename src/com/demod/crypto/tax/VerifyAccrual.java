package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.demod.crypto.tax.TaxLot.AccrualType;

public class VerifyAccrual {

	private final LocalDateTime date;
	private final AccrualType type;
	private final String asset;
	private final BigDecimal amount;
	private final BigDecimal costBasis;
	private final String buyId;
	private final String account;
	private final String transactionId;

	private final List<VerifyDisposal> disposals = new ArrayList<>();

	public VerifyAccrual(LocalDateTime date, AccrualType type, String asset, BigDecimal amount, BigDecimal costBasis,
			String buyId, String account, String transactionId) {
		this.date = date;
		this.type = type;
		this.asset = asset;
		this.amount = amount;
		this.costBasis = costBasis;
		this.buyId = buyId;
		this.account = account;
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

	public String getBuyId() {
		return buyId;
	}

	public BigDecimal getCostBasis() {
		return costBasis;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public List<VerifyDisposal> getDisposals() {
		return disposals;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public AccrualType getType() {
		return type;
	}

}
