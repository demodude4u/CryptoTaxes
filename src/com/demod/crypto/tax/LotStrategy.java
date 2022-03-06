package com.demod.crypto.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public enum LotStrategy {
	LIFO {// Last in, first out
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			return lots.stream().sorted(Comparator.<TaxLot, LocalDateTime>comparing(l -> l.getDateTime()).reversed())
					.findFirst().get();
		}
	},
	FIFO {// First in, first out
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			return lots.stream().sorted(Comparator.comparing(l -> l.getDateTime())).findFirst().get();
		}
	},
	HIFO {// Highest cost first
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			return lots.stream()
					.sorted(Comparator.<TaxLot, BigDecimal>comparing(l -> l.getEffectiveBuyPrice()).reversed())
					.findFirst().get();
		}
	},
	LOFO {// Lowest cost first
		@Override
		public TaxLot pickLot(List<TaxLot> lots) {
			return lots.stream().sorted(Comparator.<TaxLot, BigDecimal>comparing(l -> l.getEffectiveBuyPrice()))
					.findFirst().get();
		}
	},
	LGUT {// Loss Gain Utilization (
			// http://1costbasissolution.com/faq.asp (#6, pick in this order)
			// - Short term losses, greatest losses first
			// - Long term losses, greatest losses first
			// - Short term no gain or loss
			// - Long term no gain or loss
			// - Long term gains, least gained first
			// - Short term gains, least gained first
		@Override
		public TaxLot pickLot(List<TaxLot> lots, LocalDateTime date, BigDecimal amount, BigDecimal proceeds) {
			BigDecimal effectiveSellPrice = proceeds.divide(amount, 18, RoundingMode.HALF_UP);

			Optional<TaxLot> lossShortPick = lots.stream()
					.filter(l -> effectiveSellPrice.compareTo(l.getEffectiveBuyPrice()) < 0
							&& !TaxLot.isLongTerm(l.getDateTime(), date))
					.sorted(Comparator.<TaxLot, BigDecimal>comparing(l -> l.getEffectiveBuyPrice()).reversed())
					.findFirst();
			if (lossShortPick.isPresent()) {
				return lossShortPick.get();
			}

			Optional<TaxLot> lossLongPick = lots.stream()
					.filter(l -> effectiveSellPrice.compareTo(l.getEffectiveBuyPrice()) < 0
							&& TaxLot.isLongTerm(l.getDateTime(), date))
					.sorted(Comparator.<TaxLot, BigDecimal>comparing(l -> l.getEffectiveBuyPrice()).reversed())
					.findFirst();
			if (lossLongPick.isPresent()) {
				return lossLongPick.get();
			}

			Optional<TaxLot> noneShortPick = lots.stream()
					.filter(l -> effectiveSellPrice.compareTo(l.getEffectiveBuyPrice()) == 0
							&& !TaxLot.isLongTerm(l.getDateTime(), date))
					.sorted(Comparator.<TaxLot, LocalDateTime>comparing(l -> l.getDateTime()).reversed()).findFirst();
			if (noneShortPick.isPresent()) {
				return noneShortPick.get();
			}

			Optional<TaxLot> noneLongPick = lots.stream()
					.filter(l -> effectiveSellPrice.compareTo(l.getEffectiveBuyPrice()) == 0
							&& TaxLot.isLongTerm(l.getDateTime(), date))
					.sorted(Comparator.<TaxLot, LocalDateTime>comparing(l -> l.getDateTime()).reversed()).findFirst();
			if (noneLongPick.isPresent()) {
				return noneLongPick.get();
			}

			Optional<TaxLot> gainLongPick = lots.stream()
					.filter(l -> effectiveSellPrice.compareTo(l.getEffectiveBuyPrice()) > 0
							&& TaxLot.isLongTerm(l.getDateTime(), date))
					.sorted(Comparator.<TaxLot, BigDecimal>comparing(l -> l.getEffectiveBuyPrice()).reversed())
					.findFirst();
			if (gainLongPick.isPresent()) {
				return gainLongPick.get();
			}

			Optional<TaxLot> gainShortPick = lots.stream()
					.filter(l -> effectiveSellPrice.compareTo(l.getEffectiveBuyPrice()) > 0
							&& !TaxLot.isLongTerm(l.getDateTime(), date))
					.sorted(Comparator.<TaxLot, BigDecimal>comparing(l -> l.getEffectiveBuyPrice()).reversed())
					.findFirst();
			return gainShortPick.get();
		}

	};

	protected TaxLot pickLot(List<TaxLot> lots) {
		throw new UnsupportedOperationException();
	}

	public TaxLot pickLot(List<TaxLot> lots, LocalDateTime date, BigDecimal amount, BigDecimal proceeds) {
		return pickLot(lots);
	}
}
