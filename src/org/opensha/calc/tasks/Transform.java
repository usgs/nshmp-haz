package org.opensha.calc.tasks;

import java.util.concurrent.Callable;

import com.google.common.base.Function;

/**
 * Wraps a {@code Function} in a {@code Callable}, providing the ability to
 * submit data transforms to {@code Executor}s.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public abstract class Transform<F, T> implements Callable<T>, Function<F, T> {
	
	private final F from;
	
	Transform(final F from) {
		this.from = from;
	}

	@Override
	public T call() throws Exception {
		return apply(from);
	}
	
}
