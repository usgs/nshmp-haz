package org.opensha2.etc;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("javadoc")
public class HazMat {

  /*
   * Developer notes:
   * 
   * Mathworks has gone out of its way to make it more and more difficult to use
   * Java in Matlab as time goes on, despite it being a first-class citizen.
   * Specifically, the Matlab classpath includes a dizzying number of 3rd party
   * libraries, many of which are now well out of date. The problem posed to
   * nshmp-haz is the inclusion of v.15 of Google Guava (this, one can only
   * determine by unpacking the included google-collections.jar as they haven't
   * bothered to update the name of the dependency) whereas the current Guava
   * release is v.20. The aggressive deprecation and removal of bad, unused, or
   * superceeded code by Google has resulted in significant class and method
   * variations within Guava over 5 update cycles. Nshmp-haz will not remove
   * it's dependency on Guava, but putting nshmp-haz early on the Matlab
   * classpath causes Matlab to crash as it can't find (now missing) methods in
   * Guava.
   * 
   * The unsatisfactory but functional workaround is to use a custom class
   * loader that, once we want to use nshmp-haz classes, scans nshmp-haz.jar
   * before looking in the matlab classpath. This approach is messy in that the
   * sole point of entry is this class, which delegates to HazMatHelper (loaded
   * with the custom class loader) and the methods of which must be accessed via
   * reflection.
   * 
   * Note that if nshmp-haz moves to Java 8, which will probably occur sooner
   * rather than later, the custom class loader will also have to have Java 8 on
   * it's classpath. Sigh.
   */
  
  private static List<Class<?>> inputClasses = Arrays.asList(new Class<?>[] {
      double.class,
      double.class,
      double.class,
      double.class,
      double.class,
      double.class,
      double.class,
      double.class,
      double.class,
      double.class,
      boolean.class,
      double.class,
      double.class });

  private static Class<?>[] meanClasses;
  private static Class<?>[] spectrumClasses;

  static {
    List<Class<?>> classes = new ArrayList<>();
    classes.add(String.class);
    classes.addAll(inputClasses);
    spectrumClasses = classes.toArray(new Class[inputClasses.size() + 1]);
    classes.add(0, String.class);
    meanClasses = classes.toArray(new Class[inputClasses.size() + 2]);
  }

  private Object hazMatImpl;

  HazMat(Object hazMatImpl) {
    this.hazMatImpl = hazMatImpl;
  }

  public double[] gmmMean(String gmm, String imt, Input in) {
    try {
      Method method = hazMatImpl.getClass().getMethod("gmmMean", meanClasses);
      return (double[]) method.invoke(hazMatImpl,
          gmm,
          imt,
          in.Mw,
          in.rJB,
          in.rRup,
          in.rX,
          in.dip,
          in.width,
          in.zTop,
          in.zHyp,
          in.rake,
          in.vs30,
          in.vsInf,
          in.z1p0,
          in.z2p5);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public HazMatSpectrum gmmSpectrum(String gmm, Input in) {
    try {
      Method method = hazMatImpl.getClass().getMethod("gmmSpectrum", spectrumClasses);
      double[][] result = (double[][]) method.invoke(hazMatImpl,
          gmm,
          in.Mw,
          in.rJB,
          in.rRup,
          in.rX,
          in.dip,
          in.width,
          in.zTop,
          in.zHyp,
          in.rake,
          in.vs30,
          in.vsInf,
          in.z1p0,
          in.z2p5);
      HazMatSpectrum spectrum = new HazMatSpectrum();
      spectrum.periods = result[0];
      spectrum.means = result[1];
      spectrum.sigmas = result[2];
      return spectrum;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static HazMat init(String nshmpHazPath) {
    try {
      URL nshmpHazUrl = Paths.get(nshmpHazPath).toUri().toURL();
      List<URL> classpath = new ArrayList<>();
      classpath.add(nshmpHazUrl);
      ClassLoader loader = new ParentLastURLClassLoader(classpath);
      Class<?> clazz = loader.loadClass("org.opensha2.etc.HazMatImpl");
      return new HazMat(clazz.newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static class Input {
    public double Mw;
    public double rJB;
    public double rRup;
    public double rX;
    public double dip;
    public double width;
    public double zTop;
    public double zHyp;
    public double rake;
    public double vs30;
    public boolean vsInf;
    public double z1p0;
    public double z2p5;
  }

}
