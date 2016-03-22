package org.opensha2.calc;

import java.util.concurrent.ExecutorService;

/**
 * The number of threads with which to intialize thread pools. Values reference
 * the number of non-competing threads that could be supported on a particular
 * system as determined by calling {@link Runtime#availableProcessors()}.
 *
 * @author Peter Powers
 */
public enum ThreadCount {

	/**
	 * A single thread. Use of a single thread will generally prevent an
	 * {@link ExecutorService} from being used, and all calculations will be run
	 * on the thread from which a program was called.
	 */
	ONE,

	/**
	 * Half of {@code ALL}.
	 */
	HALF,

	/**
	 * Two less than {@code ALL}. So as to not commandeer all available
	 * resources.
	 */
	N_MINUS_2,

	/**
	 * All possible non-competing threads. The number of threads will equal the
	 * number of available processors.
	 */
	ALL;

}
