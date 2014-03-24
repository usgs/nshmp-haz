/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.eq.fault.scaling.impl;

import org.opensha.eq.fault.scaling.MagAreaRelDepthDep;
import org.opensha.eq.fault.scaling.MagAreaRelationship;
import org.opensha.function.ArbitrarilyDiscretizedFunc;


/**
 * <b>Title:</b>Shaw_2007_MagAreaRel<br>
 *
 * <b>Description:  This is a modified version of Shaw (2009) where beta and h are changed,
 * and where there is an option to pass the down-dip width in.</b>  .<p>
 *
 * @author Edward H. Field
 * @version 1.0
 */

public class Shaw_2009_ModifiedMagAreaRel extends MagAreaRelationship implements MagAreaRelDepthDep {

    final static String C = "Shaw_2009_ModifiedMagAreaRel";
    public final static String NAME = "Shaw (2009) Modified";
    ArbitrarilyDiscretizedFunc magAreaFunc = null;
	public final static double beta=7.4;
	public final static double h=15;
	public final static double cZero = 3.98;
	
    /**
     * Computes the median magnitude from rupture area and original down-dip width
     * (not reduced by any aseismicity)
     * @param area in km-squared
     * @param origWidth in km
     * @return median magnitude
     */
    public  double getWidthDepMedianMag(double area, double origWidth) {
    	double numer= Math.max(1.0,Math.sqrt(area/(origWidth*origWidth)));
    	double denom= (1 + Math.max(1.0,(area/(beta*origWidth*origWidth))))/2;
    	return  cZero + Math.log10(area) + 0.6667*Math.log10(numer/denom);
    }



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
		Shaw_2009_ModifiedMagAreaRel test = new Shaw_2009_ModifiedMagAreaRel();
		if(test.name().equals(Shaw_2009_ModifiedMagAreaRel.NAME))
			System.out.println("test OK");

		test.getMedianArea(7);
	}
}

