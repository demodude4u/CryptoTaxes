package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public enum LotStrategy {
	LIFO {
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			return lots.stream().sorted(Comparator.<TaxLot, LocalDateTime>comparing(l -> l.getDateTime()).reversed())
					.findFirst().get();
		}
	},
	FIFO {
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			return lots.stream().sorted(Comparator.comparing(l -> l.getDateTime())).findFirst().get();
		}
	},
	HIFO {
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			// TODO check if this is the correct way, using basis/amount
			return lots.stream()
					.sorted(Comparator.<TaxLot, BigDecimal>comparing(
							l -> l.getCostBasis().divide(l.getAmount(), 18, RoundingMode.HALF_UP)).reversed())
					.findFirst().get();
		}
	},;

	public abstract TaxLot pickLot(List<TaxLot> lots);
}
