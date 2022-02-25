package com.demod.crypto.explorer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.CDataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import com.demod.crypto.evm.RPC;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Streams;

public class BlockExplorerHelper {

	public static void main(String[] args) throws IOException {
		new BlockExplorerHelper(RPC.byName("BSC"))
				.fetchTokenTransfers("0x868d764312553ecef95cfd4cc301d9864ea69abaa7178207d8a3e3634640bb17").stream()
				.forEach(System.out::println);
	}

	private final RPC rpc;

	private boolean methodMode = false;

	public BlockExplorerHelper(RPC rpc) {
		String[] supportedExplorersA = { //
				"https://bscscan.com/", //
				"https://polygonscan.com/", //
				"https://snowtrace.io/", //
				"https://etherscan.io/",//
		};
		String[] supportedExplorersB = { //
				"https://explorer.kcc.io/",//
		};

		Preconditions.checkArgument(
				Streams.concat(Arrays.stream(supportedExplorersA), Arrays.stream(supportedExplorersB))
						.anyMatch(s -> s.equals(rpc.getExplorerUrl())),
				"Supported explorers: " + Arrays.toString(supportedExplorersA) + Arrays.toString(supportedExplorersB));

		this.methodMode = Arrays.stream(supportedExplorersB).anyMatch(s -> s.equals(rpc.getExplorerUrl()));

		this.rpc = rpc;
	}

	public List<TokenTransfer> fetchTokenTransfers(String txHash) throws IOException {
		if (!methodMode) {
			return fetchTokenTransfers_methodA(txHash);
		} else {
			return fetchTokenTransfers_methodB(txHash);
		}
	}

	public List<TokenTransfer> fetchTokenTransfers_methodA(String txHash) throws IOException {
		List<TokenTransfer> ret = new ArrayList<>();

		Document doc = Jsoup.connect(rpc.getExplorerUrl() + "tx/" + txHash).get();

		Elements mainTableRows = doc
				.selectXpath("//*[@id=\"ContentPlaceHolder1_maintable\"]//div[contains(@class,\"row\")]");

		boolean verifiedHash = false;
		for (Element row : mainTableRows) {
			String key = row.child(0).text().trim();
//			System.out.println("KEY: " + key);

			if (key.equals("Transaction Hash:")) {
				String value = row.child(1).text().trim();
				if (value.equals(txHash)) {
					verifiedHash = true;
				}
			} else if (key.startsWith("Interacted With")) {
				Elements children = row.selectXpath("//*[@id=\"wrapperContent\"]//li");
				for (Element li : children) {
					List<String> content = findContent_methodA(li);
					try {
//						System.out.println(content.stream().collect(Collectors.joining(",", "[", "]")));// XXX
						TokenTransfer tokenTransfer = parseTokenTransfer_methodA2(content);
//					System.out.println(tokenTransfer);
						ret.add(tokenTransfer);
					} catch (Exception e) {
						System.err.println(content.stream().collect(Collectors.joining(",", "[", "]")));
						throw e;
					}
				}

			} else if (key.startsWith("Tokens Transferred:")) {
				Elements children = row.selectXpath("//*[@id=\"wrapperContent\"]//li");
				if (children.size() > 1000) {
					// Probably an airdrop
					System.err.println("\tWay too many transfers! (" + children.size() + ")");
					return ret;
				}
				for (Element li : children) {
					List<String> content = findContent_methodA(li);
					try {
						if (content.size() > 5) {
							if (content.get(4).contains("ERC-1155")) {// Ignore NFTs
								continue;
							}
							if (content.get(5).contains("TokenID")) {// Ignore NFTs
								continue;
							}
						}

//					System.out.println(content.stream().collect(Collectors.joining(",", "[", "]")));// XXX
						TokenTransfer tokenTransfer = parseTokenTransfer_methodA(content);
//					System.out.println(tokenTransfer);
						ret.add(tokenTransfer);
					} catch (Exception e) {
						System.err.println(content.stream().collect(Collectors.joining(",", "[", "]")));
						throw e;
					}
				}
			}
		}

		Verify.verify(verifiedHash, "Tx Hash was not found on webpage!");

		return ret;
	}

