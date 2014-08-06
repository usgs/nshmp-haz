package org.opensha.calc;

import java.util.AbstractList;
import java.util.List;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Convenience container for {@code List}s of {@code ListenableFuture}s.
 *
 * @author Peter Powers
 */
public class AsyncList<T> extends AbstractList<ListenableFuture<T>> {

	private List<ListenableFuture<T>> delegate;
	
	@Override public ListenableFuture<T> get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}

}
