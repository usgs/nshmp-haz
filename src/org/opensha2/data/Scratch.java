package org.opensha2.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.opensha2.function.ArbitrarilyDiscretizedFunc;
import org.opensha2.function.DiscretizedFunc;
import org.opensha2.function.EvenlyDiscretizedFunc;
import org.opensha2.util.Parsing;

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
class Scratch {
	static double[] xs = new double[] { 0.0010, 0.0013, 0.0016, 0.0019, 0.0022, 0.0025, 0.0028, 0.0031 };
	static double[] ys = new double[] { 1.0, 0.95, 0.85, 0.65, 0.35, 0.15, 0.05, 0.0 };

	/*
	 * TODO this is probably important to save somewhere wrt
	 * to performance, clarity/readability, and conciseness
	 * improvements realized by XySequence
	 */
	public static void main(String[] args) {
		
		sequenceTest();

//		XySequence xy = XySequence.create(xs, ys);
//		int count = 0;
//		XyPoint pp = null;
//		for (XyPoint p : xy) {
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
		
		System.out.println("Starting ArbitrarilyDiscretizedFunc...");
		ArbitrarilyDiscretizedFunc adf = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<xs.length; i++) {
			adf.set(xs[i], ys[i]);
		}
		int numPoints = adf.getNum();
		ArbitrarilyDiscretizedFunc adfReceiver = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<xs.length; i++) {
			adfReceiver.set(xs[i], 0);
		}
		
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
		System.out.println("Time: " + sw.stop());
		System.out.println(adfReceiver);
		System.out.println();
		

		System.out.println("Starting EvenlyDiscretizedFunction...");
		EvenlyDiscretizedFunc edf = new EvenlyDiscretizedFunc(0.0010, 8, 0.0003);
		for (int i=0; i<xs.length; i++) {
			edf.set(i, ys[i]);
		}
		numPoints = edf.getNum();
		EvenlyDiscretizedFunc edfReceiver = new EvenlyDiscretizedFunc(0.0010, 8, 0.0003);
		for (int i=0; i<xs.length; i++) {
			edfReceiver.set(i, 0);
		}
		
		sw.reset().start();
		for (int i=0; i<its; i++) {
			// why on earth does this return DF when ADF.deepCLone returns an ADF?
			DiscretizedFunc copy = edf.deepClone(); 
			
			for (int k=0; k<numPoints; k++) {
				copy.set(k, copy.getY(k) * copy.getY(k));
			}
			
			for (int k=0; k<numPoints; k++) {
				double y = edfReceiver.get(k).getY();
				edfReceiver.set(k, copy.get(k).getY() + y);
			}
		}
		System.out.println("Time: " + sw.stop());
		System.out.println(edfReceiver);
		System.out.println();

		

		System.out.println("Starting XySequence...");
		XySequence xy = XySequence.createImmutable(xs, ys);
		XySequence xyReceiver =  XySequence.emptyCopyOf(xy);

		sw.reset().start();
		for (int i=0; i<its; i++) {
			XySequence copy = XySequence.copyOf(xy);
			copy.multiply(xy);
			xyReceiver.add(copy);
		}
		System.out.println("Time: " + sw.stop());
		System.out.println(xyReceiver);
		
		
	}
	
	
	
}
