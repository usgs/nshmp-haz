package org.opensha2.eq.fault.scaling.impl;

import org.opensha2.eq.fault.scaling.MagAreaRelationship;
import org.opensha2.function.ArbitrarilyDiscretizedFunc;


/**
 * <b>Title:</b>Shaw_2007_MagAreaRel<br>
 *
 *@deprecated
 * <b>Description:</b>  .<p>
 *
 * @author Edward H. Field
 * @version 1.0
 */

public class Shaw_2009_MagAreaRel extends MagAreaRelationship {

    final static String C = "Shaw_2009_MagAreaRel";
    public final static String NAME = "Shaw (2009)";
    ArbitrarilyDiscretizedFunc magAreaFunc = null;
	public final static double beta=5;
	public final static double h=19;
	public final static double cZero = 3.98;


    /**
     * Computes the median magnitude from rupture area.
     * @param area in km
     * @return median magnitude
     */
    public double getMedianMag(double area){
    	double numer= Math.max(1.0,Math.sqrt(area/(h*h)));
    	double denom= (1 + Math.max(1.0,(area/(beta*h*h))))/2;
    	return  cZero + Math.log10(area) + 0.6667*Math.log10(numer/denom);
    }

    /**
     * Gives the standard deviation for magnitude
     * @return standard deviation
     */
    public double getMagStdDev(){ return Double.NaN;}

    /**
     * Computes the median rupture area from magnitude
     * @param mag - moment magnitude
     * @return median area in km
     */
    public double getMedianArea(double mag){
    	
    	if(magAreaFunc == null) {
        	magAreaFunc = new ArbitrarilyDiscretizedFunc();
        	// area from 1 to 100000 (log area from 0 to 5)
        	for(int i=0; i<=1000; i++) {
        		double logArea = (double)i*5.5/1000.0;
        		double area = Math.pow(10,logArea);
        		double tempMag = getMedianMag(area);
        		magAreaFunc.set(area, tempMag);
        	}
        	/* debugging stuff 
        	System.out.println("firstMag="+magAreaFunc.getY(0));
        	System.out.println("lastMag="+magAreaFunc.getY(magAreaFunc.getNum()-1));
    		ArrayList funcs = new ArrayList();
    		funcs.add(magAreaFunc);
    		funcs.add(this.getMagAreaFunction(4, 0.1, 45));
    		GraphWindow graph = new GraphWindow(funcs, "Mag vs Area");   
*/
    	}
     	return magAreaFunc.getFirstInterpolatedX(mag);
   }

    /**
     * This returns NaN because the value is not available
     * @return standard deviation
     */
    public double getAreaStdDev() {return  Double.NaN;}

    /**
     * Returns the name of the object
     *
     */
    public String name() {
      return NAME;
    }
    
	public static void main(String[] args) {
		Shaw_2009_MagAreaRel test = new Shaw_2009_MagAreaRel();
		test.getMedianArea(7);
	}
}

