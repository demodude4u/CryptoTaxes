package com.demod.crypto.util;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.util.WeakHashMap;
import java.util.function.IntFunction;

import com.google.common.collect.ImmutableList;

public class LazyWeakSparseImmutableList<E> extends AbstractList<E> implements RandomAccess {

	@FunctionalInterface
	public static interface BatchResolver<E> {
		public List<E> resolveBatch(int index, int length) throws Exception;
	}

	private final int size;
	private final int batchSize;
	private final BatchResolver<E> resolver;
	private final WeakHashMap<Integer, List<E>> cache;

	public LazyWeakSparseImmutableList(int size, int batchSize, BatchResolver<E> resolver) {
		this.size = size;
		this.batchSize = batchSize;
		this.resolver = resolver;

		cache = new WeakHashMap<>();
	}

	public LazyWeakSparseImmutableList(int size, IntFunction<E> resolver) {
		this(size, 1, (i, s) -> ImmutableList.of(resolver.apply(i)));
	}

	@Override
	public E get(int index) {
		int batchOffset = index % batchSize;
		int batchIndex = index - batchOffset;
		List<E> batch = cache.get(batchIndex);
		if (batch != null) {
			return batch.get(batchOffset);
		} else {
			try {
				cache.put(batchIndex, batch = resolver.resolveBatch(index, batchSize));
			} catch (Exception e) {
				throw new RuntimeException(e);// XXX Janky

//				if (batchSize > 1 && (batchSize % 2) == 0) {
//					System.err.println("TRYING A HALF BATCH!");
//					try {
//						ArrayList<E> batchsCombined = new ArrayList<>(batchSize);
//						int halfSize = batchSize / 2;
//						batchsCombined.addAll(resolver.resolveBatch(index, halfSize));
//						batchsCombined.addAll(resolver.resolveBatch(index + halfSize, halfSize));
//						batch = batchsCombined;
//					} catch (Exception e1) {
//						e.printStackTrace();
//						e1.printStackTrace();
//					}
//				}
			}
		}
		return batch.get(batchOffset);
	}

	@Override
	public int size() {
		return size;
	}

}
