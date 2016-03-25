package org.opensha2.calc;

import java.util.concurrent.ExecutorService;

import com.google.common.util.concurrent.MoreExecutors;

/**
 * The number of threads with which to intialize thread pools. Values reference
 * the number of non-competing threads that could be supported on a particular
 * system as determined by calling {@link Runtime#availableProcessors()}.
 *
 * @author Peter Powers
 */
public enum ThreadCount {

	/**
	 * A single thread. This identifier will typically cause a program to
	 * either use Guava's {@link MoreExecutors#directExecutor()} or skip using
	 * an {@link ExecutorService} altogether, and all calculations will be run
	 * on the thread from which a program was called. This is useful for
	 * debugging.
	 */
	ONE {
		@Override
		public int value() {
			return 1;
		}
	},

	/**
	 * Half of {@code ALL}.
	 */
	HALF {
		@Override
		public int value() {
			return Math.max(1, CORES / 2);
		}
	},

	/**
	 * Two less than {@code ALL}, so as to not commandeer all available
	 * resources.
	 */
	N_MINUS_2 {
		@Override
		public int value() {
			return Math.max(1, CORES - 2);
		}
	},

	/**
	 * All possible non-competing threads. The number of threads will equal the
	 * number of available processors.
	 */
	ALL {
		@Override
		public int value() {
			return CORES;
		}
	};

	private static final int CORES = Runtime.getRuntime().availableProcessors();

	/**
	 * The number of threads relative to the number of available processors on a
	 * system. The value returned will never be less than one.
	 */
	public abstract int value();

}
