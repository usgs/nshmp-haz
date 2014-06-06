package org.opensha.calc.tasks;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;

import org.opensha.eq.forecast.Source;
import org.opensha.gmm.GMM_Source;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;

/**
 * Wraps
 *
 * @author Peter Powers
 */
public class Task<F, T> implements Iterable<T> {
	
	private final int size;
	private final CompletionService<T> cs;
	
	public Task(Iterable<F> inputs, TransformSupplier<F, T> supplier, Executor ex) {
		cs = new ExecutorCompletionService<T>(ex);
		int taskCount = 0;
		for (F input : inputs) {
			cs.submit(supplier.get(input));
			taskCount++;
		}
		size = taskCount;
	} 
	
	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			int caret = 0;

			@Override
			public boolean hasNext() {
				return caret < size;
			}

			// TODO these exceptions should be caught up the stack
			// somewhere
			@Override
			public T next() {
				caret++;
				try {
					return cs.take().get();
				} catch (InterruptedException | ExecutionException e) {
					Throwables.propagate(e);
					return null;
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}	
	
}
