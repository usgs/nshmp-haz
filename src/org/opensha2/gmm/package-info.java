/**
 * Implementations of all {@link org.opensha2.gmm.GroundMotionModel}s used in
 * the 2008 and 2014 USGS National Seismic Hazard Models.
 *
 * <p>Most of the model implementations in this package may not be instantiated
 * directly. Rather, instances of each are/should be obtained via
 * {@link org.opensha2.gmm.Gmm#instance(Imt)}. The various models are exposed
 * publicly for the sake of documentation. In many cases, the public
 * implementations are {@code abstract}, and there are a variety of model
 * flavors that are available via the {@link org.opensha2.gmm.Gmm} enum.
 */
package org.opensha2.gmm;