	public List<TokenTransfer> fetchTokenTransfers_methodB(String txHash) throws IOException {
		List<TokenTransfer> ret = new ArrayList<>();

		Document doc = Jsoup.connect(rpc.getExplorerUrl() + "tx/" + txHash).get();

		Elements mainTableRows = doc.selectXpath("//div[contains(@class,\"content\")]/div[contains(@class,\"item\")]");

		boolean verifiedHash = false;
		for (Element row : mainTableRows) {
			if (row.className().equals("item hash")) {
				if (row.child(0).text().equals(txHash)) {
					verifiedHash = true;
				}
				continue;
			}

			String key = row.child(0).text().trim();
//			System.out.println("KEY: " + key);

			if (key.startsWith("Token Txns") || key.startsWith("Call Transfers")) {
				Elements children = row.selectXpath("//div[contains(@class,\"info1\")]");
				System.out.println("txns: " + children.size());// XXX

				if (children.size() > 1000) {
					// Probably an airdrop
					System.err.println("\tWay too many transfers! (" + children.size() + ")");
					return ret;
				}
				for (Element item : children) {
					List<String> content = findContent_methodB(item);
					try {
//						if (content.size() > 5) {
//							if (content.get(4).contains("ERC-1155")) {// Ignore NFTs
//								continue;
//							}
//							if (content.get(5).contains("TokenID")) {// Ignore NFTs
//								continue;
//							}
//						}

//					System.out.println(content.stream().collect(Collectors.joining(",", "[", "]")));// XXX
						TokenTransfer tokenTransfer = parseTokenTransfer_methodB(content);
//					System.out.println(tokenTransfer);
						ret.add(tokenTransfer);
					} catch (Exception e) {
						System.err.println(content.stream().collect(Collectors.joining(",", "[", "]")));
						throw e;
					}
				}
			}
		}

		Verify.verify(verifiedHash, "Tx Hash was not found on webpage!");

		return ret;
	}

	private List<String> findContent_methodA(Element listItem) {
		List<String> ret = new ArrayList<>();

		NodeTraversor.traverse(new NodeVisitor() {
			boolean titleOverride = false;
			int titleOverrideDepth;

			@Override
			public void head(Node node, int depth) {
//				System.out.println(node.nodeName() + " - " + node.attributes());
				if (node.hasAttr("title")) {
					titleOverride = true;
					titleOverrideDepth = depth;
					nextText(node.attr("title"));
					return;
				}

				if (titleOverride && depth > titleOverrideDepth) {
					return;
				} else if (titleOverride) {
					titleOverride = false;
				}

				if (node.hasAttr("href")) {
					String href = node.attr("href");
					// Looking for token address, but without the query appended at the end
					if (href.startsWith("/token/") && href.length() == 49) {
						nextText(href.substring(7));
					}
				}

				if (node instanceof TextNode) {
					TextNode textNode = (TextNode) node;
					String text = textNode.getWholeText();

					if (preserveWhitespace(textNode.parent()) || textNode instanceof CDataNode)
						nextText(text);
					else {
						StringBuilder accum = new StringBuilder();
						StringUtil.appendNormalisedWhitespace(accum, text, lastCharIsWhitespace(accum));
						nextText(accum.toString());
					}
				}
			}

			private void nextText(String text) {
				if (text.isBlank()) {
					return;
				}
				ret.add(text.trim());
			}

			@Override
			public void tail(Node node, int depth) {
			}
		}, listItem);

		return ret;
	}

