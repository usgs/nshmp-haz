package org.opensha.mfd;


import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.opensha.function.EvenlyDiscretizedFunc;
import org.opensha.eq.Magnitudes;


/**
 * <p>Title:IncrementalMFD </p>
 * <p>Description:This class give the rate of earthquakes (number per year) in succesion</p>
 *
 * @author : Nitin Gupta Date:July 26,2002
 * @version 1.0
 */

public class IncrementalMFD extends EvenlyDiscretizedFunc {

    protected String defaultInfo;
    protected String defaultName;

    /**
     * todo constructors
     * @param min
     * @param num
     * @param delta
     * using the parameters we call the parent class constructors to initialise the parent class variables
     */
    public IncrementalMFD (double min,int num,double delta) {
     super(min,num,delta);
     setTolerance(delta/1000000);
    }

    /**
     * todo constructors
     * @param min
     * @param max
     * @param num
     * using the min, max and num we calculate the delta
     */
    public IncrementalMFD(double min,double max,int num) {
      super(min,max,num);
      setTolerance(delta/1000000);
   }


   /**
    * This function finds IncrRate for the given magnitude
    * @param mag
    * @return
    */
    public double getIncrRate(double mag) {
         int xIndex = getXIndex(mag);
         return getIncrRate(xIndex);
    }


    /**
     * This function finds the IncrRate at the given index
     * @param index
     * @return
     */
    public double getIncrRate(int index) {
        return getY(index);
    }


      /**
       * This function finds the cumulative Rate at a specified magnitude (the rate greater than
     * and equal to that mag)
       * @param mag
       * @return
       */
    public double getCumRate(double mag) {
        return getCumRate(getXIndex(mag));
    }


    /**
     * This function finds the cumulative Rate at a specified index  (the rate greater than
     * and equal to that index)
     * @param index
     * @return
     */

    public double getCumRate(int index) {
        double sum=0.0;
        for(int i=index;i<num;++i)
            sum+=getIncrRate(i);
        return sum;
    }



    /**
     * This function finds the moment Rate at a specified magnitude
     * @param mag
     * @return
     */

    public double getMomentRate(double mag) {
      return getIncrRate(mag) * Magnitudes.magToMoment(mag);
    }


    /**
     * This function finds the moment Rate at a specified index
     * @param index
     * @return
     */

    public double getMomentRate(int index) {
      return getIncrRate(index) * Magnitudes.magToMoment(getX(index));
    }



    /**
     * This function return the sum of all the moment rates as a double variable
     * @return
     */

    public double getTotalMomentRate() {
      double sum=0.0;
      for(int i=0;i<num;++i)
         sum+=getMomentRate(i);
      return sum;
    }


    /**
     * This function returns the sum of all the incremental rate as the double varibale
     * @return
     */

    public double getTotalIncrRate() {
      double sum=0.0;
      for(int i=0;i<num;++i)
         sum+=getIncrRate(i);
      return sum;
    }

    /**
     * This function normalises the values of all the Incremental rate at each point, by dividing each one
     * by the totalIncrRate, so that after normalization the sum addition of all incremental rate at each point
     * comes to be 1.
     */

    public void normalizeByTotalRate() {
      double totalIncrRate=getTotalIncrRate();
      for(int i=0;i<num;++i) {
          double newRate= getIncrRate(i)/totalIncrRate;
          super.set(i,newRate);
      }
    }



     /**
      * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
      * with Cum Rate Distribution (the rate greater than and equal to each magnitude)
      * @return
      */

    public EvenlyDiscretizedFunc getCumRateDist() {
      EvenlyDiscretizedFunc cumRateDist = new EvenlyDiscretizedFunc(minX,num,delta);
      double sum=0.0;
      for(int i=num-1;i>=0;--i) {
         sum+=getIncrRate(i);
         cumRateDist.set(i,sum);
      }
      cumRateDist.setInfo(this.getInfo());
      cumRateDist.setName(name());
      return cumRateDist;
    }
    
    /**
     * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
     * with Cum Rate Distribution (the rate greater than and equal to each magnitude).
     * It differs from getCumRateDist() in the X Values because the values are offset
     * by delta/2 in the CumDist returned by this method.
     * 
     * @return
     */

   public EvenlyDiscretizedFunc getCumRateDistWithOffset() {
     EvenlyDiscretizedFunc cumRateDist = new EvenlyDiscretizedFunc(minX-delta/2,num,delta);
     double sum=0.0;
     for(int i=num-1;i>=0;--i) {
        sum+=getIncrRate(i);
        cumRateDist.set(i,sum);
     }
     cumRateDist.setInfo(this.getInfo());
     cumRateDist.setName(name());
     return cumRateDist;
   }


