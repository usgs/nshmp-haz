package org.opensha.eq.fault.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Locations;

import com.google.common.collect.Lists;

/**
 *  <b>Title:</b> SimpleFaultData<p>
 *  <b>Description:</b> This object contains "simple fault data".  
 *  This does not check whether the values make sense
 *  (e.g., it doesn not check that 0<aveDip<90) because these will get checked in the
 *  classes that use this data (and we don't want duplicate these checks). <p>
 *
 *
 * @author     Sid Hellman, Steven W. Rock, Ned Field
 * @created    February 26, 2002
 * @version    1.0
 */
@Deprecated
public class SimpleFaultData {

    private double upperSeismogenicDepth;
    private double lowerSeismogenicDepth;
    private double aveDip;
    private double aveDipDir = Double.NaN;
    private LocationList faultTrace;


    public SimpleFaultData(){ }

    public SimpleFaultData(double aveDip, double lowerSeisDepth,
                           double upperSeisDepth, LocationList faultTrace) {

        this.aveDip = aveDip;
        this.lowerSeismogenicDepth = lowerSeisDepth;
        this.upperSeismogenicDepth = upperSeisDepth;
        this.faultTrace = faultTrace;

    }

    public SimpleFaultData(double aveDip, double lowerSeisDepth, double upperSeisDepth,
    		LocationList faultTrace, double aveDipDir) {

    	this.aveDip = aveDip;
    	this.lowerSeismogenicDepth = lowerSeisDepth;
    	this.upperSeismogenicDepth = upperSeisDepth;
    	this.faultTrace = faultTrace;
    	this.aveDipDir = aveDipDir;

    }


    public void setUpperSeismogenicDepth(double upperSeismogenicDepth) { this.upperSeismogenicDepth = upperSeismogenicDepth; }
    public double getUpperSeismogenicDepth() { return upperSeismogenicDepth; }

    public void setLowerSeismogenicDepth(double lowerSeismogenicDepth) { this.lowerSeismogenicDepth = lowerSeismogenicDepth; }
    public double getLowerSeismogenicDepth() { return lowerSeismogenicDepth; }

    public void setAveDip(double aveDip) { this.aveDip = aveDip; }
    public double getAveDip() { return aveDip; }
    
    public void setAveDipDir(double aveDipDir) { this.aveDipDir = aveDipDir; }
    public double getAveDipDir() { return aveDipDir; }

