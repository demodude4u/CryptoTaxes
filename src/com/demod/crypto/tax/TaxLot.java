package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class TaxLot {
	public static enum TaxLotSellType {
		INCOME, SHORT_TERM, LONG_TERM, REMOVED
	}

	private final TaxEvent buyEvent;
	private final LocalDateTime dateTime;
	private BigDecimal amount;
	private BigDecimal costBasis;
	private BigDecimal fees;

	private boolean sold = false;
	private TaxLotSellType sellType;
	private TaxEvent sellEvent;
	private BigDecimal sellProceeds;

	public TaxLot(TaxEvent buyEvent, LocalDateTime dateTime, BigDecimal amount, BigDecimal costBasis,
			BigDecimal buyFees) {
		this.buyEvent = buyEvent;
		this.dateTime = dateTime;
		this.amount = amount;
		this.costBasis = costBasis;
		this.fees = buyFees;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public TaxEvent getBuyEvent() {
		return buyEvent;
	}

	public BigDecimal getCostBasis() {
		return costBasis;
	}

	public LocalDateTime getDateTime() {
		return dateTime;
	}

	public BigDecimal getFees() {
		return fees;
	}

	public TaxEvent getSellEvent() {
		return sellEvent;
	}

	public BigDecimal getSellProceeds() {
		return sellProceeds;
	}

	public TaxLotSellType getSellType() {
		return sellType;
	}

	public boolean isSold() {
		return sold;
	}

	public void setSold(TaxLotSellType sellType, TaxEvent sellEvent, BigDecimal sellProceeds, BigDecimal sellFees) {
		Preconditions.checkState(!sold);
		Preconditions.checkArgument(buyEvent.getAsset().equals(sellEvent.getAsset()));
		sold = true;
		this.sellType = sellType;
		this.sellEvent = sellEvent;
		this.sellProceeds = sellProceeds;
		fees = fees.add(sellFees);
	}

	public TaxLot split(BigDecimal splitAmount, BigDecimal splitCostBasis) {
		Preconditions.checkState(!sold);
		Preconditions.checkArgument(amount.compareTo(splitAmount) > 0);
		Preconditions.checkArgument(costBasis.compareTo(splitCostBasis) >= 0);
		amount = amount.subtract(splitAmount);
		costBasis = costBasis.subtract(splitCostBasis);
		return new TaxLot(buyEvent, dateTime, splitAmount, splitCostBasis, BigDecimal.ZERO);
	}

	public TaxLot splitProportionally(BigDecimal splitAmount) {
		BigDecimal splitCostBasis = splitAmount.divide(amount, 18, RoundingMode.HALF_UP).multiply(costBasis);
		return split(splitAmount, splitCostBasis);
	}

	@Override
	public String toString() {
		return "TaxLot [dateTime=" + dateTime + ", amount=" + amount.toPlainString() + ", costBasis=$"
				+ costBasis.toPlainString() + ", buyId=" + buyEvent.getId() + ", sellType="
				+ Optional.ofNullable(sellType).map(TaxLotSellType::name).orElse("") + ", sellId="
				+ Optional.ofNullable(sellEvent).map(TaxEvent::getId).orElse("") + ", sellProceeds=$"
				+ Optional.ofNullable(sellProceeds).map(BigDecimal::toPlainString).orElse("") + "]";
	}
}
