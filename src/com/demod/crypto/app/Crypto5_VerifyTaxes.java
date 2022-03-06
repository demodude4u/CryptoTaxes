package com.demod.crypto.app;

import com.demod.crypto.tax.LotStrategy;

public class Crypto5_VerifyTaxes {

	public static void main(String[] args) {
		int year = 2021;
		LotStrategy lotStrategy = LotStrategy.LGUT;

		if (args.length == 2) {
			year = Integer.parseInt(args[0]);
			lotStrategy = LotStrategy.valueOf(args[1]);
		}
	}

}
