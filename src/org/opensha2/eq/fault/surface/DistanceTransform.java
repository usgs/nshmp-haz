package org.opensha.eq.fault.surface;

import static org.opensha.geo.GeoTools.*;
import static org.apache.commons.math3.geometry.euclidean.threed.RotationOrder.*;
import static java.lang.Math.*;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.eq.fault.Faults;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Locations;
import org.opensha.geo.LocationVector;

/**
 * Wrapper class to simplify calculating the distance to a fault trace. Class
 * treats calculating the shortest distance as 2D problem. The parallelograms
 * representing each fault segment are rotated into the xy plane of a local
 * cartesian coordinate system. Precalculating and storing the 2D parallelograms
 * and the required rotation matrices drastically reduces the time required to
 * calculate the minimum distance to a fault surface.
 * 
 * <p>Internally, this class uses a right-handed cartesian coordinate system where
 * x is latitude, y is longitude, and z is depth (positive down per seismological
 * convention). This convention preserves strike values (degrees clockwise from
 * north) as clockwise rotation about the z-axis per cartesian convention.</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class DistanceTransform {

	private LocationList trace;
	private List<Rotation> rots;
	private List<Path2D> rupPaths;
	private List<Path2D> jbPaths;

	DistanceTransform(LocationList trace, double dip, double width) {
		this.trace = trace;
		rots = new ArrayList<Rotation>();
		rupPaths = new ArrayList<Path2D>();
		jbPaths = new ArrayList<Path2D>();
		initSegments2(dip, width);
	}

	private void initSegments2(double dip, double width) {
		for (int i = 0; i < trace.size() - 1; i++) {

			Location p1 = trace.get(i);
			Location p2 = trace.get(i + 1);

			double strikeRad =  Faults.strikeRad(p1, p2);
			double length = Locations.horzDistance(p1, p2);
			// average values of parent surface
			double dipDirRad = Faults.dipDirectionRad(trace);
			double dipRad = dip * TO_RAD;

			Rotation rot = buildRot(strikeRad, dipRad, dipDirRad, width);
			rots.add(rot);
			Vector3D[] rupVecs = buildVectorsRup(strikeRad, dipRad, dipDirRad,
				width, length);
			Vector3D[] jbVecs = toVectorsJB(rupVecs);			
			
			rupPaths.add(buildPath(rupVecs, rot));
			jbPaths.add(buildPath(jbVecs));
		}
	}
	
	/*
	 * Build rotation required to transform Location to cartesian coordinate
	 * system of rupture surface (parallelogram)
	 */
	private static Rotation buildRot(double strikeRad, double dipRad,
			double dipDirRad, double width) {
		// vector from origin to corner of surface immediately down-dip
		Vector3D vb1 = new Vector3D(width, new Vector3D(dipDirRad, dipRad));
		// rotate vb1 -strike about the z-axis and flatten onto xy plane [0,y,z]
		Rotation dRot = new Rotation(Vector3D.PLUS_K, -strikeRad);
		Vector3D dVec = dRot.applyTo(vb1);
		dVec = new Vector3D(0, dVec.getY(), dVec.getZ());
		// true dip of parallelogram
		double surfDip = dVec.getDelta();
		// assemble full rotation
		return new Rotation(XYZ, -surfDip, 0, -strikeRad);
	}
		
	/*
	 * Returns [vTop1 (origin), vTop2, vBottom1, vBottom2]. These define the 4
	 * corners of a rupture surface parallelogram to be used for rRup
	 * calculations.
	 */
	private static Vector3D[] buildVectorsRup(double strikeRad, double dipRad,
			double dipDirRad, double width, double length) {
		Vector3D[] vv = new Vector3D[4];
		vv[0] = Vector3D.ZERO;
		vv[1] = new Vector3D(length, new Vector3D(strikeRad, 0));
		vv[2] = new Vector3D(width, new Vector3D(dipDirRad, dipRad));
		vv[3] = new Vector3D(1, vv[1], 1, vv[2]);
		return vv;
	}
	
	/*
	 * Flattens the z-values in a copy of the supplied Vector3D[]. These define
	 * the surface projection of a rupture surface parallelogram to be used for
	 * rJB calculations.
	 */
	private static Vector3D[] toVectorsJB(Vector3D[] vIn) {
		Vector3D[] vOut = new Vector3D[4];
		vOut[0] = Vector3D.ZERO;
		vOut[1] = new Vector3D(vIn[1].getX(), vIn[1].getY(), 0.0);
		vOut[2] = new Vector3D(vIn[2].getX(), vIn[2].getY(), 0.0);
		vOut[3] = new Vector3D(vIn[3].getX(), vIn[3].getY(), 0.0);
		return vOut;
	}

	/* 
	 * Builds a Path2D from a Vector3D[4], after first rotating the supplied 
	 * vectors to the xy-plane and aligning the upper trace to the x-axis. The
	 * contents of vv are modified in the process. 
	 */
	private static Path2D buildPath(Vector3D[] vv, Rotation rot) {
		vv[1] = rot.applyTo(vv[1]);
		vv[2] = rot.applyTo(vv[2]);
		vv[3] = rot.applyTo(vv[3]);
		return buildPath(vv);
	}
	
	/* Builds a Path2D from a Vector3D[4]. */
	private static Path2D buildPath(Vector3D[] vv) {
		Path2D path = new Path2D.Double();
		path.moveTo(vv[0].getX(), vv[0].getY());
		path.lineTo(vv[1].getX(), vv[1].getY());
		path.lineTo(vv[3].getX(), vv[3].getY());
		path.lineTo(vv[2].getX(), vv[2].getY());
		path.lineTo(vv[0].getX(), vv[0].getY());
		return path;
		
	}

	
