package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.demod.crypto.tax.TaxLot.DisposeType;

public class VerifyDisposal {

	private final VerifyAccrual accrual;
	private final LocalDateTime date;
	private final DisposeType type;
	private final String asset;
	private final BigDecimal amount;
	private final BigDecimal costBasis;
	private final String sellId;
	private final String account;
	private final String transactionId;
	private final BigDecimal proceeds;

	public VerifyDisposal(VerifyAccrual accrual, LocalDateTime date, DisposeType type, String asset, BigDecimal amount,
			BigDecimal costBasis, BigDecimal proceeds, String sellId, String account, String transactionId) {
		this.accrual = accrual;
		this.date = date;
		this.type = type;
		this.asset = asset;
		this.amount = amount;
		this.costBasis = costBasis;
		this.proceeds = proceeds;
		this.sellId = sellId;
		this.account = account;
		this.transactionId = transactionId;
	}

	public String getAccount() {
		return account;
	}

	public VerifyAccrual getAccrual() {
		return accrual;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getAsset() {
		return asset;
	}

	public BigDecimal getCostBasis() {
		return costBasis;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public BigDecimal getProceeds() {
		return proceeds;
	}

	public String getSellId() {
		return sellId;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public DisposeType getType() {
		return type;
	}

}
