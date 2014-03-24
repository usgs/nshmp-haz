package org.opensha.eq.forecast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagAreaRelationship;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.surface.AbstractEvenlyGriddedSurface;
import org.opensha.eq.fault.surface.AbstractEvenlyGriddedSurfaceWithSubsets;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.BorderType;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Locations;
import org.opensha.geo.Region;
import org.opensha.geo.Regions;
import org.opensha.eq.forecast.Rupture;
import org.opensha.eq.forecast.Source;
import org.opensha.mfd.GaussianMFD;
import org.opensha.mfd.IncrementalMFD;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


/**
 * <p>Title: FloatingPoissonFaultSource </p>
 * <p>Description: This implements a basic Poisson fault source for arbitrary: <p>
 * <UL>
 * <LI>magDist - any IncrementalMFD (rate per year)
 * <LI>faultSurface - any EvenlyDiscretizedSurface
 * <LI>magScalingRel- any magLenthRelationship or magAreaRelalationship
 * <LI>magScalingSigma - the standard deviation of log(Length) or log(Area)
 * <LI>rupAspectRatio - the ratio of rupture length to rupture width (down-dip)
 * <LI>rupOffset - the amount by which ruptures are offset on the fault.
 * <LI>rake - that rake (in degrees) assigned to all ruptures.
 * <LI>minMag - the minimum magnitude to be considered from magDist (lower mags are ignored)
 * <LI>floatTypeFlag - if = 0 full down-dip width ruptures; if = 1 float both along strike and down dip; 
 *                        if = 2 float only along strike and centered down dip.
 * <LI>fullFaultRupMagThresh - magnitudes greater than or equal to this value will be forced to rupture the entire fault
 * <LI>duration - the duration of the forecast in years.
 * </UL><p>
 * 
 * Note that few of these input objects are saved internally (after construction) in
 * order to conserve memory (this is why there are no associated get/set methods for each).<p>
 * The floatTypeFlag specifies the type of floaters as described above.  For floating,
 * ruptures are placed uniformly across the fault surface (at rupOffset spacing), which
 * means there is a tapering of implied slip amounts at the ends of the fault.<p>
 * All magnitudes below minMag in the magDist are ignored in building the ruptures. <p>
 * Note that magScalingSigma can be either a MagAreaRelationship or a
 * MagLengthRelationship.  If a MagAreaRelationship is being used, and the rupture
 * width implied for a given magnitude exceeds the down-dip width of the faultSurface,
 * then the rupture length is increased accordingly and the rupture width is set as
 * the down-dip width.  If a MagLengthRelationship is being used, and the rupture
 * width implied by the rupAspectRatio exceeds the down-dip width, everything below
 * the bottom edge of the fault is simply cut off (ignored).  Thus, with a
 * MagLengthRelationship you can force rupture of the entire down-dip width by giving
 * rupAspecRatio a very small value (using floatTypeFlag=1).  The fullFaultRupMagThresh
 * value allows you to force full-fault ruptures for large mags.</p>
 * magScalingSigma is set by hand (rather than getting it from the magScalingRel) to
 * allow maximum flexibility (e.g., some relationships do not even give a sigma value).<p>
 * If magScalingSigma is non zero, then 25 branches from -3 to +3 sigma are considered 
 * for the Area or Length values (this high number was implemented to match PEER test
 * cases); the option for other numbers of branches should be added to speed things up
 * if this feature will be widely used.<p>
 * 
 * To Do: 1) generalize makeFaultCornerLocs() to work better for large surfaces; 
 * 2) clarify documentation on magSigma branches
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Ned Field
 * @date Sept, 2003
 * @version 1.0
 */


public class FloatingPoissonFaultSource implements Source {

	//name for this classs
	protected String  NAME = "Floating Poisson Fault Source";

	// private fields - TODO shoudl be immutable
	private List<Rupture> ruptureList;

	//	private ArrayList<Location> faultCornerLocations = new ArrayList<Location>();   // used for the getMinDistance(Site) method
	private double duration;
	private AbstractEvenlyGriddedSurfaceWithSubsets faultSurface;

	// used for the getMinDistance(Site) method
	private Region sourceRegion;
	private LocationList sourceTrace;

	private double lastDuration = Double.NaN;

	@Override
	public Location centroid() {
		throw new UnsupportedOperationException("");
	}
	

