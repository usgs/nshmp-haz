package org.opensha.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.data.DataUtils.*;

import java.util.Arrays;

import com.google.common.base.Objects;

/**
 * Array based implementation of an {@code XY_Sequence}.
 * 
 * <p><em>Notes on usage:</em>
 * 
 * <p>This class provides methods for combining and modifying array-based
 * sequences (e.g. {@link #add(ArrayXY_Sequence)} TODO more links ). These are
 * very efficient implementations that should be used in favor of standard
 * iterators where possible.</p>
 * 
 * <p>If multiple instances of a sequence with a fixed set of x-values are
 * required, use the {@link ArrayXY_Sequence#copyOf(ArrayXY_Sequence)}
 * constructor as it short circuits time-consuming argument validation.</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ArrayXY_Sequence extends AbstractXY_Sequence {

	private final double[] xs;
	private final double[] ys;
	
	private final int xHash;
	
	/*
	 * Only for use by static factory methods. Create a new sequence from an
	 * existing one; copies the fields of {@code seq} to {@code this}.
	 */
	ArrayXY_Sequence(ArrayXY_Sequence seq) {
		checkNotNull(seq, "Sequence to copy is null");
		xs = seq.xs;
		ys = Arrays.copyOf(seq.ys, seq.ys.length);
		xHash = seq.xHash;
	}

	/*
	 * Only for use by static factory methods. Create a new sequence from
	 * defensive copies of the supplied value arrays; array lengths must >=2;
	 * y-values may be {@code null}.
	 */
	ArrayXY_Sequence(double[] xs, double[] ys) {
		checkArgument(checkNotNull(xs).length > 1, "Xs too small");
		if (ys == null) ys = new double[xs.length];
		checkArgument(xs.length == ys.length, "Xs and Ys are different lengths");
		checkArgument(isMonotonic(true, false, xs), "Non-monotonic x-values");
		this.xs = Arrays.copyOf(xs, xs.length);
		this.ys = Arrays.copyOf(ys, ys.length);
		xHash = Arrays.hashCode(xs);
	}
	
	/**
	 * Create a new sequence from the supplied value arrays.
	 * 
	 * @param xs x-values to initialize sequence with
	 * @param ys y-values to initialize sequence with; may be {@code null}
	 * @return an array based sequence
	 * @throws NullPointerException if {@code xs} are {@code null}
	 * @throws IllegalArgumentException if {@code xs} and {@code ys} are not the
	 *         same size
	 * @throws IllegalArgumentException if {@code xs} does not increase
	 *         monotonically or contains repeated values
	 */
	public static ArrayXY_Sequence create(double[] xs, double[] ys) {
		return new ArrayXY_Sequence(xs, ys);
	}
		
	/**
	 * Copy the supplied sequence to a new sequence.
	 * 
	 * @param sequence to copy
	 * @return a copy of the supplied {@code sequence}
	 * @throws NullPointerException if the supplied {@code sequence} is
	 *         {@code null}
	 */
	public static ArrayXY_Sequence copyOf(ArrayXY_Sequence sequence) {
		checkNotNull(sequence, "Sequence to copy is null");
		return new ArrayXY_Sequence(sequence);
	}
	
	@Override
	public double x(int index) {
		checkElementIndex(index, xs.length);
		return xUnchecked(index);
	}
	
	@Override
	double xUnchecked(int index) {
		return xs[index];
	}

	@Override
	public double y(int index) {
		checkElementIndex(index, ys.length);
		return yUnchecked(index);
	}

	@Override
	double yUnchecked(int index) {
		return ys[index];
	}

	@Override
	public void set(int index, double value) {
		checkElementIndex(index, xs.length);
		ys[index] = value;
	}
	
	@Override
	public int size() {
		return xs.length;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ArrayXY_Sequence)) return false;
		return hashCode() == obj.hashCode();
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(xHash, Arrays.hashCode(ys));
	}
	
	public ArrayXY_Sequence add(double value) {
		DataUtils.add(value, xs);
		return this;
	}
	
	/**
	 * Adds the supplied sequence to {@code this} sequence in place and returns
	 * {@code this}.
	 * @param sequence to add to {@code this}
	 * @return {@code this} sequence
	 * @throws NullPointerException if {@code sequence} is {@code null}
	 * @throws IllegalArgumentException if the x-values of the supplied sequence
	 *         are not the same as those of {@code this}
	 */
	public ArrayXY_Sequence add(ArrayXY_Sequence sequence) {
		checkArgument(checkNotNull(sequence).xHash == xHash);
		DataUtils.uncheckedAdd(ys, sequence.ys);
		return this;
	}

//	/**
//	 * Subtracts the supplied sequence from {@code this} sequence in place and returns
//	 * {@code this}.
//	 * @param sequence to subtract from {@code this}
//	 * @return {@code this} sequence
//	 * @throws NullPointerException if {@code sequence} is {@code null}
//	 * @throws IllegalArgumentException if the x-values of the supplied sequence
//	 *         are not the same as those of {@code this}
//	 */
//	public ArrayXY_Sequence subtract(ArrayXY_Sequence sequence) {
//		checkArgument(checkNotNull(sequence).xHash == xHash);
//		DataUtils.uncheckedSubtract(ys, sequence.ys);
//		return this;
//	}

	/**
	 * Multiplies {@code this} sequence in place by the one supplied and returns
	 * {@code this}.
	 * @param sequence to multiply {@code this} by
	 * @return {@code this} sequence
	 * @throws NullPointerException if {@code sequence} is {@code null}
	 * @throws IllegalArgumentException if the x-values of the supplied sequence
	 *         are not the same as those of {@code this}
	 */
	public ArrayXY_Sequence multiply(ArrayXY_Sequence sequence) {
		checkArgument(checkNotNull(sequence).xHash == xHash);
		DataUtils.uncheckedMultiply(ys, sequence.ys);
		return this;
	}

//	/**
//	 * Divides {@code this} sequence in place by the one supplied and returns
//	 * {@code this}.
//	 * @param sequence to divide {@code this} by
//	 * @return {@code this} sequence
//	 * @throws NullPointerException if {@code sequence} is {@code null}
//	 * @throws IllegalArgumentException if the x-values of the supplied sequence
//	 *         are not the same as those of {@code this}
//	 */
//	public ArrayXY_Sequence divide(ArrayXY_Sequence sequence) {
//		checkArgument(checkNotNull(sequence).xHash == xHash);
//		DataUtils.uncheckedDivide(ys, sequence.ys);
//		return this;
//	}
	
//	public Array
//	public ArrayXY_Sequence scale(double scale) {
//		
//	}
	
	

//	@Override
//	public void scale(double value) {
//		// TODO do nothing
//
//	}
//
//	@Override
//	public boolean equals(Object obj) {
//		return super.equals(obj);
//		// TODO do nothing
//		
//	}
	
//	@Override
//	public boolean equals(DefaultSequence sequence) {
//		return false;
//	}
//	private static class DefaultPoint implements XY_Point {
//		private DefaultSequence xy;
//		private int idx;
//		private DefaultPoint(DefaultSequence xy) { this.xy = xy; }
//		@Override public double x() { return xy.xs[idx]; }
//		@Override public double y() { return xy.ys[idx]; }
//	}
	

}
