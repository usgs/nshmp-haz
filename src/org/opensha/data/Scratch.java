package org.opensha.data;

import java.util.Arrays;

import org.opensha.util.Parsing;

import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class Scratch {

	public static void main(String[] args) {
		
		
		double[] xs = new double[] { 0.0010, 0.0013, 0.0018, 0.0024, 0.0033, 0.0044, 0.0059, 0.0080 };
		double[] ys = new double[] { 1.0, 0.95, 0.85, 0.65, 0.35, 0.15, 0.05, 0.0 };

		XY_Sequence xy = ArrayXY_Sequence.create(xs, ys);
		int count = 0;
		XY_Point pp = null;
		for (XY_Point p : xy) {
			System.out.println(p);
			if (count == 3) pp = p;
			count++;
		}
		
		System.out.println(pp);
		
//		double num = 32;
//		double min = 0.001; // g
//		double max = 10.0; // g
//
//		double lnMin = Math.log(min);
//		double lnMax = Math.log(max);
//		double step = (lnMax - lnMin) / (num - 1);
//		
//		double[] seq = DataUtils.buildSequence(lnMin, lnMax, step, true);
//		System.out.println(Arrays.toString(seq));
//		DataUtils.exp(seq);
//		System.out.println(Parsing.toString(Doubles.asList(seq), "%.4f"));
//		System.out.println(seq.length);
//	
//		double[] pp = new double[8];
//		System.out.println(Arrays.toString(pp));
	}
	
	
	
}
