/**
 * Geographic data classes and utilities.
 * 
 * <p>All objects and methods in this package assume that depths are
 * positive-down, consistent with seismological convention.
 * 
 * <p>Note that while the objects and methods of this package produce reliable
 * results for most regions of the globe, situations may arise in which users
 * encounter unexpected behaviour and results. For instance, the 'fast'
 * algorithms in {@link org.opensha2.geo.Locations} (e.g.
 * {@link org.opensha2.geo.Locations#horzDistanceFast(Location, Location)}) will
 * not produce accurate results when used in close proximity to the poles or
 * when locations span the antimeridian (the -180° +180° transition). In such
 * cases, users should consider substituting slower, but more accurate
 * algorithms. In the latter case, one could alternatively opt to use locations
 * referenced to 0° to 360° instead. Exceptional behavior is well documented in
 * the {@link org.opensha2.geo.Locations} class.
 */
package org.opensha2.geo;