    /**
     * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
     * with Moment Rate Distribution
     * @return
     */

    public EvenlyDiscretizedFunc getMomentRateDist() {
        EvenlyDiscretizedFunc momentRateDist = new EvenlyDiscretizedFunc(minX,num,delta);
        for(int i=num-1;i>=0;--i) {
            momentRateDist.set(i,getMomentRate(i));
        }
        momentRateDist.setInfo(this.getInfo());
        momentRateDist.setName(name());
        return momentRateDist;
    }



    /**
     * This returns the object of the class EvenlyDiscretizedFunc which contains cumulative
     * Moment Rate (the total moment rate for all points greater than and equal to each mag)
     * @return
     */
    public EvenlyDiscretizedFunc getCumMomentRateDist() {
        EvenlyDiscretizedFunc momentRateDist = new EvenlyDiscretizedFunc(minX,num,delta);
        double totMoRate=0;
        for(int i=num-1;i>=0;--i) {
        	totMoRate += getMomentRate(i);
            momentRateDist.set(i,totMoRate);
        }
        momentRateDist.setInfo(this.getInfo());
        momentRateDist.setName(name());
        return momentRateDist;
    }

    /**
     * Using this function each data point is scaled to ratio of specified newTotalMomentRate
     * and oldTotalMomentRate.
     * @param newTotMoRate
     */

    public void scaleToTotalMomentRate(double newTotMoRate) {
        double oldTotMoRate=getTotalMomentRate();
        if(D) System.out.println("old Mo. Rate = " + oldTotMoRate);
        if(D) System.out.println("target Mo. Rate = " + newTotMoRate);
        double scaleRate=newTotMoRate/oldTotMoRate;
        for(int i=0;i<num;++i) {
            super.set(i,scaleRate*getIncrRate(i));
        }
        if(D) System.out.println("actual Mo. Rate = " + getTotalMomentRate());


    }


    /**
     * Using this function each data point is scaled to the ratio of the CumRate at a given
     * magnitude and the specified rate.
     * @param mag
     * @param rate
     */

    public void scaleToCumRate(double mag,double rate) {
        int index = getXIndex(mag);
        scaleToCumRate(index,rate);
    }



    /**
     * Using this function each data point is scaled to the ratio of the CumRate at a given
     * index and the specified rate
     * @param index
     * @param rate
     */

   public void scaleToCumRate(int index,double rate) {
        double temp=getCumRate(index);
        double scaleCumRate=rate/temp;
        for(int i=0;i<num;++i)
            super.set(i,scaleCumRate*getIncrRate(i));
   }



   /**
    * Using this function each data point is scaled to the ratio of the IncrRate at a given
    * magnitude and the specified newRate
    * @param mag
    * @param newRate
    */

    public void scaleToIncrRate(double mag, double newRate) {
        int index = getXIndex(mag);
        scaleToIncrRate(index,newRate);
    }


    /**
     * Using this function each data point is scaled to the ratio of the IncrRate at a given
     * index and the specified newRate
     * @param index
     * @param newRate
     */

    public void scaleToIncrRate(int index, double newRate) {
        double temp=getIncrRate(index);
        double scaleIncrRate=newRate/temp;
        for(int i=0;i<num;++i)
            super.set(i,scaleIncrRate*getIncrRate(i));
    }

    /**
     * Returns the default Info String for the Distribution
     * @return String
     */
    public String getDefaultInfo(){
      return defaultInfo;
    }

    /**
     * Returns the default Name for the Distribution
     * @return String
     */
    public String getDefaultName(){
      defaultName = "Incremental Mag Freq Dist";
      return defaultName;
    }

    /**
     * Returns the Name of the Distribution that user has set from outside,
     * if it is null then it returns the default Name from the distribution.
     * Makes the call to the parent "getName()" method to get the metadata
     * set outside the application.
     * @return String
     */
    @Override
    public String name(){
      if(name !=null && !(name.trim().equals("")))
        return super.name();
      return getDefaultName();
    }


    /**
     * Returns the info of the distribution that user has set from outside,
     * if it is null then it returns the default info from the distribution.
     * Makes the call to the parent "getInfo()" method to get the metadata
     * set outside the application.
     * @return String
     */
    public String getInfo(){
      if(info !=null && !(info.equals("")))
        return super.getInfo();
      return getDefaultInfo();
    }