	/**
	 * This creates the Simple Poisson Fault Source, where a variety floating options are given
	 * by the floatTypeFlag described below. All magnitudes below minMag are given a zero probability,
	 * and all those greater than or equal to fullFaultRupMagThresh are forced to rupture the entire fault.
	 * @param magDist - any incremental mag. freq. dist. object
	 * @param faultSurface - any EvenlyGriddedSurface representation of the fault
	 * @param magScalingRel - any magAreaRelationship or magLengthRelationthip
	 * @param magScalingSigma - uncertainty of the length(mag) or area(mag) relationship
	 * @param rupAspectRatio - ratio of rupture length to rupture width
	 * @param rupOffset - amount of offset for floating ruptures in km
	 * @param rake - average rake of the ruptures
	 * @param duration - the timeSpan of interest in years (this is a Poissonian source)
	 * @param minMag - the minimum magnitude to be considered from magDist (lower mags are ignored)
	 * @param floatTypeFlag - if = 0 full down-dip width ruptures; if = 1 float both along strike and down dip; 
	 *                        if = 2 float only along strike and centered down dip.
	 * @param fullFaultRupMagThresh - magnitudes greater than or equal to this value will be forced to rupture the entire fault
	 */
	public FloatingPoissonFaultSource(IncrementalMFD magDist,
			AbstractEvenlyGriddedSurfaceWithSubsets faultSurface,
			MagScalingRelationship magScalingRel,
			double magScalingSigma,
			double rupAspectRatio,
			double rupOffset,
			double rake,
			double duration,
			double minMag,
			int floatTypeFlag,
			double fullFaultRupMagThresh) {

		this.duration = duration;
		this.faultSurface = faultSurface;

//		if (D) {
//			System.out.println(magDist.name());
//			System.out.println("surface rows, cols: "+faultSurface.getNumCols()+", "+faultSurface.getNumRows());
//			System.out.println("magScalingRelationship: "+magScalingRel.name());
//			System.out.println("magScalingSigma: "+magScalingSigma);
//			System.out.println("rupAspectRatio: "+rupAspectRatio);
//			System.out.println("rupOffset: "+rupOffset);
//			System.out.println("rake: "+rake);
//			System.out.println("timeSpan: "+duration);
//			System.out.println("minMag: "+minMag);
//
//		}
		// make a list of a subset of locations on the fault for use in the getMinDistance(site) method
		mkApproxSourceSurface(faultSurface);

		// make the rupture list
		ruptureList = Lists.newArrayList();
		if(magScalingSigma == 0.0)
			addRupturesToList(magDist, faultSurface, magScalingRel, magScalingSigma, rupAspectRatio, rupOffset, 
					rake, minMag, 0.0, 1.0, floatTypeFlag, fullFaultRupMagThresh);
		else {
			GaussianMFD gDist = new GaussianMFD(-3.0,3.0,25,0.0,1.0,1.0);
			gDist.scaleToCumRate(0, 1.0);  // normalize to make it a probability density
//			if(D) System.out.println("gDist:\n"+gDist.toString());
			for(int m=0; m<gDist.getNum(); m++) {
				addRupturesToList(magDist, faultSurface, magScalingRel, magScalingSigma,
						rupAspectRatio, rupOffset, rake, minMag, gDist.getX(m), gDist.getY(m), 
						floatTypeFlag, fullFaultRupMagThresh);
//				if(D) System.out.println(m+"\t"+gDist.getX(m)+"\t"+gDist.getY(m));
			}
		}

		lastDuration = duration;
	}


	/**
	 * This constructor sets floatTypeFlag=1 and fullFaultRupMagThresh = Double.MAX_VALUE.  Otherwise it's the same.
	 */
	public FloatingPoissonFaultSource(IncrementalMFD magDist,
			AbstractEvenlyGriddedSurfaceWithSubsets faultSurface,
			MagScalingRelationship magScalingRel,
			double magScalingSigma,
			double rupAspectRatio,
			double rupOffset,
			double rake,
			double duration,
			double minMag) {
		this( magDist, faultSurface, magScalingRel,magScalingSigma,rupAspectRatio,rupOffset,rake,duration,minMag, 1,Double.MAX_VALUE);
	}


