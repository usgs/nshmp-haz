package org.opensha.function;

import java.awt.geom.Point2D;
import java.util.Collection;

public interface Point2DToleranceSortedList extends Iterable<Point2D> {

	public double getTolerance();

	public void setTolerance(double newTolerance);

	public boolean add(Point2D e);

	public boolean addAll(Collection<? extends Point2D> c);

	public boolean remove(Object obj);

	public boolean removeAll(Collection<?> c);

	public double getMinY();

	public double getMaxY();

	public double getMinX();

	public double getMaxX();

	public Point2D get(int index);

	public boolean remove(int index);

	public Point2D get(double x);

	public int indexOf(Point2D findPoint);
	
	public Point2DComparator getComparator();
	
	public int size();
	
	public void clear();
	
	public int binarySearch(Point2D p);

}