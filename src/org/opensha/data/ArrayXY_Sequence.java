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
	
	// TODO revisit making defensive copies; copyOf constructors should reuse
	// exisitng immutable x-arrays
	
	/*
	 * Only for use by static factory methods. Create a new sequence from an
	 * existing one; copies the fields of {@code seq} to {@code this}.
	 */
	ArrayXY_Sequence(ArrayXY_Sequence seq) {
		checkNotNull(seq, "Sequence to copy is null");
		xs = Arrays.copyOf(seq.xs, seq.xs.length);
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
		// TODO shouldn't this use the existing x-array
		return new ArrayXY_Sequence(sequence);
	}
	
	@Override
	public double getX(int index) {
		checkElementIndex(index, xs.length);
		return getXunchecked(index);
	}
	
	@Override
	double getXunchecked(int index) {
		return xs[index];
	}

	@Override
	public double getY(int index) {
		checkElementIndex(index, ys.length);
		return getYunchecked(index);
	}

	@Override
	double getYunchecked(int index) {
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
	
	/**
	 * Adds the supplied sequence to {@code this}.
	 * @param sequence to add
	 * @throws NullPointerException if {@code sequence} is {@code null}
	 * @throws IllegalArgumentException if the x-values of the the supplied
	 *         sequence are not the same as those of {@code this}
	 */
	public void add(ArrayXY_Sequence sequence) {
		checkArgument(checkNotNull(sequence).xHash == xHash);
		uncheckedAdd(ys, sequence.ys);
	}


//	@Override
//	public void subtract(XY_Sequence values) {
//		// TODO do nothing
//
//	}
//
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
	
	
//	/**
//	 * Adds the supplied sequence to {@code this}.
//	 * @param values to add
//	 * @throws NullPointerException if {@code values} is {@code null}
//	 * @throws IllegalArgumentException if the x-values of the the supplied
//	 *         sequence are not the same as those of {@code this}
//	 */
//	public void add(XY_Sequence values);
//	
//	
//	/**
//	 * Adds the supplied sequence to {@code this}.
//	 * @param values to add
//	 * @throws NullPointerException if {@code values} is {@code null}
//	 * @throws IllegalArgumentException if the x-values of the the supplied
//	 *         sequence are not the same as those of {@code this}
//	 */
//	public void subtract(XY_Sequence values);
//	
//	/**
//	 * Scale the y-values of this sequence by some {@code value}.
//	 * @param value to scale y-values by
//	 */
//	public void scale(double value);


}