//	private static Vector3d[] initVectorsRup(Location p1, Location p2, double dipRad, double dipDirRad, double width) {
//		LocationVector vec = Locations.vector(p1, p2);
//		double strikeRad =  vec.getAzimuthRad();
//		double surfDip; // true dip of parallelogram
//		double p1p2Dist = vec.getHorzDistance();
//
//		
//		// top trace #1 is at [0,0]
//		Vector3D vt1 = Vector3D.ZERO;
//		// top trace #2
//		Vector3D vt2 = new Vector3D(p1p2Dist, new Vector3D(strikeRad, 0));
//		// bottom trace #1
//		Vector3D vb1 = new Vector3D(width, new Vector3D(dipDirRad, dipRad));
//		// bottom trace #2
//		Vector3D vb2 = new Vector3D(1, vt2, 1, vb1);
//
//		// true dip of surface - rotate vb1 the strike angle about
//		// the z-axis, and flatten onto xy plane [0,y,z]
//		Rotation dRot = new Rotation(Vector3D.PLUS_K, -strikeRad);
//		Vector3D dVec = dRot.applyTo(vb1);
//		dVec = new Vector3D(0, dVec.getY(), dVec.getZ());
//		surfDip = dVec.getDelta();
//
//		// set rotation
//		Rotation rot = new Rotation(XYZ, -surfDip, 0, -strikeRad);
//		rots.add(rot);
//
//		// rotate parallelogram
//		vt2 = rot.applyTo(vt2);
//		vb1 = rot.applyTo(vb1);
//		vb2 = rot.applyTo(vb2);
//
//		// set up for 2D ops in yz plane
//		Path2D surface = new Path2D.Double();
//		surface.moveTo(vt1.getX(), vt1.getY());
//		surface.lineTo(vt2.getX(), vt2.getY());
//		surface.lineTo(vb2.getX(), vb2.getY());
//		surface.lineTo(vb1.getX(), vb1.getY());
//		surface.lineTo(vt1.getX(), vt1.getY());
//		rupPaths.add(surface);
//
////		System.out.println(vt1);
////		System.out.println(vt2);
////		System.out.println(vb1);
////		System.out.println(vb2);
////		System.out.println("=== " + i + " ===");
//	}

	@Deprecated
	private void initSegments(double dip, double width) {
		for (int i = 0; i < trace.size() - 1; i++) {

			Location p1 = trace.get(i);
			Location p2 = trace.get(i + 1);
			LocationVector vec = LocationVector.create(p1, p2);

			double strikeRad =  vec.azimuth();
			double dipRad = dip * TO_RAD;									// avg dip of parent fault
			double dipDirRad = (Faults.strike(trace) * TO_RAD) + PI_BY_2;	// avg dip dir of parent fault
			double surfDip; // true dip of parallelogram
			double p1p2Dist = vec.horizontal();
			
//			System.out.println("strikeRad " + strikeRad);
//			System.out.println("dipRad " + dipRad);
//			System.out.println("dipDirRad " + dipDirRad);
//			System.out.println("length " + p1p2Dist);

			// top trace #1 is at [0,0]
			Vector3D vt1 = Vector3D.ZERO;

			// top trace #2
			Vector3D vt2 = new Vector3D(p1p2Dist, new Vector3D(strikeRad, 0));

			// bottom trace #1
			Vector3D vb1 = new Vector3D(width, new Vector3D(dipDirRad, dipRad));

			// bottom trace #2
			Vector3D vb2 = new Vector3D(1, vt2, 1, vb1);

			// true dip of surface - rotate vb1 the strike angle about
			// the z-axis, and flatten onto xy plane [0,y,z]
			Rotation dRot = new Rotation(Vector3D.PLUS_K, -strikeRad);
			Vector3D dVec = dRot.applyTo(vb1);
			dVec = new Vector3D(0, dVec.getY(), dVec.getZ());
			surfDip = dVec.getDelta();

//			System.out.println("surfDip " + surfDip);

			// set rotation
			Rotation rot = new Rotation(XYZ, -surfDip, 0, -strikeRad);
			rots.add(rot);

			// rotate parallelogram
			vt2 = rot.applyTo(vt2);
			vb1 = rot.applyTo(vb1);
			vb2 = rot.applyTo(vb2);

			// set up for 2D ops in yz plane
			Path2D surface = new Path2D.Double();
			surface.moveTo(vt1.getX(), vt1.getY());
			surface.lineTo(vt2.getX(), vt2.getY());
			surface.lineTo(vb2.getX(), vb2.getY());
			surface.lineTo(vb1.getX(), vb1.getY());
			surface.lineTo(vt1.getX(), vt1.getY());
			rupPaths.add(surface);

//			System.out.println(vt1);
//			System.out.println(vt2);
//			System.out.println(vb1);
//			System.out.println(vb2);
//			System.out.println("=== " + i + " ===");
		}
	}

	/**
	 * Returns the rRup distance to the surface.
	 * @param loc of interest
	 * @return the rRup distance
	 */
	public double distanceRup(Location loc) {
		double distance = Double.MAX_VALUE;
		for (int i = 0; i < trace.size() - 1; i++) {
			// compute geographic vector to point from segment origin
			LocationVector vec = LocationVector.create(trace.get(i), loc);
			// convert to segment cartesian
			Vector3D vp = new Vector3D(vec.horizontal(), new Vector3D(
				vec.azimuth(), 0), vec.vertical(), Vector3D.PLUS_K);
			// rotate
			vp = rots.get(i).applyTo(vp);
			// compute distance
			Path2D path = rupPaths.get(i);
			distance = min(distance, path.contains(vp.getX(), vp.getY())
				? abs(vp.getZ()) : distanceToSurface(vp, path));
		}
		return distance;
	}
	
	/**
	 * Returns the rJB distance to the surface.
	 * @param loc of interest
	 * @return the rJB distance
	 */
	public double distanceJB(Location loc) {
		double distance = Double.MAX_VALUE;
		for (int i = 0; i < trace.size() - 1; i++) {
			// compute geographic vector to point from segment origin
			LocationVector vec = LocationVector.create(trace.get(i), loc);
			// convert to segment cartesian
			Vector3D vp = new Vector3D(vec.horizontal(), new Vector3D(
				vec.azimuth(), 0));
			// compute distance
			Path2D path = jbPaths.get(i);
			distance = min(distance, path.contains(vp.getX(), vp.getY())
				? abs(vp.getZ()) : distanceToSurface(vp, path));
		}
		return distance;
	}

	/*
	 * Iterates over surface outline path calculating distance to line segments
	 * and returning the minimum.
	 */
	private static double distanceToSurface(Vector3D p, Path2D border) {
		PathIterator pit = border.getPathIterator(null);
		double[] c = new double[6]; // coordinate receiver array
		double xStart = 0.0;
		double yStart = 0.0;
		double minDistSq = Double.MAX_VALUE;
		pit.next(); // skip initial PathIterator.SEG_MOVETO
		while (!pit.isDone()) {
			pit.currentSegment(c);
			double distSq = Line2D.ptSegDistSq(xStart, yStart, c[0], c[1], p.getX(), p.getY());
			minDistSq = Math.min(minDistSq, distSq);
			xStart = c[0];
			yStart = c[1];
			pit.next();
		}
		return Math.sqrt(p.getZ() * p.getZ() + minDistSq);
	}
	
	
	public static void main(String[] args) {
		double depth = 0;
		LocationList.Builder builder = LocationList.builder();
		builder.add(34.0, -118.0, depth);
		builder.add(34.1, -117.9, depth);
		builder.add(34.3, -117.8, depth);
		builder.add(34.4, -117.7, depth);
		builder.add(34.5, -117.5, depth);
		LocationList ft = builder.build();

		// double stk = 35;
		double dip = 50;
		double wid = 15;
		DistanceTransform dt = new DistanceTransform(ft, dip, wid);

		Location p = Location.create(34.0, -117.9);
		System.out.println(dt.distanceRup(p));
		System.out.println(dt.distanceJB(p));
		
		p = Location.create(34.2, -117.8);
		System.out.println(dt.distanceRup(p));
		System.out.println(dt.distanceJB(p));

		p = Location.create(34.3, -117.7);
		System.out.println(dt.distanceRup(p));
		System.out.println(dt.distanceJB(p));

		p = Location.create(34.4, -117.6);
		System.out.println(dt.distanceRup(p));
		System.out.println(dt.distanceJB(p));

		p = Location.create(34.0, -117.6);
		System.out.println(dt.distanceRup(p));
		System.out.println(dt.distanceJB(p));
	}

}