	/**
	 * This constructor sets minMag=5, floatTypeFlag=1 and 
	 * fullFaultRupMagThresh = Double.MAX_VALUE.  Otherwise it's the same.
	 */
	public FloatingPoissonFaultSource(IncrementalMFD magDist,
			AbstractEvenlyGriddedSurfaceWithSubsets faultSurface,
			MagScalingRelationship magScalingRel,
			double magScalingSigma,
			double rupAspectRatio,
			double rupOffset,
			double rake,
			double duration) {
		this( magDist, faultSurface, magScalingRel,magScalingSigma,rupAspectRatio,rupOffset,rake,duration,5.0);
	}

//	/**
//	 * This allows you to change the duration of the forecast
//	 * @param newDuration
//	 */
//	public void setDuration(double newDuration) {
//		for(int r=0; r<ruptureList.size(); r++) {
//			Rupture rup = ruptureList.get(r);
//			double rate = rup.getMeanAnnualRate(lastDuration);
//			rup.setProbability(1.0 - Math.exp(-duration*rate));
//		}
//		lastDuration = newDuration;
//	}


	/**
	 * This computes the rupture length from the information supplied
	 * @param magScalingRel - a MagLengthRelationship or a MagAreaRelationship
	 * @param magScalingSigma - the standard deviation of the Area or Length estimate
	 * @param numSigma - the number of sigmas from the mean for which the estimate is for
	 * @param rupAspectRatio
	 * @param mag
	 * @return
	 */
	private double getRupLength(MagScalingRelationship magScalingRel,
			double magScalingSigma,
			double numSigma,
			double rupAspectRatio,
			double mag) throws RuntimeException {

		// if it's a mag-area relationship
		if(magScalingRel instanceof MagAreaRelationship) {
			double area = magScalingRel.getMedianScale(mag) * Math.pow(10,numSigma*magScalingSigma);
			return Math.sqrt(area*rupAspectRatio);
		}
		else if (magScalingRel instanceof MagLengthRelationship) {
			return magScalingRel.getMedianScale(mag) * Math.pow(10,numSigma*magScalingSigma);
		}
		else throw new RuntimeException("bad type of MagScalingRelationship");
	}



	/**
	 * This method makes and adds ruptures to the list
	 */
	private void addRupturesToList(IncrementalMFD magDist,
			AbstractEvenlyGriddedSurfaceWithSubsets faultSurface,
			MagScalingRelationship magScalingRel,
			double magScalingSigma,
			double rupAspectRatio,
			double rupOffset,
			double rake,
			double minMag,
			double numSigma,
			double weight,
			int floatTypeFlag,
			double fullFaultRupMagThresh) {


		double rupLen;
		double rupWidth;
		int numRup;
		double mag;
		double rate;
		double prob=Double.NaN;



//		if( D ) System.out.println(C+": magScalingSigma="+magScalingSigma);

		// loop over magnitudes
		int numMags = magDist.getNum();
		for(int i=0;i<numMags;++i){
			mag = magDist.getX(i);
			rate = magDist.getY(i);
			// make sure it has a non-zero rate & the mag is >= minMag
			if(rate > 10E-15 && mag >= minMag) {

				// if floater
				if(mag < fullFaultRupMagThresh) {
					// get down-dip width of fault
					double ddw=faultSurface.width();

					rupLen = getRupLength(magScalingRel,magScalingSigma,numSigma,rupAspectRatio,mag);
					rupWidth= rupLen/rupAspectRatio;

					// if magScalingRel is a MagAreaRelationship, then rescale rupLen if rupWidth
					// exceeds the down-dip width (don't do anything for MagLengthRelationship)
					if(magScalingRel instanceof MagAreaRelationship  && rupWidth > ddw) {
						rupLen *= rupWidth/ddw;
						rupWidth = ddw;
					}

					// check if full down-dip rupture chosen
					if(floatTypeFlag==0)
						rupWidth = 2*ddw;  // factor of 2 more than ensures full ddw ruptures

					//System.out.println((float)mag+"\t"+(float)rupLen+"\t"+(float)rupWidth+"\t"+(float)(rupLen*rupWidth));

					// get number of ruptures depending on whether we're floating down the middle
					if(floatTypeFlag != 2)
						numRup = faultSurface.getNumSubsetSurfaces(rupLen,rupWidth,rupOffset);
					else
						numRup = faultSurface.getNumSubsetSurfacesAlongLength(rupLen, rupOffset);

					for(int r=0; r < numRup; ++r) {
						RuptureSurface surface = (floatTypeFlag != 2) ?
							faultSurface.getNthSubsetSurface(rupLen,rupWidth,rupOffset,r) :
							faultSurface.getNthSubsetSurfaceCenteredDownDip(rupLen,rupWidth,rupOffset,r);
						double rupRate = weight*rate/numRup;
						Rupture probEqkRupture = Rupture.create(mag, rake, rupRate, surface);
						
//						probEqkRupture.setAveRake(rake);
//						if(floatTypeFlag != 2)
//							probEqkRupture.setRuptureSurface(faultSurface.getNthSubsetSurface(rupLen,rupWidth,rupOffset,r));
//						else
//							probEqkRupture.setRuptureSurface(faultSurface.getNthSubsetSurfaceCenteredDownDip(rupLen,rupWidth,rupOffset,r));
//						probEqkRupture.setMag(mag);
//						prob = (1.0 - Math.exp(-duration*weight*rate/numRup));
//						probEqkRupture.setProbability(prob);
						ruptureList.add(probEqkRupture);
					}
					/*    			if( D ) System.out.println(C+": ddw="+ddw+": mag="+mag+"; rupLen="+rupLen+"; rupWidth="+rupWidth+
    					"; rate="+rate+"; timeSpan="+duration+"; numRup="+numRup+
    					"; weight="+weight+"; prob="+prob+"; floatTypeFlag="+floatTypeFlag);
					 */

				}
				// Apply full fault rupture
				else {
					double rupRate = weight*rate;
					Rupture probEqkRupture = Rupture.create(mag, rupRate, rake, faultSurface);

//					probEqkRupture.setAveRake(rake);
//					probEqkRupture.setRuptureSurface(faultSurface);
//					probEqkRupture.setMag(mag);
//					prob = (1.0 - Math.exp(-duration*weight*rate));
//					probEqkRupture.setProbability(prob);

					ruptureList.add(probEqkRupture);
				}
			}
		}
	}

