package org.opensha2.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.opensha2.function.ArbitrarilyDiscretizedFunc;
import org.opensha2.util.Parsing;

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
class Scratch {
	static double[] xs = new double[] { 0.0010, 0.0013, 0.0018, 0.0024, 0.0033, 0.0044, 0.0059, 0.0080 };
	static double[] ys = new double[] { 1.0, 0.95, 0.85, 0.65, 0.35, 0.15, 0.05, 0.0 };

	public static void main(String[] args) {
		
		sequenceTest();

//		XY_Sequence xy = ArrayXY_Sequence.create(xs, ys);
//		int count = 0;
//		XY_Point pp = null;
//		for (XY_Point p : xy) {
//			System.out.println(p);
//			if (count == 3) pp = p;
//			count++;
//		}
//		
//		System.out.println(pp);
		
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
	
	private static void sequenceTest() {
		int its = 100000000;
		
		ArbitrarilyDiscretizedFunc adf = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<xs.length; i++) {
			adf.set(xs[i], ys[i]);
		}
		int numPoints = adf.getNum();
		ArbitrarilyDiscretizedFunc adfReceiver = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<xs.length; i++) {
			adfReceiver.set(xs[i], 0);
		}
		
		
		System.out.println("Starting ArbDiscrFunc...");
		Stopwatch sw = Stopwatch.createStarted();
		for (int i=0; i<its; i++) {
			ArbitrarilyDiscretizedFunc copy = adf.deepClone();
			
			for (int k=0; k<numPoints; k++) {
				copy.set(k, copy.getY(k) * copy.getY(k));
			}
			
			for (int k=0; k<numPoints; k++) {
				double y = adfReceiver.get(k).getY();
				adfReceiver.set(k, copy.get(k).getY() + y);
			}
		}
		System.out.println(sw.stop().elapsed(TimeUnit.MILLISECONDS));
		System.out.println(adfReceiver);
		System.out.println();
		

		
		ArrayXY_Sequence axy = ArrayXY_Sequence.create(xs, ys);
		ArrayXY_Sequence xyReceiver =  ArrayXY_Sequence.create(xs, null);

		System.out.println("Starting XY_Sequence...");
		sw.reset().start();
		for (int i=0; i<its; i++) {
			ArrayXY_Sequence copy = ArrayXY_Sequence.copyOf(axy);
			copy.multiply(axy);
			xyReceiver.add(copy);
		}
		System.out.println(sw.stop().elapsed(TimeUnit.MILLISECONDS));
		System.out.println(xyReceiver);
		
		
	}
	
	
	
}
