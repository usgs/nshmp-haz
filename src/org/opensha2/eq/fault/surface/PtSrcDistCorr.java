package org.opensha2.eq.fault.surface;

import java.util.ArrayList;

import org.opensha2.function.EvenlyDiscretizedFunc;
import org.opensha2.gmm.GmmUtils;

/**
 * This implements various point-source distance corrections.
 * @author field
 *
 */
@Deprecated
public class PtSrcDistCorr {
	
	public enum Type {
	    NONE, FIELD, NSHMP08 
	}
	
	public static double getCorrection(double horzDist, double mag, Type type) {
		double corr=Double.NaN;
		switch (type) {
			case NONE:
				corr = 1.0;
				break;
			case FIELD: 
				// Wells and Coppersmith L(M) for "all" focal mechanisms
				// this correction comes from work by Ned Field and Bruce Worden
				// it assumes a vertically dipping straight fault with random
				// hypocenter and strike
				double rupLen =  Math.pow(10.0,-3.22+0.69*mag);
				corr = 0.7071 + (1.0-0.7071)/(1 + Math.pow(rupLen/(horzDist*0.87),1.1));
				break;
			case NSHMP08:
				if(mag<=6) return 1.0;
					//if (mag<=7.6) {
					// NSHMP getMeanRJB is built on the assumption of 0.05 M
					// centered bins. Non-UCERF erf's often do not make
					// this assumption and are 0.1 based so we push
					// the value down to the next closest compatible M
					
					// this was Peter's original correction, but it explodes if it's given say 6.449999999999999 (which converts to 6.39999999999999)
//					double adjMagAlt = ((int) (mag*100) % 10 != 5) ? mag - 0.05 : mag;
					double adjMag = ((double)Math.round(mag/0.05))*0.05;
					if (adjMag > 8.6) adjMag = 8.55;
//					if(adjMagAlt != adjMag)
//						System.out.println("mag,adj,alt:\t"+mag+"\t"+adjMag+"\t"+adjMagAlt);
					if(horzDist==0)
						corr = 1;
					else
						corr = GmmUtils.getMeanRJB(adjMag, horzDist)/horzDist;
					break;
//				}
//				else
//					throw new RuntimeException("PtSrcDistCorr.Type.NSHMP08 cannot be used above mag 7.6; your mag is "+mag);
		}
		return corr;
	}
	
//	public static void plotTest() {
//		ArrayList<EvenlyDiscretizedFunc> funcs = new  ArrayList<EvenlyDiscretizedFunc>();
//		
//		double[] horzDists = {0,0.5,1,5,50,150,250};
//		for(int d=0; d<horzDists.length; d++) {
//			double horzDist = horzDists[d];
//			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(5.05,35,0.1);
//			for(int m=0; m<func.getNum(); m++) {
//				double mag = func.getX(m);
//				double corr = PtSrcDistCorr.getCorrection(horzDist, mag, PtSrcDistCorr.Type.FIELD);
//				func.set(m, corr);
//			}
//			func.setName("Dist="+horzDist+";\tType=FIELD");
//			funcs.add(func);
//			func = new EvenlyDiscretizedFunc(5.05,35,0.1);
//			for(int m=0; m<func.getNum(); m++) {
//				double mag = func.getX(m);
//				if(mag<=7.6) {
////					double roundedMag = ((double)Math.round(mag*100.0))/100;
//					double roundedMag = mag;
//					double corr = PtSrcDistCorr.getCorrection(horzDist, roundedMag, PtSrcDistCorr.Type.NSHMP08);
//					func.set(m, corr);
//				}
//				else
//					func.set(m, Double.NaN);
//			}
//			func.setName("Dist="+horzDist+";\tType=NSHMP08");
//			funcs.add(func);
//		}
//		
//		GraphWindow graph = new GraphWindow(funcs, "Distance Corrections "); 
//		graph.setX_AxisLabel("Magnitude");
//		graph.setY_AxisLabel("Correction");
//
//	}
	
	
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) {
//		
//		PtSrcDistCorr.plotTest();
//		
////		double[] mags = {5, 6, 7, 7.6, 7.61};
////		double horzDist = 50.0;
////		for(int m=0; m<mags.length; m++) {
////			double mag = mags[m];
////			System.out.println("M "+mag+" corrections:\n");
////			System.out.println("\tNONE: "+PtSrcDistCorr.getCorrection(horzDist, mag, PtSrcDistCorr.Type.NONE));
////			System.out.println("\tFIELD: "+PtSrcDistCorr.getCorrection(horzDist, mag, PtSrcDistCorr.Type.FIELD));
////			System.out.println("\tNSHMP08: "+PtSrcDistCorr.getCorrection(horzDist, mag, PtSrcDistCorr.Type.NSHMP08));
////
////		}
//
//	}


}

