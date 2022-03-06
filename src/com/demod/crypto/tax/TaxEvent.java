package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.json.JSONObject;

import com.google.common.base.Preconditions;

public class TaxEvent {
	public static enum TaxEventType {
		BUY, SELL, DEPOSIT, WITHDRAW, FEE, REWARD, UNKNOWN, REMOVED, CARRYOVER
	}

	private static final DateTimeFormatter FMT_DATE_ID = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private LocalDateTime dateTime;
	private final String account;
	private final TaxEventType type;
	private final String asset;
	private final BigDecimal amount;
	private final BigDecimal value;
	private final String transactionId;
	private final Map<String, String> extraData;
	private final String originFile;
	private final int originFileLineNumber;

	private final String id;

	public TaxEvent(LocalDateTime dateTime, String account, TaxEventType type, String asset, BigDecimal amount,
			BigDecimal value, String transactionId, Map<String, String> extraData, String originFile,
			int originFileLineNumber) {
		super();
		this.dateTime = dateTime;
		this.account = account;
		this.type = type;
		this.asset = asset;
		this.amount = amount;
		this.value = value;
		this.transactionId = transactionId;
		this.extraData = extraData;
		this.originFile = originFile;
		this.originFileLineNumber = originFileLineNumber;

		// id must preserve original data
		if (transactionId.isBlank()) {
			id = account + " " + asset + " " + FMT_DATE_ID.format(dateTime) + " " + type.name() + " #"
					+ originFileLineNumber;
		} else {
			id = account + " " + asset + " " + FMT_DATE_ID.format(dateTime) + " " + type.name() + " " + transactionId
					+ " #" + originFileLineNumber;
		}

		Preconditions.checkArgument(amount.compareTo(BigDecimal.ZERO) > 0, this);
		Preconditions.checkArgument(value.compareTo(BigDecimal.ZERO) >= 0, this);
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

	public LocalDateTime getDateTime() {
		return dateTime;
	}

	public Map<String, String> getExtraData() {
		return extraData;
	}

	public String getId() {
		return id;
	}

	public String getOriginFile() {
		return originFile;
	}

	public int getOriginFileLineNumber() {
		return originFileLineNumber;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public TaxEventType getType() {
		return type;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setDateTime(LocalDateTime dateTime) {
		this.dateTime = dateTime;
	}

	@Override
	public String toString() {
		return "TaxEvent [dateTime=" + dateTime + ", account=" + account + ", type=" + type + ", asset=" + asset
				+ ", amount=" + amount.toPlainString() + ", value=$" + value.toPlainString() + ", transactionId="
				+ transactionId + ", extraData=" + new JSONObject(extraData).toString() + ", originFile=" + originFile
				+ ", originFileLineNumber=" + originFileLineNumber + "]";
	}

}