	private List<String> findContent_methodB(Element listItem) {
		List<String> ret = new ArrayList<>();

		NodeTraversor.traverse(new NodeVisitor() {
			boolean override = false;
			int overrideDepth;

			@Override
			public void head(Node node, int depth) {
//				System.out.println(node.nodeName() + " - " + node.attributes());
				if (node instanceof Element) {
					Element element = (Element) node;
					if (element.className().startsWith("address")) {
						override = true;
						overrideDepth = depth;
						nextText(node.attr("href").substring(12));
						return;
					}
				}
				if (override && depth > overrideDepth) {
					return;
				} else if (override) {
					override = false;
				}

				if (node instanceof TextNode) {
					TextNode textNode = (TextNode) node;
					String text = textNode.getWholeText();

					if (preserveWhitespace(textNode.parent()) || textNode instanceof CDataNode)
						nextText(text);
					else {
						StringBuilder accum = new StringBuilder();
						StringUtil.appendNormalisedWhitespace(accum, text, lastCharIsWhitespace(accum));
						nextText(accum.toString());
					}
				}
			}

			private void nextText(String text) {
				if (text.isBlank()) {
					return;
				}
				ret.add(text.trim());
			}

			@Override
			public void tail(Node node, int depth) {
			}
		}, listItem);

		return ret;
	}

	private boolean lastCharIsWhitespace(StringBuilder sb) {
		return sb.length() != 0 && sb.charAt(sb.length() - 1) == ' ';
	}

	private BigDecimal parseBigNumber(String text) {
//		System.out.println("PARSING NUMBER " + text);// XXX
		return new BigDecimal(text.trim().replace(",", ""));
	}

	private TokenTransfer parseTokenTransfer_methodA(List<String> content) {
		TokenTransfer ret = new TokenTransfer();

		for (int i = 0; i < content.size(); i++) {
			String text = content.get(i);
//			System.out.println("PARSE " + i + ": " + text);// XXX
			switch (i) {
			case 0:
				Verify.verify(text.equals("From"), text);
				break;
			case 1:
			case 3:
				String alias;
				String aliasAddress;
				// "Alias (Address)"
				if (text.length() > 42) {
					int addressIndex = text.lastIndexOf("(");
					alias = text.substring(0, addressIndex - 1);
					aliasAddress = text.substring(addressIndex + 1, text.length() - 1);
				} else {
					aliasAddress = text;
					alias = null;
				}
//				System.out.println("PARSE ADDRESS: " + aliasAddress + "," + alias);// XXX
				if (i == 1) {
					ret.fromAddress = aliasAddress;
					ret.fromAddressAlias = alias;
				} else {
					ret.toAddress = aliasAddress;
					ret.toAddressAlias = alias;
				}
				break;
			case 2:
				Verify.verify(text.equals("To"), text);
				break;
			case 4:
				Verify.verify(text.equals("For"), text);
				break;
			case 5:
				int usdIndex = text.lastIndexOf("(");
				if (usdIndex != -1) {
					ret.amount = parseBigNumber(text.substring(0, usdIndex - 1));
					ret.amountCurrentUSD = parseBigNumber(text.substring(usdIndex + 2, text.length() - 1));
				} else {
					ret.amount = parseBigNumber(text);
					ret.amountCurrentUSD = null;
				}
				break;
			case 6:
				if (text.startsWith("($")) {
					ret.amountCurrentUSD = parseBigNumber(text.substring(2, text.length() - 1));
					content.remove(6);
					if (content.get(6).startsWith("($")) {
						content.remove(6);
					}
					text = content.get(6);// Skip on to the next right away
				}

				if (text.startsWith("0x")) {
					ret.tokenAddress = text;
				} else {
					// Blocked, probably fraudulent
					content.add(6, "");// Fake address
				}
				break;
			case 7:
				if (i == content.size() - 1) {
					int addressIndex = text.lastIndexOf("(");
					ret.tokenName = text.substring(0, addressIndex - 1);
					ret.tokenSymbol = text.substring(addressIndex + 1, text.length() - 1);
				} else if (text.endsWith("(")) {
					ret.tokenName = text.substring(0, text.length() - 1).trim();

				} else {
					ret.tokenName = text;
				}
				break;
			case 8:
				if (text.length() > 1 && text.startsWith("(")) {
					ret.tokenSymbol = text.substring(1, text.length() - 1);
				} else if (i != content.size() - 1) {
					ret.tokenSymbol = text;
				}
				break;
			case 9:
				if (!text.endsWith(")")) {
					ret.tokenSymbol = text;
				}
				break;
			}
		}

		Preconditions.checkState(ret.tokenSymbol != null);
		Preconditions.checkState(!ret.tokenSymbol.equals(")"));
		return ret;
	}