    /** Returns a copy of this and all points in this DiscretizedFunction */
   public IncrementalMFD deepClone() {

       IncrementalMFD f = new IncrementalMFD(
           minX, num, delta
       );

       f.tolerance = tolerance;
       f.setInfo(this.getInfo());
       f.setName(name());
       for(int i = 0; i<num; i++)
           f.set(i, points[i]);

       return f;
   }
   
   /**
    * This returns the maximum magnitude with a non-zero rate
    * @return
    */
   public double getMinMagWithNonZeroRate() {
	   for(int i=0; i<num; i++) {
		   if(getY(i)>0) return getX(i);
	   }
	   return Double.NaN;
   }
   
   /**
    * This returns the maximum magnitude with a non-zero rate
    * @return
    */
   public double getMaxMagWithNonZeroRate() {
	   for(int i=num-1; i>=0; i--) {
		   if(getY(i)>0) return getX(i);
	   }
	   return  Double.NaN;
   }

   
   /**
	* This computes the b-value (the slope of the line of a linear-log plot, meaning
    * after computing log10 of all y-axis values) between the the given x-axis values.
    * If Double.NaN is passed in, then the first (or last) non-zero rate is used for
    * min_bValMag (or max_bValMag).
    * @param min_bValMag
    * @param max_bValMag
    * @return
    */
   public double compute_bValue(double min_bValMag, double max_bValMag) {
	   int firstIndex, lastIndex;

	   if(Double.isNaN(min_bValMag))
		   firstIndex = getClosestXIndex(getMinMagWithNonZeroRate());
	   else
		   firstIndex = getClosestXIndex(min_bValMag);

	   if(Double.isNaN(max_bValMag))
		   lastIndex = getClosestXIndex(getMaxMagWithNonZeroRate());
	   else
		   lastIndex = getClosestXIndex(max_bValMag);

	   SimpleRegression regression = new SimpleRegression();
	   for(int i=firstIndex; i<=lastIndex; i++) {
		   if(getY(i)>0.0)	// avoid taking log of zero
			   regression.addData(getX(i), Math.log10(getY(i)));
	   }
	   
//	   if(getX(lastIndex)-getX(firstIndex) <1.0)
//		   return Double.NaN;

	   return regression.getSlope();
   }
   
   /**
    * This sets all y-axis values above the given total moment rate to zero.
    * The final total moment rate will be something less than the value passed in.
    * @param moRate
    */
   public void setValuesAboveMomentRateToZero(double moRate) {
	   double mag=findMagJustAboveMomentRate(moRate);
	   if(Double.isNaN(mag)) return;
	   zeroAboveMag(mag);
   }
   
   /**
    * This finds the smallest magnitude such that all those less than and equal
    * to this have a cumulative moment rate less than that passed in.
    * @param moRate - in Nm/yr
    */
   public double findMagJustAboveMomentRate(double moRate) {
	   double cumMoRate=0;
	   int targetIndex = -1;
	   for(int i=0;i<getNum();i++) {
		   cumMoRate += getMomentRate(i);
		   if(cumMoRate>moRate) {
			   targetIndex = i-1;
			   break;
		   }
	   }
	   if(targetIndex == -1)
		   return Double.NaN;
	   else
		   return getX(targetIndex);
	   
   }
   
	/**
	 * Sets the rate of all magnitudes above the supplied magnitude to 0.
	 * @param mag 
	 * TODO this is awful this assumes you know the (almost) exact magnitude
	 * (wihtin tolerance) of a mag in the MFD; if you just pick an arbitrary
	 * value, internally an index of -1 will be returned and all values
	 * will be set to Zero
	 */
	public void zeroAboveMag(double mag) {
		for (int i = getXIndex(mag)+1; i < getNum(); i++) {
			set(i, 0);
		}
	}
	
	public void zeroAboveMag2(double mag) {
		for (int i=0; i < getNum(); i++) {
			if (getX(i) > mag) set(i, 0);
		}
	}
	
	/**
	 * Sets the rate of all magnitudes above the supplied magnitude to 0.
	 * @param mag 
	 */
	public void zeroAtAndAboveMag(double mag) {
		for (int i = getXIndex(mag); i < getNum(); i++) {
			set(i, 0);
		}
	}   
   
   /**
    * This computes the b-value (the slope of the line of a linear-log plot, meaning
    * after computing log10 of all y-axis values) between the smallest and largest 
    * mags with non-zero rates (zeros at the beginning and end of the distribution 
    * are ignored).
    * @return
    */
   public double compute_bValue() {
	   return compute_bValue(Double.NaN, Double.NaN);
   }


}
