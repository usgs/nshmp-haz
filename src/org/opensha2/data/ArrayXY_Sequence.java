package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.data.DataUtils.isMonotonic;
import static org.opensha2.data.DataUtils.uncheckedAdd;
import static org.opensha2.data.DataUtils.uncheckedFlip;
import static org.opensha2.data.DataUtils.uncheckedMultiply;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Function;
import com.google.common.primitives.Doubles;

/**
 * Array based implementation of an {@code XY_Sequence}.
 * 
 * <p>This class provides methods for combining and modifying array-based
 * sequences (e.g. {@link #add(ArrayXY_Sequence)}). These are
 * very efficient implementations that should be used in favor of standard
 * iterators where possible.</p>
 * 
 * <p>If multiple instances of a sequence with a fixed set of x-values are
 * required, use the {@link ArrayXY_Sequence#copyOf(ArrayXY_Sequence)}
 * constructor as it short circuits time-consuming argument validation.</p>
 * 
 * @author Peter Powers
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
		checkArgument(isMonotonic(true, true, xs), "Non-monotonic x-values");
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
	 * Create a new sequence from the supplied value {@code Collection}s.
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
	public static ArrayXY_Sequence create(Collection<Double> xs, Collection<Double> ys) {
		return create(Doubles.toArray(checkNotNull(xs)), Doubles.toArray(checkNotNull(ys)));
	}

	/**
	 * Create a copy of the supplied {@code sequence}.
	 * 
	 * @param sequence to copy
	 * @return a copy of the supplied {@code sequence}
	 * @throws NullPointerException if the supplied {@code sequence} is
	 *         {@code null}
	 */
	public static ArrayXY_Sequence copyOf(ArrayXY_Sequence sequence) {
		return new ArrayXY_Sequence(sequence);
	}
	
	/**
	 * Create a copy of the supplied {@code sequence}.
	 * 
	 * @param sequence to copy
	 * @return a copy of the supplied {@code sequence}
	 * @throws NullPointerException if the supplied {@code sequence} is
	 *         {@code null}
	 */
	public static ArrayXY_Sequence copyOf(XY_Sequence sequence) {
		if (sequence instanceof ArrayXY_Sequence) {
			return copyOf((ArrayXY_Sequence) sequence);
		}
		return create(sequence.xValues(), sequence.yValues());
	}

	/**
	 * Create a resampled version of the supplied {@code sequence}. Method
	 * resamples via linear interpolation and does not extrapolate beyond the
	 * domain of the source {@code sequence}; y-values with x-values outside the
	 * domain of the source sequence are set to 0.
	 * 
	 * @param sequence to resample
	 * @param xs resample values
	 * @return a resampled sequence
	 */
	public static ArrayXY_Sequence resampleTo(XY_Sequence sequence, double[] xs) {
		// NOTE TODO this will support mfd combining
		checkNotNull(sequence);
		checkArgument(checkNotNull(xs).length > 0);
		double[] yResample = Interpolate.findY(sequence.xValues(), sequence.yValues(), xs);
		// TODO disable extrapolation
		if (true) throw new UnsupportedOperationException();
		return ArrayXY_Sequence.create(xs, yResample);
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
		uncheckedAdd(term, ys);
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
		uncheckedAdd(ys, validateSequence(sequence).ys);
		return this;
	}

	/**
	 * Multiply ({@code scale}) the y-values of this sequence in place.
	 * 
	 * @param scale factor
	 * @return {@code this} sequence, for use inline
	 */
	public ArrayXY_Sequence multiply(double scale) {
		uncheckedMultiply(scale, ys);
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
		uncheckedMultiply(ys, validateSequence(sequence).ys);
		return this;
	}

	/**
	 * Sets the y-values of {@code this} sequence to their complement in place [
	 * {@code 1 - y}]. Assumes this is a probability function limited to the
	 * domain [0 1].
	 * 
	 * @return {@code this} sequence, for use inline
	 */
	public ArrayXY_Sequence complement() {
		uncheckedAdd(1, uncheckedFlip(ys));
		return this;
	}
	
	/**
	 * Sets all y-values to 0.
	 * 
	 * @return {@code this} sequence, for use inline
	 */
	public ArrayXY_Sequence clear() {
		Arrays.fill(ys, 0.0);
		return this;
	}
	
	/**
	 * Transforms all y-values in place using the supplied {@link Function}.
	 * 
	 * @param function for transform
	 * @return {@code this} sequence, for use inline
	 */
	public ArrayXY_Sequence transform(Function<Double, Double> function) {
		DataUtils.uncheckedTransform(function, ys);
		return this;
	}

	private ArrayXY_Sequence validateSequence(ArrayXY_Sequence sequence) {
		checkArgument(checkNotNull(sequence).xHash == xHash);
		return sequence;
	}

}
