package com.demod.crypto.evm;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.Transaction;

import com.demod.crypto.util.LazyWeakSparseImmutableList;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.Uninterruptibles;

public class Web3Helper {

	public static class FindTransactionsResult {
		public boolean partialResult;
		public Long lastProcessedBlock;
		public List<Entry<Block, Transaction>> foundTransactions;
	}

	public static ContiguousSet<Long> contiguousSet(long first, long last) {
		return ContiguousSet.create(Range.closed(first, last), DiscreteDomain.longs());
	}

	public static LocalDateTime convertTimestamp(BigInteger timestamp) {
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp.longValue()), ZoneId.systemDefault());
	}

	private final RPC rpc;

	private Web3j web3;
	private final Cache<Long, Block> blockCache;

	private int blockCacheBatchSize = 1000;
	private boolean hasLastBlockNumber = false;
	private long lastBlockNumber;

	private long checkedLastBlockNumberTime;

	private volatile boolean forceStop = false;

	public Web3Helper(Web3j web3, RPC rpc) {
		this.web3 = web3;
		this.rpc = rpc;

		setBlockCacheBatchSize(rpc.getBatchSize());

		blockCache = CacheBuilder.newBuilder().maximumSize(2000).build();
	}

	private long checkLastBlockNumber() throws IOException {
		if (!hasLastBlockNumber || System.currentTimeMillis() - checkedLastBlockNumberTime > 60000) {
			lastBlockNumber = web3.ethBlockNumber().send().getBlockNumber().longValue();
			hasLastBlockNumber = true;
			checkedLastBlockNumberTime = System.currentTimeMillis();
		}
		return lastBlockNumber;
	}

	public ContiguousSet<Long> findBlockRangeForYear(int year) throws IOException {
		long lastBlockNumber = web3.ethBlockNumber().send().getBlockNumber().longValue();

		List<Block> allBlocks = getBlockList(contiguousSet(0, lastBlockNumber), false);

		ZoneId timeZone = ZoneId.systemDefault();
		long yearStartTime = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, timeZone).toEpochSecond();
		long yearEndTime = yearStartTime + (365 * 24 * 60 * 60) - 1;

		List<Long> allTimestamps = Lists.transform(allBlocks, b -> b.getTimestamp().longValue());
		int yearStartBlock = Collections.binarySearch(allTimestamps, yearStartTime);
		if (yearStartBlock < 0) {
			yearStartBlock = -yearStartBlock - 1;
		}
		if (allBlocks.get(yearStartBlock).getTimestamp().longValue() < yearStartTime) {
			yearStartBlock++;
		}
		int yearEndBlock = Collections.binarySearch(allTimestamps, yearEndTime);
		if (yearEndBlock < 0) {
			yearEndBlock = -yearEndBlock - 1;
		}

		return contiguousSet(yearStartBlock, yearEndBlock);
	}

	// Can be force stopped
	public FindTransactionsResult findTransactionsWithAccounts(ContiguousSet<Long> blockRange, List<String> accounts,
			int batchSize) throws IOException {

		Set<String> accountCheck = accounts.stream().map(s -> s.toLowerCase()).collect(Collectors.toSet());
		// Strip "0x"
		List<String> inputCheck = accounts.stream().map(s -> s.toLowerCase().substring(2)).collect(Collectors.toList());

//		List<Block> blocks = getBlockListBatched(blockRange, true, batchSize);

		FindTransactionsResult result = new FindTransactionsResult();
		result.foundTransactions = new ArrayList<>();
		long lastProcessedBlock = -1;

		long blockStart = blockRange.first();
		long blockEnd = blockRange.last();

		try {
			long lastSampleTime = System.currentTimeMillis();
			int sampleCount = 0;

			for (long blockNumber = blockStart; blockNumber <= blockEnd; blockNumber++) {
				Block block = getBlock(blockNumber);
//				System.out.println(">>> >>> " + block.getNumber().longValue());// XXX

				sampleCount++;
				long sampleTime = System.currentTimeMillis();
				long sampleDuration = sampleTime - lastSampleTime;
				if (sampleDuration >= 60000) {
					long percentDone = ((blockNumber - blockStart) * 100) / blockRange.size();
					long blocksPerMinute = (sampleCount * 60000L) / sampleDuration;
					double etaHours = (sampleDuration * (blockEnd - blockNumber)) / (sampleCount * 1000D * 60D * 60D);
					System.out.println(">>> " + block.getNumber() + " (" + percentDone + "%) " + blocksPerMinute
							+ "/min -- ETA " + etaHours + " hrs");

					lastSampleTime = sampleTime;
					sampleCount = 0;
				}

				nextTx: for (Transaction tx : Lists.transform(block.getTransactions(), tr -> (Transaction) tr.get())) {
//				System.out.println(tx.getHash());
//				System.out.println(tx.getHash().substring(0, 6) + " " + tx.getFrom());
//				System.out.println(tx.getHash().substring(0, 6) + " " + tx.getTo());
//				System.out.println(tx.getHash().substring(0, 6) + " " + tx.getInput());

					if (accountCheck.contains(tx.getFrom().toLowerCase())) {
						result.foundTransactions.add(new SimpleImmutableEntry<>(block, tx));
						System.out.println("[FROM " + tx.getFrom().substring(0, 6) + "] " + tx.getHash());
						continue;
					}

					if ((tx.getTo() != null) && accountCheck.contains(tx.getTo().toLowerCase())) {
						result.foundTransactions.add(new SimpleImmutableEntry<>(block, tx));
						System.out.println("[TO " + tx.getTo().substring(0, 6) + "] " + tx.getHash());
						continue;
					}

					String inputLowercase = tx.getInput().toLowerCase();
					for (int i = 0; i < inputCheck.size(); i++) {
						String check = inputCheck.get(i);
						if (inputLowercase.contains(check)) {
							result.foundTransactions.add(new SimpleImmutableEntry<>(block, tx));
							System.out.println("[INPUT 0x" + check.substring(0, 4) + "] " + tx.getHash());
							continue nextTx;
						}
					}
				}
				lastProcessedBlock = block.getNumber().longValue();

				if (forceStop) {
					throw new InterruptedException("Forced to stop!");
				}
			}

			result.partialResult = false;

		} catch (Exception e) {
			e.printStackTrace();
			result.partialResult = true;
		}

		result.lastProcessedBlock = lastProcessedBlock;

		return result;
	}

	public void forceStop() {
		forceStop = true;
	}

	// Requests in batches and caches the results
	public Block getBlock(long requestNumber) throws IOException, InterruptedException {
		Preconditions.checkArgument(requestNumber <= checkLastBlockNumber(), "Requested block not made yet!",
				requestNumber, lastBlockNumber);

		Block ret = blockCache.getIfPresent(requestNumber);
		if (ret == null) {
			long batchOffset = requestNumber % blockCacheBatchSize;
			long batchStart = requestNumber - batchOffset;
			long batchLength = blockCacheBatchSize;
			if (batchStart + batchLength - 1 > lastBlockNumber) {
				batchLength = lastBlockNumber - batchStart + 1;
			}

			BatchRequest batchRequest = web3.newBatch();
			for (int i = 0; i < batchLength; i++) {
				DefaultBlockParameter param = DefaultBlockParameter.valueOf(BigInteger.valueOf(batchStart + i));
				batchRequest.add(web3.ethGetBlockByNumber(param, true));
			}

			;
			List<Block> orderedBlocks;
			while (true) {
				if (forceStop) {
					throw new InterruptedException("Forced to stop!");
				}
				try {
					BatchResponse batchResponse = batchRequest.send();

					orderedBlocks = batchResponse.getResponses().stream().map(r -> ((EthBlock) r).getBlock())
							.sorted(Comparator.comparing(b -> b.getNumber().longValue())).collect(Collectors.toList());
					break;
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Trying again in 15 seconds...");
					Uninterruptibles.sleepUninterruptibly(15, TimeUnit.SECONDS);
					web3.shutdown();
					web3 = rpc.createWeb3();
				}
			}

//			System.out.println(">>> Batch " + batchStart + " - " + (batchStart + batchLength - 1) + " ("
//					+ orderedBlocks.size() + ")");// XXX
//			System.out.println(">>> Batch Blocks... " + orderedBlocks.stream().map(b -> b.getNumber().toString())
//					.collect(Collectors.joining(",", "[", "]")));// XXX

			for (int i = 0; i < orderedBlocks.size(); i++) {
				Block block = orderedBlocks.get(i);
				long expectedBlockNumber = batchStart + i;
				long blockNumber = block.getNumber().longValue();
				if (blockNumber != expectedBlockNumber) {
					throw new IOException(
							"Block number does not match batch request! " + blockNumber + " != " + expectedBlockNumber);
				}
				blockCache.put(blockNumber, block);
				if (blockNumber == requestNumber) {
					ret = block;
				}
			}
		}
		return ret;
	}

	public LazyWeakSparseImmutableList<Block> getBlockList(ContiguousSet<Long> blockRange,
			boolean returnFullTransactionObjects) throws IOException {
		Long startBlock = blockRange.first();
		Long endBlock = blockRange.last();

		int size = (int) (endBlock - startBlock + 1);

		IntFunction<Block> resolver = index -> {
			try {
				DefaultBlockParameter param = DefaultBlockParameter.valueOf(BigInteger.valueOf(index + startBlock));
//				System.out.println(">>> " + blockNumber);
				return web3.ethGetBlockByNumber(param, returnFullTransactionObjects).send().getBlock();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		};

		return new LazyWeakSparseImmutableList<>(size, resolver);
	}

	public void setBlockCacheBatchSize(int blockCacheBatchSize) {
		this.blockCacheBatchSize = blockCacheBatchSize;
	}
}
