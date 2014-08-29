package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Convenience {@code List} implementation for {@code List}s of
 * {@code ListenableFuture}s. An {@code AsyncList} does not permit {@code null}
 * elements.
 * 
 * @author Peter Powers
 */
final class AsyncList<T> extends AbstractList<ListenableFuture<T>> {

	private final List<ListenableFuture<T>> delegate;

	private AsyncList(int initialCapacity) {
		delegate = new ArrayList<>(initialCapacity);
	}

	/**
	 * Creates a new {@code AsynList} with an inital capacity of 256 elements.
	 * @return a new {@code AsynList}
	 */
	static <T> AsyncList<T> create() {
		return new AsyncList<T>(256);
	}

	/**
	 * Creates a new {@code AsynList} with the specified inital capacity.
	 * @return a new {@code AsynList}
	 */
	static <T> AsyncList<T> createWithCapacity(int initialCapacity) {
		return new AsyncList<T>(initialCapacity);
	}

	@Override public boolean add(ListenableFuture<T> future) {
		return delegate.add(checkNotNull(future));
	}

	@Override public ListenableFuture<T> get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}

}
