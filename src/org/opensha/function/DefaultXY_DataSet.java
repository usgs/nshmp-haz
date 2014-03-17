package org.opensha.function;

import static com.google.common.base.Preconditions.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import com.google.common.primitives.Doubles;

/**
 * Container for a data set of (X,Y) values. Internally, value pairs are stored
 * as {@link Point2D}s.
 * 
 * @author Peter Powers
 * @author Kevin Milner
 * @version $Id: DefaultXY_DataSet.java 9729 2012-11-07 21:33:01Z kmilner $
 */
public class DefaultXY_DataSet extends AbstractXY_DataSet {
	
	private ArrayList<Point2D> points;
	
	private SummaryStatistics xStats;
	private SummaryStatistics yStats;
		
	/**
	 * Initializes a new, empty data set.
	 */
	public DefaultXY_DataSet() { init(); }

	/**
	 * Initializes a new data set with the supplied <code>List</code>s of x and
	 * y data
	 * @param x values
	 * @param y values
	 * @throws NullPointerException if either data list is null
	 * @throws IllegalArgumentException if either data list is empty
	 */
	public DefaultXY_DataSet(List<Double> x, List<Double> y) {
		this(Doubles.toArray(x), Doubles.toArray(y));
	}	

	/**
	 * Initializes a new data set with the supplied arrays of x and
	 * y data
	 * @param x values
	 * @param y values
	 */
	public DefaultXY_DataSet(double[] x, double[] y) {
		checkNotNull(x, "Supplied x-values are null");
		checkNotNull(y, "Supplied y-values are null");
		checkArgument(x.length > 0, "Supplied x-values are empty");
		checkArgument(y.length > 0, "Supplied y-values are empty");
		checkArgument(x.length == y.length, "%s [x=%s, y=%s]",
			"Supplied data sets are different sizes", x.length, y.length);
		init();
		for (int i = 0; i < x.length; i++) {
			set(x[i], y[i]);
		}
	}

	private void init() {
		points = new ArrayList<Point2D>();
		xStats = new SummaryStatistics();
		yStats = new SummaryStatistics();
		
//		xTracker = new MinMaxAveTracker();
//		yTracker = new MinMaxAveTracker();
	}

	@Override
	public XY_DataSet deepClone() {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		for (Point2D pt : points) {
			xy.set(pt);
		}
		xy.setName(name());
		xy.setInfo(getInfo());
		xy.setXAxisName(getXAxisName());
		xy.setYAxisName(getYAxisName());
		return xy;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DefaultXY_DataSet))
			return false;
		DefaultXY_DataSet function = (DefaultXY_DataSet)obj;
		if( !name().equals(function.name() )  ) return false;

		if( !getInfo().equals(function.getInfo() )  ) return false;
		return true;
	}

	@Override
	public Point2D get(int index) {
		return points.get(index);
	}
 
	@Override
	public double getMaxX() {
		return xStats.getMax();
	}

	@Override
	public double getMaxY() {
		return yStats.getMax();
	}

	@Override
	public double getMinX() {
		return xStats.getMin();
	}

	@Override
	public double getMinY() {
		return yStats.getMin();
	}

	@Override
	public int getNum() {
		return points.size();
	}

	@Override
	public double getX(int index) {
		return get(index).getX();
	}

	@Override
	public double getY(int index) {
		return get(index).getY();
	}

	@Override
	public boolean hasPoint(Point2D point) {
		return points.contains(point);
	}

	@Override
	public boolean hasPoint(double x, double y) {
		return hasPoint(new Point2D.Double(x, y));
	}

	@Override
	public void set(Point2D point) {
		points.add(point);
		xStats.addValue(point.getX());
		yStats.addValue(point.getY());
	}

	@Override
	public void set(double x, double y) {
		set(new Point2D.Double(x, y));
	}

	@Override
	public void set(int index, double y) {
		throw new UnsupportedOperationException();
//		Point2D point = get(index);
//		if (point != null)
//			point.setLocation(point.getX(), y);
//		else
//			throw new IndexOutOfBoundsException();
	}

//	@Override
//	public Element toXMLMetadata(Element root) {
//		throw new RuntimeException("not supported");
//	}
	
	/**
	 * Standard java function, usually used for debugging, prints out
	 * the state of the list, such as number of points, the value of each point, etc.
	 * @return
	 */
	public String toString(){
		StringBuffer b = new StringBuffer();
		//Iterator it2 = this.iterator();

		b.append("Name: " + name() + '\n');
		b.append("Num Points: " + getNum() + '\n');
		b.append("Info: " + getInfo() + "\n\n");
		b.append("X, Y Data:" + '\n');
		b.append(getMetadataString()+ '\n');
		return b.toString();
	}
	
	/**
	 *
	 * @return value of each point in the function in String format
	 */
	public String getMetadataString(){
		StringBuffer b = new StringBuffer();
		Iterator<Point2D> it2 = this.iterator();

		while(it2.hasNext()){

			Point2D point = (Point2D)it2.next();
			double x = point.getX();
			double y = point.getY();
			b.append((float) x + "\t  " + (float) y + '\n');
		}
		return b.toString();
	}



}
