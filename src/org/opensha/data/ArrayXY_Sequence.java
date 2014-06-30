package org.opensha.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.data.DataUtils.*;

import java.util.Arrays;
import java.util.Objects;

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
		xs = checkNotNull(seq).xs;
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
	 * Returns a copy of the supplied sequence.
	 * 
	 * @param sequence to copy
	 * @return a copy of the supplied {@code sequence}
	 * @throws NullPointerException if the supplied {@code sequence} is
	 *         {@code null}
	 */
	public static ArrayXY_Sequence copyOf(ArrayXY_Sequence sequence) {
		return new ArrayXY_Sequence(sequence);
	}

	@Override public double x(int index) {
		checkElementIndex(index, xs.length);
		return xUnchecked(index);
	}

	@Override double xUnchecked(int index) {
		return xs[index];
	}

	@Override public double y(int index) {
		checkElementIndex(index, ys.length);
		return yUnchecked(index);
	}

	@Override double yUnchecked(int index) {
		return ys[index];
	}

	@Override public void set(int index, double value) {
		checkElementIndex(index, xs.length);
		ys[index] = value;
	}

	@Override public int size() {
		return xs.length;
	}

	@Override public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ArrayXY_Sequence)) return false;
		return hashCode() == obj.hashCode();
	}

	@Override public int hashCode() {
		return Objects.hash(xHash, Arrays.hashCode(ys));
	}

	/**
	 * Add a {@code term} to the y-values of this sequence in place.
	 * 
	 * @param term to add
	 * @return {@code this} sequence, for use inline
	 */
	public ArrayXY_Sequence add(double term) {
		DataUtils.uncheckedAdd(term, ys);
		return this;
	}

	/**
	 * Add the y-values of a sequence to the y-values of {@code this} sequence
	 * in place.
	 * 
	 * @param sequence to add
	 * @return {@code this} sequence, for use inline
	 * @throws IllegalArgumentException if
	 *         {@code sequence.xValues() != this.xValues()}
	 */
	public ArrayXY_Sequence add(ArrayXY_Sequence sequence) {
		DataUtils.uncheckedAdd(ys, validateSequence(sequence).ys);
		return this;
	}

	/**
	 * Multiply ({@code scale}) the y-values of this sequence in place.
	 * 
	 * @param scale factor
	 * @return {@code this} sequence, for use inline
	 */
	public ArrayXY_Sequence multiply(double scale) {
		DataUtils.uncheckedMultiply(scale, ys);
		return this;
	}

	/**
	 * Multiply the y-values of {@code this} sequence by the y-values of another
	 * sequence in place.
	 * 
	 * @param sequence to multiply {@code this} sequence by
	 * @return {@code this} sequence, for use inline
	 * @throws IllegalArgumentException if
	 *         {@code sequence.xValues() != this.xValues()}
	 */
	public ArrayXY_Sequence multiply(ArrayXY_Sequence sequence) {
		DataUtils.uncheckedMultiply(ys, validateSequence(sequence).ys);
		return this;
	}

	private ArrayXY_Sequence validateSequence(ArrayXY_Sequence sequence) {
		checkArgument(checkNotNull(sequence).xHash == xHash);
		return sequence;
	}

}