	// Interacted With (TRANSFER)
	private TokenTransfer parseTokenTransfer_methodA2(List<String> content) {
		TokenTransfer ret = new TokenTransfer();

		for (int i = 0; i < content.size(); i++) {
			String text = content.get(i);
//			System.out.println("PARSE " + i + ": " + text);// XXX
			switch (i) {
			case 0:
				Verify.verify(text.equals("TRANSFER"), text);
				break;
			case 1:
				String[] split = text.split("\\s+");
				if (split.length == 2) {
					ret.amount = parseBigNumber(split[0]);
					ret.tokenSymbol = rpc.getCurrencySymbol();
				} else {
					Verify.verify(content.get(2).equals("."));
					split = content.get(3).split("\\s+");
					ret.amount = parseBigNumber(text + "." + split[0]);
					ret.tokenSymbol = rpc.getCurrencySymbol();
					content.remove(2);
					content.remove(2);
				}
				break;
			case 2:
				Verify.verify(text.equals("From"), text);
				break;
			case 3:
			case 5:
				String alias;
				String aliasAddress;
				// "Alias (Address)"
				if (text.length() > 42) {
					int addressIndex = text.lastIndexOf("(");
					alias = text.substring(0, addressIndex - 1);
					aliasAddress = text.substring(addressIndex + 1, text.length() - 1);
				} else {
					aliasAddress = text;
					alias = null;
				}
//				System.out.println("PARSE ADDRESS: " + aliasAddress + "," + alias);// XXX
				if (i == 3) {
					ret.fromAddress = aliasAddress;
					ret.fromAddressAlias = alias;
				} else {
					ret.toAddress = aliasAddress;
					ret.toAddressAlias = alias;
				}
				break;
			case 4:
				Verify.verify(text.equals("To"), text);
				break;
			}
		}

		Preconditions.checkState(ret.tokenSymbol != null);
		Preconditions.checkState(!ret.tokenSymbol.equals(")"));
		return ret;
	}

	private TokenTransfer parseTokenTransfer_methodB(List<String> content) {
		TokenTransfer ret = new TokenTransfer();

		for (int i = 0; i < content.size(); i++) {
			String text = content.get(i);
//			System.out.println("PARSE " + i + ": " + text);// XXX
			switch (i) {
			case 0:
				Verify.verify(text.equals("From"), text);
				break;
			case 1:
			case 3:
				String alias;
				String aliasAddress;
				// "Alias (Address)"
				if (text.length() > 42) {
					int addressIndex = text.lastIndexOf("(");
					alias = text.substring(0, addressIndex - 1);
					aliasAddress = text.substring(addressIndex + 1, text.length() - 1);
				} else {
					aliasAddress = text;
					alias = null;
				}
//				System.out.println("PARSE ADDRESS: " + aliasAddress + "," + alias);// XXX
				if (i == 1) {
					ret.fromAddress = aliasAddress;
					ret.fromAddressAlias = alias;
				} else {
					ret.toAddress = aliasAddress;
					ret.toAddressAlias = alias;
				}
				break;
			case 2:
				Verify.verify(text.equals("To"), text);
				break;
			case 4:
				int usdIndex = text.lastIndexOf("(");
				if (usdIndex != -1) {
					ret.amount = parseBigNumber(text.substring(0, usdIndex - 1));
					ret.amountCurrentUSD = parseBigNumber(text.substring(usdIndex + 2, text.length() - 1));
				} else {
					ret.amount = parseBigNumber(text);
					ret.amountCurrentUSD = null;
				}
				break;
			case 5:
				ret.tokenSymbol = text;
				break;
			}
		}

		Preconditions.checkState(ret.tokenSymbol != null);
		return ret;
	}

	private boolean preserveWhitespace(@Nullable Node node) {
		// looks only at this element and five levels up, to prevent recursion &
		// needless stack searches
		if (node instanceof Element) {
			Element el = (Element) node;
			int i = 0;
			do {
				if (el.tag().preserveWhitespace())
					return true;
				el = el.parent();
				i++;
			} while (i < 6 && el != null);
		}
		return false;
	}

}
