package org.opensha.eq.fault.surface;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.opensha.eq.fault.Faults;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;

/**
 * <p>Title:  EvenlyGriddedSurfFromSimpleFaultData </p>
 *
 * <p>Description: This creates and EvenlyGriddedSurface from SimpleFaultData</p>
 *
 * @author Nitin Gupta
 */
public abstract class EvenlyGriddedSurfFromSimpleFaultData
extends AbstractEvenlyGriddedSurfaceWithSubsets{

	protected LocationList faultTrace;
	protected double upperSeismogenicDepth = Double.NaN;
	protected double lowerSeismogenicDepth = Double.NaN;
	protected double aveDip;
	
	/**
	 * No are constructor needed by subclasses
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData() {}

	/**
	 * This applies the grid spacing exactly as given, both along strike and down dip, clipping
	 * any remainder
	 * @param simpleFaultData
	 * @param gridSpacing
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(SimpleFaultData simpleFaultData, double gridSpacing) {

		this(simpleFaultData.getFaultTrace(), simpleFaultData.getAveDip(),
				simpleFaultData.getUpperSeismogenicDepth(),
				simpleFaultData.getLowerSeismogenicDepth(), gridSpacing);

	}

	/**
	 * This applies the grid spacing exactly as given, both along strike and down dip, clipping
	 * any remainder
	 * @param faultTrace
	 * @param aveDip
	 * @param upperSeismogenicDepth
	 * @param lowerSeismogenicDepth
	 * @param gridSpacing
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(LocationList faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacing) {
		set(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing, gridSpacing);
	}
		
	/**
	 * This constructor will adjust the grid spacings along strike and down dip to exactly fill the surface
	 * (not cut off ends), leaving the grid spacings just less then the originals.
	 * @param simpleFaultData
	 * @param maxGridSpacingAlong - maximum grid spacing along strike
	 * @param maxGridSpacingDown - maximum grid spacing down dip
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(SimpleFaultData simpleFaultData,
			double maxGridSpacingAlong, double maxGridSpacingDown) {

		this(simpleFaultData.getFaultTrace(), simpleFaultData.getAveDip(),
				simpleFaultData.getUpperSeismogenicDepth(), simpleFaultData.getLowerSeismogenicDepth(),
				maxGridSpacingAlong, maxGridSpacingDown);

	}

	/**
	 * This constructor will adjust the grid spacings along strike and down dip to exactly fill the surface
	 * (not cut off ends), leaving the grid spacings just less then the originals.
	 * @param faultTrace
	 * @param aveDip
	 * @param upperSeismogenicDepth
	 * @param lowerSeismogenicDepth
	 * @param maxGridSpacingAlong - maximum grid spacing along strike
	 * @param maxGridSpacingDown - maximum grid spacing down dip
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(LocationList faultTrace, double aveDip,
			double upperSeismogenicDepth, double lowerSeismogenicDepth, double maxGridSpacingAlong,
			double maxGridSpacingDown) {
		
		double length = faultTrace.length();
		double gridSpacingAlong = length/Math.ceil(length/maxGridSpacingAlong);
		double downDipWidth = (lowerSeismogenicDepth-upperSeismogenicDepth)/Math.sin(aveDip*Math.PI/180 );
		double gridSpacingDown = downDipWidth/Math.ceil(downDipWidth/maxGridSpacingDown);
/*		
		System.out.println(faultTrace.getName()+"\n\t"+
				maxGridSpacingAlong+"\t"+(float)gridSpacingAlong+"\t"+(float)gridSpacingDown+"\t"+
				(float)(faultTrace.getTraceLength()/gridSpacingAlong)+"\t"+
				(float)(downDipWidth/gridSpacingDown));
*/				

		set(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacingAlong, gridSpacingDown);
	}


	protected void set(LocationList faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacingAlong, double gridSpacingDown)	{
		this.faultTrace =faultTrace;
		this.aveDip =aveDip;
		this.upperSeismogenicDepth = upperSeismogenicDepth;
		this.lowerSeismogenicDepth =lowerSeismogenicDepth;
		this.gridSpacingAlong = gridSpacingAlong;
		this.gridSpacingDown = gridSpacingDown;
		this.sameGridSpacing = true;
		if(gridSpacingDown != gridSpacingAlong) sameGridSpacing = false;
	}


	// ***************************************************************
	/** @todo  Serializing Helpers - overide to increase performance */
	// ***************************************************************


	public LocationList getFaultTrace() { return faultTrace; }

	public double getUpperSeismogenicDepth() { return upperSeismogenicDepth; }

	public double getLowerSeismogenicDepth() { return lowerSeismogenicDepth; }


	/**
	 * This method checks the simple-fault data to make sure it's all OK.
	 * @throws FaultException
	 */
	protected void assertValidData() {

		checkNotNull(faultTrace, "Fault Trace is null");
//		if( faultTrace == null ) throw new FaultException(C + "Fault Trace is null");

		Faults.validateDip(aveDip);
		Faults.validateDepth(lowerSeismogenicDepth);
		Faults.validateDepth(upperSeismogenicDepth);
		checkArgument(upperSeismogenicDepth < lowerSeismogenicDepth);
		
		checkArgument(!Double.isNaN(gridSpacingAlong), "invalid gridSpacing");
//		if( gridSpacingAlong == Double.NaN ) throw new FaultException(C + "invalid gridSpacing");

		double depth = faultTrace.first().depth();
		checkArgument(depth <= upperSeismogenicDepth,
			"depth on faultTrace locations %s must be <= upperSeisDepth %s",
			depth, upperSeismogenicDepth);
		//		if(depth > upperSeismogenicDepth)
//			throw new FaultException(C + "depth on faultTrace locations must be < upperSeisDepth");

//		Iterator<Location> it = faultTrace.iterator();
//		while(it.hasNext()) {
//			if(it.next().getDepth() != depth){
//				throw new FaultException(C + ":All depth on faultTrace locations must be equal");
//			}
//		}
		for (Location loc : faultTrace) {
			if (loc.depth() != depth) {
				checkArgument(loc.depth() == depth, "All depth on faultTrace locations must be equal");
//				throw new FaultException(C + ":All depth on faultTrace locations must be equal");
			}
		}
	}
	
	@Override
	public double dip() {
		return aveDip;
	}

	@Override
	public double depth() {
		return upperSeismogenicDepth;
	}

	@Override
	public double strike() {
		return Faults.strike(faultTrace);
	}

	@Override
	public LocationList getUpperEdge() {
		// check that the location depths in faultTrace are same as
		// upperSeismogenicDepth
		double aveTraceDepth = 0;
		for (Location loc : faultTrace)
			aveTraceDepth += loc.depth();
		aveTraceDepth /= faultTrace.size();
		double diff = Math.abs(aveTraceDepth - upperSeismogenicDepth); // km
		if (diff < 0.001) return faultTrace;
		throw new RuntimeException(
			" method not yet implemented where depths in the " +
				"trace differ from upperSeismogenicDepth (and projecting will create " +
				"loops for FrankelGriddedSurface projections; aveTraceDepth=" +
				aveTraceDepth + "\tupperSeismogenicDepth=" +
				upperSeismogenicDepth);
	}

}
