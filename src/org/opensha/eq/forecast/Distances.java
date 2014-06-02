package org.opensha.eq.forecast;

import static org.opensha.geo.Locations.*;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.BorderType;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.LocationVector;
import org.opensha.geo.Locations;
import org.opensha.geo.Region;
import org.opensha.geo.Regions;

import com.google.common.collect.Lists;


/**
 * Add comments here
 * TODO can this be package private; it should be if r fields are exposed.
 * 
 * @author Peter Powers
 */
public final class Distances {

	public final int index;
	public final double rJB, rRup, rX;
	
	private Distances(int index, double rJB, double rRup, double rX) {
		this.index = index;
		this.rJB = rJB;
		this.rRup = rRup;
		this.rX = rX;
	}

	public static Distances create(int index, double rJB, double rRup, double rX) {
		return new Distances(index, rJB, rRup, rX);
	}
	
	public static Distances create(double rJB, double rRup, double rX) {
		return new Distances(-1, rJB, rRup, rX);
	}
	
	
	
	/**
	 * This computes distRup, distJB, & distSeis, which are available in the returned
	 * array in elements 0, 1, and 2 respectively.
	 * @param surface
	 * @param loc
	 * @return
	 */
	public static Distances compute(GriddedSurface surface, Location loc) {
		
		Location loc1 = loc;
		Location loc2;
		double distJB = Double.MAX_VALUE;
//		double distSeis = Double.MAX_VALUE;
		double distRup = Double.MAX_VALUE;
		
		double horzDist, vertDist, rupDist;

		// flag to project to seisDepth if only one row and depth is below seisDepth
//		boolean projectToDepth = false;
//		if (surface.getNumRows() == 1 && surface.getLocation(0,0).depth() < SEIS_DEPTH)
//			projectToDepth = true;

		// get locations to iterate over depending on dip
		Iterator<Location> it;
		if(surface.dip() > 89) {
			it = surface.getColumnIterator(0);
//			if (surface.getLocation(0,0).depth() < SEIS_DEPTH)
//				projectToDepth = true;
		}
		else {
			it = surface.iterator();
		}

		while( it.hasNext() ){

			loc2 = it.next();

			// get the vertical distance
			vertDist = Locations.vertDistance(loc1, loc2);

			// get the horizontal dist depending on desired accuracy
			horzDist = Locations.horzDistanceFast(loc1, loc2);

			if(horzDist < distJB) distJB = horzDist;

			rupDist = horzDist * horzDist + vertDist * vertDist;
			if(rupDist < distRup) distRup = rupDist;

//			if (loc2.depth() >= SEIS_DEPTH) {
//				if (rupDist < distSeis)
//					distSeis = rupDist;
//			}
//			// take care of shallow line or point source case
//			else if(projectToDepth) {
//				rupDist = horzDist * horzDist + SEIS_DEPTH * SEIS_DEPTH;
//				if (rupDist < distSeis)
//					distSeis = rupDist;
//			}
		}

		distRup = Math.pow(distRup,0.5);
//		distSeis = Math.pow(distSeis,0.5);

//		if(D) {
//			System.out.println(C+": distRup = " + distRup);
//			System.out.println(C+": distSeis = " + distSeis);
//			System.out.println(C+": distJB = " + distJB);
//		}
		
		// Check whether small values of distJB should really be zero
		if(distJB <surface.getAveGridSpacing()) { // check this first since the next steps could take time
			
			// first identify whether it's a frankel type surface
//			boolean frankelTypeSurface=false;
//			if(surface instanceof FrankelGriddedSurface) {
//				frankelTypeSurface = true;
//			}
//			else if(surface instanceof GriddedSubsetSurface) {
//				if(((GriddedSubsetSurface)surface).getParentSurface() instanceof FrankelGriddedSurface) {
//					frankelTypeSurface = true;
//				}
//			}
					
//			if (frankelTypeSurface) {
//				if (isDjbZeroFrankel(surface, distJB)) distJB = 0;
//			} else {
				if (isDjbZero(surface.getPerimeter(), loc)) distJB = 0;
//			}
		}
		
		if(distJB <surface.getAveGridSpacing() && isDjbZero(surface.getPerimeter(), loc)) distJB = 0;

//		double[] results = {distRup, distJB, distSeis};
		
//		return results;
		
		double rX = getDistanceX(surface.getEvenlyDiscritizedUpperEdge(), loc);
		
		return Distances.create(distJB, distRup, rX);

	}
	