	@Override
	public AbstractEvenlyGriddedSurface surface() { return faultSurface; }

	/**
	 * @return the total num of rutures for all magnitudes
	 */
	public int getNumRuptures() { return ruptureList.size(); }


	/**
	 * This method returns the nth Rupture in the list
	 */
	public Rupture getRupture(int nthRupture){ return ruptureList.get(nthRupture); }

	public List<Rupture> getRuptureList() {
		// TODO may want to throw unsupported operation ex
		return ruptureList;
	}
	
	
	
	/**
	 * This returns the shortest dist to the fault surface approximated as a region according
	 * to the corners and mid-points along strike (both on top and bottom trace).
	 * @param site
	 * @return minimum distance in km
	 */
	public  double getMinDistance(Location loc) {
		if (sourceRegion != null) {
			return sourceRegion.distanceToLocation(loc);
		} 
		return sourceTrace.minDistToLocation(loc);

	}


	/**
	 * This creates an approximation of the source surface, taking the end points and mid point along
	 * strike (both on top and bottom trace).  If region creation fails (e.g. due to vertical dip) 
	 * a sourceTrace is created instead.
	 * 
	 * @param faultSurface
	 */
	private void mkApproxSourceSurface(AbstractEvenlyGriddedSurfaceWithSubsets faultSurface) {

		if(faultSurface.dip() != 90) {
			int nRows = faultSurface.getNumRows();
			int nCols = faultSurface.getNumCols();
			LocationList faultCornerLocations = LocationList.create(
				faultSurface.getLocation(0,0),
				faultSurface.getLocation(0,(int)(nCols/2)),
				faultSurface.getLocation(0,nCols-1),
				faultSurface.getLocation(nRows-1,nCols-1),
				faultSurface.getLocation(nRows-1,(int)(nCols/2)),
				faultSurface.getLocation(nRows-1,0));
			try {
				sourceRegion = Regions.create(
					"Approx surface of " + name(), 
					faultCornerLocations, 
					BorderType.GREAT_CIRCLE);
			} catch (IllegalArgumentException iae) {
			}
		}
		else {
			Iterator it = faultSurface.getColumnIterator(0);
			List<Location> tmpLocs = Lists.newArrayList();
			while (it.hasNext()) {
				tmpLocs.add((Location) it.next());
			}
			sourceTrace = LocationList.create(tmpLocs);
		}
	}

	/**
	 * get the name of this class
	 *
	 * @return
	 */
	public String name() {
		return NAME;
	}


	@Override
	public Iterator<Rupture> iterator() {
		return ruptureList.iterator();
	}


	@Override
	public int size() {
		return ruptureList.size();
	}
}
