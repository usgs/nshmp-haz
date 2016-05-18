package org.opensha2.function;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Point2DToleranceSortedArrayList implements Point2DToleranceSortedList {

  private Point2DComparator comparator;
  private List<Point2D> list;

  public Point2DToleranceSortedArrayList(Point2DComparator comparator) {
    this.comparator = comparator;
    list = new ArrayList<Point2D>();
  }

  @Override
  public Iterator<Point2D> iterator() {
    return list.iterator();
  }

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public double getTolerance() {
    return comparator.getTolerance();
  }

  @Override
  public void setTolerance(double newTolerance) {
    comparator.setTolerance(newTolerance);
  }

  @Override
  public int binarySearch(Point2D p) {
    return Collections.binarySearch(list, p, comparator);
  }

  @Override
  public boolean add(Point2D e) {
    int ind = binarySearch(e);
    if (ind >= 0) {
      list.set(ind, e);
    } else {
      list.add(-ind - 1, e);
    }
    return true;
  }

  @Override
  public boolean addAll(Collection<? extends Point2D> c) {
    for (Point2D p : c) {
      add(p);
    }
    return !c.isEmpty();
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof Point2D) {
      int ind = binarySearch((Point2D) o);
      if (ind >= 0) {
        list.remove(ind);
        return true;
      }
      return false;
    }
    throw new ClassCastException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    boolean ret = false;
    for (Object o : c) {
      if (remove(o)) {
        ret = true;
      }
    }
    return ret;
  }

  @Override
  public double getMinY() {
    if (isEmpty()) {
      throw new IndexOutOfBoundsException("list is empty!");
    }
    double minY = Double.POSITIVE_INFINITY;
    for (Point2D p : this) {
      if (p.getY() < minY) {
        minY = p.getY();
      }
    }
    return minY;
  }

  @Override
  public double getMaxY() {
    if (isEmpty()) {
      throw new IndexOutOfBoundsException("list is empty!");
    }
    double maxY = Double.NEGATIVE_INFINITY;
    for (Point2D p : this) {
      if (p.getY() > maxY) {
        maxY = p.getY();
      }
    }
    return maxY;
  }

  @Override
  public double getMinX() {
    if (isEmpty()) {
      throw new IndexOutOfBoundsException("list is empty!");
    }
    return get(0).getX();
  }

  @Override
  public double getMaxX() {
    if (isEmpty()) {
      throw new IndexOutOfBoundsException("list is empty!");
    }
    return get(size() - 1).getX();
  }

  @Override
  public Point2D get(int index) {
    if (index >= 0 && index < size()) {
      return list.get(index);
    }
    return null;
  }

  @Override
  public boolean remove(int index) {
    list.remove(index);
    return true;
  }

  @Override
  public Point2D get(double x) {
    int ind = indexOf(new Point2D.Double(x, 0d));
    return get(ind);
  }

  @Override
  public int indexOf(Point2D findPoint) {
    int ind = binarySearch(findPoint);
    if (ind < -1) {
      ind = -1;
    }
    return ind;
  }

  @Override
  public Point2DComparator getComparator() {
    return comparator;
  }

  @Override
  public void clear() {
    list.clear();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

}
