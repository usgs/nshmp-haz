package org.opensha.eq.forecast;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class FaultSourceSet implements SourceSet<FaultSource> {

	private final List<FaultSource> sources = Lists.newArrayList();
	private final String name;
	private double weight;
	
	FaultSourceSet(String name, double weight) {
		this.name = name;
		this.weight = weight;
		
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
		return null;
		// TODO do nothing
		
	}

	//TODO tmp delete
	public int size() { return sources.size(); }

	@Override
	public SourceType type() {
		return SourceType.FAULT;
	}

}
