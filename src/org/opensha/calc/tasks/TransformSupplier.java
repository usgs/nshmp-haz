package org.opensha.calc.tasks;

/**
 * A supplier of {@code AsyncFunction}s.
 *
 * @author Peter Powers
 * @see Transform
 */
public interface TransformSupplier<F, T> {

	public Transform<F, T> get(F from);
	
}
