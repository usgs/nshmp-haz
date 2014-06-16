package org.opensha.calc.tasks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.calc.GroundMotionCalcResult;
import org.opensha.calc.ScalarGroundMotion;
import org.opensha.calc.Site;
import org.opensha.eq.fault.surface.IndexedFaultSurface;
import org.opensha.eq.forecast.DistanceType;
import org.opensha.eq.forecast.Distances;
import org.opensha.eq.forecast.FaultSource;
import org.opensha.eq.forecast.IndexedFaultSource;
import org.opensha.eq.forecast.Source;
import org.opensha.geo.Location;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;

import com.google.common.base.Supplier;
import com.google.common.collect.Table;

/**
 * Factory class for creating {@code Callable} data transforms.
 * 
 * @author Peter Powers
 * @see TransformSupplier
 * @see Transform
 */
public final class Transforms {

	/**
	 * Returns a supplier of {@link Source} to {@link GmmInput} transforms.
	 * @param site of interest
	 * @return a {@code List<GmmInput>} of Gmm inputs
	 * @see Gmm
	 */
	public static TransformSupplier<Source, List<GmmInput>> sourceInitializerSupplier(
			final Site site) {
		return new TransformSupplier<Source, List<GmmInput>>() {
			@Override public Transform<Source, List<GmmInput>> get(Source source) {
				return new SourceInitializer(source, site);
			}
		};
	}
	
//	/**
//	 * Returns a supplier of {@link Source} to {@link GmmInput} transforms.
//	 * @param site of interest
//	 * @return a {@code List<GmmInput>} of Gmm inputs
//	 * @see Gmm
//	 */
//	public static TransformSupplier<Source, List<GmmInput>> sourceInitializerSupplier(
//			final Site site) {
//		return new TransformSupplier<Source, List<GmmInput>>() {
//			@Override public Transform<Source, List<GmmInput>> get(Source source) {
//				return new SourceInitializer(source, site);
//			}
//		};
//	}

	/**
	 * Creates a {@code Callable} from a {@code FaultSource} and {@code Site}
	 * that returns a {@code List<GmmInput>} of inputs for a ground motion
	 * model (Gmm) calculation.
	 * @param source
	 * @param site
	 * @return a {@code List<GmmInput>} of Gmm inputs
	 * @see Gmm
	 */
	public static Callable<List<GmmInput>> newFaultCalcInitializer(final FaultSource source,
			final Site site) {
		return new FaultCalcInitializer(source, site);
	}

	/**
	 * Creates a {@code Callable} from a {@code FaultSource} and {@code Site}
	 * that returns a {@code List<GmmInput>} of inputs for a ground motion
	 * model (Gmm) calculation.
	 * @param source
	 * @param site
	 * @return a {@code List<GmmInput>} of Gmm inputs
	 * @see Gmm
	 */
	public static Callable<GmmInput> newIndexedFaultCalcInitializer(
			final IndexedFaultSource source, final Site site,
			final Table<DistanceType, Integer, Double> rTable, final List<Integer> sectionIDs) {
		return new IndexedFaultCalcInitializer(source, site, rTable, sectionIDs);
	}

	/**
	 * Creates a {@code Callable} that returns the distances between the
	 * supplied {@code IndexedFaultSurface} and {@code Location}.
	 * 
	 * @param surface for distance
	 * @param loc for distance calculation
	 * @return a {@code Distances} wrapper object
	 */
	public static Callable<Distances> newDistanceCalc(final IndexedFaultSurface surface,
			final Location loc) {
		return new DistanceCalc(surface, loc);
	}

	/**
	 * Creates a {@code Callable} that returns the supplied {@code FaultSource}
	 * if it is within {@code distance} of a {@code Location}.
	 * 
	 * @param source to filter
	 * @param loc for distance calculation
	 * @param distance limit
	 * @return the supplied {@code source} or {@code null} if source is farther
	 *         than {@code distance} from {@code loc}
	 */
	public static Callable<FaultSource> newQuickDistanceFilter(final FaultSource source,
			final Location loc, final double distance) {
		return new QuickDistanceFilter(source, loc, distance);
	}

	/**
	 * Creates a {@code Callable} that processes {@code GmmInput}s against one
	 * or more {@code GroundMotionModel}s and returns the results in a
	 * {@code Map}.
	 * @param gmmInstanceMap ground motion models to use
	 * @param input to the models
	 * @return a {@code Map} of {@code ScalarGroundMotion}s
	 */
	public static Callable<GroundMotionCalcResult> newGroundMotionCalc(
			final Map<Gmm, GroundMotionModel> gmmInstanceMap, final GmmInput input) {
		return new GroundMotionCalc(gmmInstanceMap, input);
	}

}