	/**
	 * This computes distanceX
	 * 
	 * TODO I cannot believe there is not a cleaner implementation
	 * @param surface
	 * @param siteLoc
	 * @return
	 */
	private static double getDistanceX(LocationList trace, Location siteLoc) {

		double distanceX;
		
		// set to zero if it's a point source
		if(trace.size() == 1) {
			distanceX = 0;
		}
		else {
			// We should probably set something here here too if it's vertical strike-slip
			// (to avoid unnecessary calculations)

				// get points projected off the ends
				Location firstTraceLoc = trace.first(); 	// first trace point
				Location lastTraceLoc = trace.last(); 	// last trace point

				// get point projected from first trace point in opposite direction of the ave trace
				LocationVector dirBase = LocationVector.create(lastTraceLoc, firstTraceLoc); 		
//				dir.setHorzDistance(1000); // project to 1000 km
//				dir.setVertDistance(0d);
				LocationVector dirUtil = LocationVector.create(dirBase.azimuth(), 1000.0, 0.0);
				Location projectedLoc1 = Locations.location(firstTraceLoc, dirUtil);


				// get point projected from last trace point in ave trace direction
				dirUtil = LocationVector.reverseOf(dirUtil);
//				dir.setAzimuth(dir.getAzimuth()+180);  // flip to ave trace dir
				Location projectedLoc2 = Locations.location(lastTraceLoc, dirUtil);
				// point down dip by adding 90 degrees to the azimuth
				double rot90 = (dirUtil.azimuthDegrees() + 90.0) * GeoTools.TO_RAD;
				dirUtil = LocationVector.create(rot90, dirUtil.horizontal(), 0.0);
//				dir.setAzimuth(dir.getAzimuth()+90);  // now point down dip

				// get points projected in the down dip directions at the ends of the new trace
				Location projectedLoc3 = Locations.location(projectedLoc1, dirUtil);

				Location projectedLoc4 = Locations.location(projectedLoc2, dirUtil);

//				LocationList locsForExtendedTrace = new LocationList();
				List<Location> locsForExtendedTrace = Lists.newArrayList();
//				LocationList locsForRegion = new LocationList();
				List<Location> locsForRegion = Lists.newArrayList();

				locsForExtendedTrace.add(projectedLoc1);
				locsForRegion.add(projectedLoc1);
				for(int c=0; c<trace.size(); c++) {
					locsForExtendedTrace.add(trace.get(c));
					locsForRegion.add(trace.get(c));     	
				}
				locsForExtendedTrace.add(projectedLoc2);
				locsForRegion.add(projectedLoc2);

				// finish the region
				locsForRegion.add(projectedLoc4);
				locsForRegion.add(projectedLoc3);

//				// write these out if in debug mode
//				if(D) {
//					System.out.println("Projected Trace:");
//					for(int l=0; l<locsForExtendedTrace.size(); l++) {
//						Location loc = locsForExtendedTrace.get(l);
//						System.out.println(loc.lat()+"\t"+ loc.lon()+"\t"+ loc.depth());
//					}
//					System.out.println("Region:");
//					for(int l=0; l<locsForRegion.size(); l++) {
//						Location loc = locsForRegion.get(l);
//						System.out.println(loc.lat()+"\t"+ loc.lon()+"\t"+ loc.depth());
//					}
//				}

				Region polygon=null;
				try {
					polygon = Regions.create("", 
						LocationList.create(locsForRegion), 
						BorderType.MERCATOR_LINEAR);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("==== trace  ====");
					System.out.println(trace);
//					RegionUtils.locListToKML(trace, "distX_trace", Color.ORANGE);
					System.out.println("==== region ====");
					System.out.println(locsForRegion);
//					RegionUtils.locListToKML(locsForRegion, "distX_region", Color.RED);
					System.exit(0);
				}
				boolean isInside = polygon.contains(siteLoc);

				double distToExtendedTrace = LocationList.create(locsForExtendedTrace).minDistToLine(siteLoc);

				if(isInside || distToExtendedTrace == 0.0) // zero values are always on the hanging wall
					distanceX = distToExtendedTrace;
				else 
					distanceX = -distToExtendedTrace;
		}
		
		return distanceX;
	}

	private static double calcDistanceX(LocationList trace, Location loc) {
		if (trace.size() == 1) return 0.0;

		// Compare the distance to the closest segment to the distances to the
		// endpoints. If the closest segment distance is less than both endpoint
		// distances, use that segment as a line to compute rX, otherwise use
		// endpoints of the trace as a line to compute rX
		int minIdx = trace.minDistIndex(loc);
		double rSeg = distanceToSegmentFast(trace.get(minIdx),
			trace.get(minIdx + 1), loc);
		double rFirst = horzDistanceFast(trace.first(), loc);
		double rLast = horzDistanceFast(trace.last(), loc);

		return (rSeg < Math.min(rFirst, rLast)) ? distanceToLineFast(
			trace.get(minIdx), trace.get(minIdx + 1), loc)
			: distanceToLineFast(trace.first(), trace.last(), loc);
	}
	
	/*
	 * This method is used to check small distJB values for continuous, smooth
	 * surfaces; e.g. non-Frankel type surfaces. This was implemented to replace
	 * using a Region.contains() which can fail when dipping faults have
	 * jagged traces. This method borrows from Region using a java.awt.geom.Area
	 * to perform a contains test, however no checking is done of the area's
	 * singularity.
	 * 
	 * The Elsinore fault was the culprit leading to this implementation. For
	 * a near-vertical (85??) strike-slip fault, it is has an unrealistic ???90 jog
	 * in it. Even this method does not technically give a 100% correct answer.
	 * Drawing out a steeply dipping fault with a jog will show that the
	 * resultant perimeter polygon has eliminated small areas for which distJB
	 * should be zero. The areas are so small though that the hazard is not
	 * likely affected.
	 */
	private static boolean isDjbZero(LocationList border, Location pt) {
		Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD, border.size());
		boolean starting = true;
		for (Location loc : border) {
			double lat = loc.lat();
			double lon = loc.lon();
			// if just starting, then moveTo
			if (starting) {
				path.moveTo(lon, lat);
				starting = false;
				continue;
			}
			path.lineTo(lon, lat);
		}
		path.closePath();
		Area area = new Area(path);
		return area.contains(pt.lon(), pt.lat());
	}


}
