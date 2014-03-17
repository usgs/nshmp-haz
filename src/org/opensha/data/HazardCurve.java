package org.opensha.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.data.DataUtils.*;

import java.util.Arrays;

/**
 * A custom XY_Sequence whose y-values must decrease monotonically. This check
 * is made upon creation with new data but may not be enforced as y-values are
 * updated/mutated.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class HazardCurve extends ArrayXY_Sequence {

	/* Only for use by static factory methods. See parent. */
	HazardCurve(ArrayXY_Sequence seq) {
		super(seq);
	}

	/* Only for use by static factory methods. See parent. */
	HazardCurve(double[] xs, double[] ys) {
		super(xs, ys);
		checkArgument(DataUtils.isMonotonic(false, true, ys));
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
	public static HazardCurve create(double[] xs, double[] ys) {
		return new HazardCurve(xs, ys);
	}

	/**
	 * Copy the supplied sequence to a new sequence.
	 * 
	 * @param sequence to copy
	 * @return a copy of the supplied {@code sequence}
	 * @throws NullPointerException if the supplied {@code sequence} is
	 *         {@code null}
	 */
	public static HazardCurve copyOf(ArrayXY_Sequence sequence) {
		return new HazardCurve(sequence);
	}

}
