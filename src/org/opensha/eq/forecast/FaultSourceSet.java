package org.opensha.eq.forecast;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;

import com.google.common.collect.Lists;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class FaultSourceSet implements SourceSet<FaultSource> {

	private final List<FaultSource> sources = Lists.newArrayList();
	private final String name;
	private final double weight;
	private final MagScalingType magScaling;
	
	FaultSourceSet(String name, double weight, MagScalingType magScaling) {
		this.name = name;
		this.weight = weight;
		this.magScaling = magScaling;
		
		// TODO weight could be used in parser to scale all MFD rates
		// and not stored here
	}
	
	void add(FaultSource source) {
		sources.add(source);
	}
	
	
	@Override
	public Iterator<FaultSource> iterator() {
		return sources.iterator();
	}

	@Override
	public String name() {
		return name;
	}

	//TODO tmp delete
	public int size() { return sources.size(); }

	@Override
	public SourceType type() {
		return SourceType.FAULT;
	}

}
