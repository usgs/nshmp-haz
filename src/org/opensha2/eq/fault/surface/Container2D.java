package org.opensha2.eq.fault.surface;

import org.opensha2.util.Named;

import java.util.ListIterator;

/**
 * <b>Title:</b> Container2DAPI<p>
 *
 * <b>Description:</b> Main interface that all 2D data containers must
 * implement. These provide functions for iteration through the elements,
 * replacing elements, etc. Each element is any object that extends Object. <p>
 *
 * This two dimensional grid acts more like a matrix than a geographical array
 * of location objects. You can store anything in each grid point. One of the
 * main purposes we use this class for is to store Location objects (latitude,
 * longitude) at each grid point. It just so happens that the location objects
 * are sorted spatially so that locations next to each other in real space are
 * next to each other in the grid. The confusing point is that even though the
 * grid is rectangular, the location objects may not map out to a rectangular
 * grid on the earth's surface at all. This is a fine point distintion but worth
 * noting due to confusion in designing this class.<p>
 *
 * @author Steven W. Rock
 */
@Deprecated
public interface Container2D<T> extends Named, Iterable<T> {

  /**
   * Returns the number of rows int this two dimensional container.
   *
   * @return Number of rows.
   */
  public int getNumRows();

  /**
   * Every container has a name associated with it to distinguish it from other
   * container instances.
   * @param name
   */
  public void setName(String name);

  /**
   * Returns the number of columns in this two dimensional container.
   *
   * @return Get number of columns.
   */
  public int getNumCols();

  /**
   * Empties the list of all data. Note, the data may still exist elsewhere if
   * it is referenced elsewhere.
   */
  public void clear();

  /**
   * Check if this grid cell has a java object stored in it. Returns false if
   * this gird point object is null.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   * @return True if an object has been set in this cell.
   */
  public boolean exist(int row, int column);

  /**
   * returns the number of cells in this two dimensional matrix, i.e. numwRows *
   * numCols.
   *
   * @return The number of cells.
   */
  public long size();

  /**
   * Places a Java object into one cell in this two dimensional matrix specified
   * by the row and column indices.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   * @param obj The Java object to place in the cell.
   * @exception ArrayIndexOutOfBoundsException Thrown if the row and column are
   *            beyond the two dimensional matrix range.
   * @exception ClassCastException Thrown by subclasses that expect a particular
   *            type of Java object.
   */
  public void set(int row, int column, T obj) throws ArrayIndexOutOfBoundsException,
  ClassCastException;

  // TODO should be immutable and have a Builder

  /**
   * Returns the object stored in this two dimensional grid cell.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   */
  public T get(int row, int column);

  /**
   * Returns an ordered list iterator over all columns associated with one row.
   * This returns all the objects in that row. The results are returned from
   * lowest index to highest along the interating row.
   *
   * @param row The x coordinate of the cell.
   * @return The columnIterator value
   * @exception ArrayIndexOutOfBoundsException Thrown if the row is beyond the
   *            two dimensional matrix range.
   */
  public ListIterator<T> getColumnIterator(int row) throws ArrayIndexOutOfBoundsException;

  /**
   * Returns an ordered list iterator over all rows associated with one column.
   * This returns all the objects in that column. The results are returned from
   * lowest index to highest along the interating column.
   *
   * @param column The y coordinate of the cell.
   * @return The rowIterator value
   * @exception ArrayIndexOutOfBoundsException Thrown if the column is beyond
   *            the two dimensional matrix range.
   */
  public ListIterator<T> getRowIterator(int column) throws ArrayIndexOutOfBoundsException;

  /**
   * This returns an iterator of all the Java objects stored in this two
   * dimensional matrix iterating over all rows within a column and then moving
   * to the next column until iteration has been done over all rows and all
   * columns.
   *
   * @return The allByColumnsIterator value
   */
  public ListIterator<T> getAllByColumnsIterator();

  /**
   * This returns an iterator of all the Java objects stored in this two
   * dimensional matrix iterating over all columns within a rows and then moving
   * to the next column until iteration has been done over all columns and all
   * rows.
   *
   * @return The allByRowsIterator value
   */
  public ListIterator<T> getAllByRowsIterator();

}