    public void setFaultTrace(LocationList faultTrace) {
        this.faultTrace = faultTrace;
    }
    public LocationList getFaultTrace() { return faultTrace; }
    
//    /**
//     * Get a single combined simpleFaultData from multiple SimpleFaultData
//     * @param simpleFaultDataList
//     * @return
//     */
//    @Deprecated
//    public static SimpleFaultData getCombinedSimpleFaultData(List<SimpleFaultData> simpleFaultDataList) {
//    	if(simpleFaultDataList.size()==1) {
//    		return simpleFaultDataList.get(0);
//    	}                                      
//    	// correctly order the first fault section
//    	LocationList faultTrace1 = simpleFaultDataList.get(0).getFaultTrace();
//    	LocationList faultTrace2 =  simpleFaultDataList.get(1).getFaultTrace();
//    	double minDist = Double.MAX_VALUE, distance;
//    	boolean reverse = false;
////    	ArrayList<Integer> reversedIndices = new ArrayList<Integer>();
//    	LocationList locs1 = simpleFaultDataList.get(0).getFaultTrace(); // faultTrace1.locs();
//    	LocationList locs2 = simpleFaultDataList.get(1).getFaultTrace(); // faultTrace2.locs();
//    	distance = Locations.horzDistanceFast(locs1.first(), locs2.first());
//    	if(distance<minDist) {
//    		minDist = distance;
//    		reverse=true;
//    	}
//    	distance = Locations.horzDistanceFast(locs1.first(), locs2.last());
//    	if(distance<minDist) {
//    		minDist = distance;
//    		reverse=true;  
//    	}
//    	distance = Locations.horzDistanceFast(locs1.last(), locs2.first());
//    	if(distance<minDist) {
//    		minDist = distance;
//    		reverse=false;
//    	}
//    	distance = Locations.horzDistanceFast(locs1.last(), locs2.last());
//    	if(distance<minDist) {
//    		minDist = distance;
//    		reverse=false;
//    	}
//    	if(reverse) {
//    		reversedIndices.add(0);
////    		faultTrace1.reverse();
//    		faultTrace1 = LocationList.reverseOf(faultTrace1);
//    		if( simpleFaultDataList.get(0).getAveDip()!=90)  simpleFaultDataList.get(0).setAveDip(- simpleFaultDataList.get(0).getAveDip());
//    	}
//    	
//    	// Calculate Upper Seis Depth, Lower Seis Depth and Dip
//    	double combinedDip=0, combinedUpperSeisDepth=0, totArea=0, totLength=0;
////    	FaultTrace combinedFaultTrace = new FaultTrace("Combined Fault Sections");
//    	List<Location> combinedFaultTrace = Lists.newArrayList();
//    	int num = simpleFaultDataList.size();
//    	
//    	for(int i=0; i<num; ++i) {
////    		FaultTrace faultTrace = simpleFaultDataList.get(i).getFaultTrace();
//    		LocationList faultTrace = simpleFaultDataList.get(i).getFaultTrace();
//    		int numLocations = faultTrace.size();
//    		if(i>0) { // check the ordering of point in this fault trace
////    			FaultTrace prevFaultTrace = simpleFaultDataList.get(i-1).getFaultTrace();
//    			LocationList prevFaultTrace = simpleFaultDataList.get(i-1).getFaultTrace();
//    			Location lastLoc = prevFaultTrace.get(prevFaultTrace.size()-1);
//    			double distance1 = Locations.horzDistance(lastLoc, faultTrace.get(0));
//    			double distance2 = Locations.horzDistance(lastLoc, faultTrace.get(faultTrace.size()-1));
//    			if(distance2<distance1) { // reverse this fault trace
////    				faultTrace.reverse();
//    				faultTrace = LocationList.reverseOf(faultTrace);
//    				reversedIndices.add(i);
//    				if(simpleFaultDataList.get(i).getAveDip()!=90) simpleFaultDataList.get(i).setAveDip(-simpleFaultDataList.get(i).getAveDip());
//    			}
//    			//  remove any loc that is within 1km of its neighbor
//            	//  as per Ned's email on Feb 7, 2007 at 5:53 AM
////        		if(distance2>1 && distance1>1) combinedFaultTrace.add(faultTrace.get(0).clone());
//        		if(distance2>1 && distance1>1) combinedFaultTrace.add(faultTrace.first());
//        		// add the fault Trace locations to combined trace
//        		for(int locIndex=1; locIndex<numLocations; ++locIndex) 
//        			combinedFaultTrace.add(faultTrace.get(locIndex));
//       
//    		} else { // if this is first fault section, add all points in fault trace
////    			 add the fault Trace locations to combined trace
//        		for(int locIndex=0; locIndex<numLocations; ++locIndex) 
//        			combinedFaultTrace.add(faultTrace.get(locIndex));
//    		}
//    		
//    		double length = faultTrace.length();
//    		double dip = simpleFaultDataList.get(i).getAveDip();
//    		double area = Math.abs(length*(simpleFaultDataList.get(i).getLowerSeismogenicDepth()-simpleFaultDataList.get(i).getUpperSeismogenicDepth())/Math.sin(dip*Math.PI/ 180));
//    		totLength+=length;
//    		totArea+=area;
//    		combinedUpperSeisDepth+=(area*simpleFaultDataList.get(i).getUpperSeismogenicDepth());
//    		if(dip>0)
//    			combinedDip += (area * dip);
//    		else combinedDip+=(area*(dip+180));
//    		//System.out.println(dip+","+area+","+combinedDip+","+totArea);
//    	}
//    	
//    	// Revert back the fault traces that were reversed
//    	// we should always be copying data out of the original list so this shouldn't be needed
//    	for(int i=0; i<reversedIndices.size(); ++i) {
//    		int index = reversedIndices.get(i);
//    		simpleFaultDataList.get(index).getFaultTrace().reverse();
//    		if(simpleFaultDataList.get(index).getAveDip()!=90) simpleFaultDataList.get(index).setAveDip(- simpleFaultDataList.get(index).getAveDip());
//    	}
//
//    	
//    	double dip = combinedDip/totArea;
//    	
//    	//double tolerance = 1e-6;
//		//if(dip-90 < tolerance) dip=90;
////   	 if Dip<0, reverse the trace points to follow Aki and Richards convention
//    	if(dip>90) {
//    		dip=(180-dip);
//    		combinedFaultTrace.reverse();
//    	}
//    	
//    	//System.out.println(dip);
//    	
//    	SimpleFaultData simpleFaultData = new SimpleFaultData();
//    	simpleFaultData.setAveDip(dip);
//    	double upperSeismogenicDepth = combinedUpperSeisDepth/totArea;
//    	simpleFaultData.setUpperSeismogenicDepth(upperSeismogenicDepth);
//    	
//    	for(int i=0; i<combinedFaultTrace.size(); ++i) {
//    		//combinedFaultTrace.getLocationAt(i).setDepth(
//    		//		simpleFaultData.getUpperSeismogenicDepth());
//    		// replace trace Locations with depth corrected values
//    		Location old = combinedFaultTrace.get(i);
//    		Location loc = Location.create(
//    				old.lat(), 
//    				old.lon(),
//    				upperSeismogenicDepth);
//    		combinedFaultTrace.set(i, loc);
//    	}
//    	simpleFaultData.setLowerSeismogenicDepth((totArea/totLength)*Math.sin(dip*Math.PI/180)+upperSeismogenicDepth);
//    	//System.out.println(simpleFaultData.getLowerSeismogenicDepth());
//    	simpleFaultData.setFaultTrace(combinedFaultTrace);
//    	return simpleFaultData;
// 
//    }
    

    private final static String TAB = "  ";
    public String toString(){

        StringBuffer b = new StringBuffer("Simple Fault Data");
        b.append('\n');
        b.append(TAB + "Ave. Dip = " + aveDip);
        b.append(TAB + "Upper Seismogenic Depth = " + upperSeismogenicDepth);
        b.append(TAB + "Lower Seismogenic Depth = " + lowerSeismogenicDepth);
        b.append(TAB + "Fault Trace = " + faultTrace.toString() ) ;
        return b.toString();

    }
    
    /**
     * Clones the SimpleFaultData. Please note that FaultTrace is not completely cloned
     */
    public SimpleFaultData clone() {
    	SimpleFaultData simpleFaultData = new SimpleFaultData();
    	simpleFaultData.setUpperSeismogenicDepth(upperSeismogenicDepth);
    	simpleFaultData.setLowerSeismogenicDepth(lowerSeismogenicDepth);
    	simpleFaultData.setAveDip(aveDip);
    	simpleFaultData.setFaultTrace(faultTrace);
    	return simpleFaultData;
    }

}
