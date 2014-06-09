package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.forecast.FaultSourceSet.Builder;
import org.opensha.geo.Location;

import com.google.common.collect.Lists;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class SubductionSourceSet implements SourceSet<SubductionSource> {

	private final List<SubductionSource> sources;
	private final String name;
	private final double weight;
	private final MagScalingType msrType;

	// NOTE we're holding onto weight for reference, however, MFD
	// rates will have already been scaled in place. The weight value
	// may come in handy when trying to put together individual
	// logic tree branches.
	//
	// NOTE msrType is currently not exposed
	
	private SubductionSourceSet(String name, double weight, MagScalingType msrType,
			List<SubductionSource> sources) {
		this.name = name;
		this.weight = weight;
		this.msrType = msrType;
		this.sources = sources;
	}
	
	@Override
	public Iterable<SubductionSource> locationIterable(Location loc) {
		// TODO
		return null;
	}	
	
	@Override
	public Iterator<SubductionSource> iterator() {
		return sources.iterator();
	}

	@Override
	public String name() {
		return name;
	}
	
	@Override
	public double weight() {
		return weight;
	}

	@Override
	public int size() {
		return sources.size();
	}

	@Override
	public SourceType type() {
		return SourceType.INTERFACE;
	}

	static class Builder extends FaultSourceSet.Builder {

		static final String mssgID = "SubductionSourceSet.Builder";

		// type-specific field
		List<SubductionSource> sources = Lists.newArrayList();

		// type-specific method
		Builder source(SubductionSource source) {
			sources.add(checkNotNull(source, "SubductionSource is null"));
			return this;
		}

		SubductionSourceSet buildSubductionSet() {
			validateState(ID);
			return new SubductionSourceSet(name, weight, magScaling, sources);
		}
	}

}
