package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class TaxLot {
	public static enum AccrualType {
		BUY, INCOME, CARRYOVER, UNKNOWN
	}

	public static enum DisposeType {
		SHORT_TERM, LONG_TERM, REMOVED
	}

	public static boolean isLongTerm(LocalDateTime buyDate, LocalDateTime sellDate) {
		long days = Duration.between(buyDate, sellDate).toDays();
		return days > 363;
	}

	private final AccrualType accrualType;
	private final TaxEvent buyEvent;
	private final LocalDateTime dateTime;
	private BigDecimal amount;
	private BigDecimal costBasis;

	private boolean disposed = false;
	private DisposeType disposeType;
	private TaxEvent disposeEvent;
	private BigDecimal proceeds;

	public TaxLot(AccrualType accrualType, TaxEvent buyEvent, LocalDateTime dateTime, BigDecimal amount,
			BigDecimal costBasis) {
		this.accrualType = accrualType;
		this.buyEvent = buyEvent;
		this.dateTime = dateTime;
		this.amount = amount;
		this.costBasis = costBasis;
	}

	public AccrualType getAccrualType() {
		return accrualType;
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

	public TaxEvent getDisposeEvent() {
		return disposeEvent;
	}

	public DisposeType getDisposeType() {
		return disposeType;
	}

	public BigDecimal getEffectiveBuyPrice() {
		return costBasis.divide(amount, 18, RoundingMode.HALF_UP);
	}

	public BigDecimal getProceeds() {
		return proceeds;
	}

	public boolean isDisposed() {
		return disposed;
	}

	public void setRemoved(TaxEvent removalEvent) {
		Preconditions.checkState(!disposed);
		Preconditions.checkArgument(buyEvent.getAsset().equals(removalEvent.getAsset()));
		disposed = true;
		disposeType = DisposeType.REMOVED;
		disposeEvent = removalEvent;
		proceeds = BigDecimal.ZERO;
	}

	public void setSold(TaxEvent sellEvent, BigDecimal sellProceeds) {
		Preconditions.checkState(!disposed);
		Preconditions.checkArgument(buyEvent.getAsset().equals(sellEvent.getAsset()));
		disposed = true;
		if (isLongTerm(buyEvent.getDateTime(), sellEvent.getDateTime())) {
			disposeType = DisposeType.LONG_TERM;
		} else {
			disposeType = DisposeType.SHORT_TERM;
		}
		disposeEvent = sellEvent;
		proceeds = sellProceeds;
	}

	public TaxLot split(BigDecimal splitAmount, BigDecimal splitCostBasis) {
		Preconditions.checkState(!disposed);
		Preconditions.checkArgument(amount.compareTo(splitAmount) > 0);
		Preconditions.checkArgument(costBasis.compareTo(splitCostBasis) >= 0, this);
		amount = amount.subtract(splitAmount);
		costBasis = costBasis.subtract(splitCostBasis);
		return new TaxLot(accrualType, buyEvent, dateTime, splitAmount, splitCostBasis);
	}

	public TaxLot splitProportionally(BigDecimal splitAmount) {
		BigDecimal splitCostBasis = splitAmount.divide(amount, 18, RoundingMode.HALF_UP).multiply(costBasis);
		return split(splitAmount, splitCostBasis);
	}

	@Override
	public String toString() {
		return "TaxLot [acquireType=" + accrualType.name() + ", dateTime=" + dateTime + ", amount="
				+ amount.toPlainString() + ", costBasis=$" + costBasis.toPlainString() + ", buyId=" + buyEvent.getId()
				+ ", disposeType=" + Optional.ofNullable(disposeType).map(DisposeType::name).orElse("") + ", disposeId="
				+ Optional.ofNullable(disposeEvent).map(TaxEvent::getId).orElse("") + ", proceeds=$"
				+ Optional.ofNullable(proceeds).map(BigDecimal::toPlainString).orElse("") + "]";
	}
}
