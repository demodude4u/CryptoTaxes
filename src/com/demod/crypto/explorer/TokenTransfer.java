package com.demod.crypto.explorer;

import java.math.BigDecimal;

public class TokenTransfer {
	public String fromAddress;
	public String fromAddressAlias;
	public String toAddress;
	public String toAddressAlias;
	public BigDecimal amount;
	public BigDecimal amountCurrentUSD;
	public String tokenSymbol;
	public String tokenName;
	public String tokenAddress;

	@Override
	public String toString() {
		return "ScrapedTokenTransfer [fromAddress=" + fromAddress + ", fromAddressAlias=" + fromAddressAlias
				+ ", toAddress=" + toAddress + ", toAddressAlias=" + toAddressAlias + ", amount=" + amount
				+ ", amountCurrentUSD=" + amountCurrentUSD + ", tokenSymbol=" + tokenSymbol + ", tokenName="
				+ tokenName + ", tokenAddress=" + tokenAddress + "]";
	}
}