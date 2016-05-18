package org.opensha2.gmm;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
// import org.opensha.commons.data.function.DiscretizedFunc;
// import org.opensha.commons.exceptions.ConstraintException;
// import org.opensha.commons.exceptions.ParameterException;
// import org.opensha.commons.param.Parameter;
// import org.opensha.commons.util.DataUtils;
// import org.opensha.nshmp2.util.CurveTable;
// import org.opensha.nshmp2.util.FaultCode;
// import org.opensha.nshmp2.util.Period;
// import org.opensha.nshmp2.util.Utils;
// import org.opensha.sha.imr.AttenRelRef;
// import org.opensha.sha.imr.ScalarIMR;
// import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
// import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
// import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
// import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
// import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
// import
// org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;

/*
 * This class first tests ground motion mean and sigma for NSHMP CEUS
 * attenuation relationships. Tests are performed for events at:
 *
 * distance R = 0km, 10km, 100km
 *
 * magnitude M = 5.05, 5.75, 6.45, 7.15
 *
 * period P = 0.0 (PGA), 0.2sec, 1.0sec, 2.0sec
 *
 * The NSHMP results were calculated using hazgridXnga5.f which creates lookup
 * arrays of hazard values spannning user specified distances, mags, etc. As
 * such a Okm = 2.5km, 10km = 12.5km etc.
 *
 * This class tests both Atkinson & Boore and Johnson mblg to Mw magnitude
 * conversion branches.
 */
@Deprecated // until recussitated
public class Ceus2008 {

  // TODO tests grid table lookups

  private static final double MEAN_TOL_PCT = 0.005;
  private static final double MEAN_TOL_ABS = 0.00001;
  private static final double FUNC_TOL = 0.0000000001;

  private static final Path RESULTS_BASE = Paths.get("test", "org", "opensha2", "gmm");

  // AB mag conversion; firm-rock
  private static String ceus_ab_bc_name = RESULTS_BASE + "CEUS_2008_AB_BC.txt";
  private static File ceus_ab_bc = new File(ceus_ab_bc_name);
  private static String ceus_j_bc_name = RESULTS_BASE + "CEUS_2008_J_BC.txt";
  private static File ceus_j_bc = new File(ceus_j_bc_name);

  //// private static Map<Gmm, ScalarIMR> imrMap_AB;
  // private static Set<Gmm> imrs_AB;
  // private static Map<Gmm, Map<Imt, CurveTable>> curveTableMap_AB;
  //// private static Map<Gmm, ScalarIMR> imrMap_J;
  // private static Set<Gmm> imrs_J;
  // private static Map<Gmm, Map<Period, CurveTable>> curveTableMap_J;
  //
  // @BeforeClass
  // public static void setUp() {
  // imrMap_AB = Maps.newEnumMap(AttenRelRef.class);
  // curveTableMap_AB = Maps.newEnumMap(AttenRelRef.class);
  // imrMap_J = Maps.newEnumMap(AttenRelRef.class);
  // curveTableMap_J = Maps.newEnumMap(AttenRelRef.class);
  //
  // init(ceus_ab_bc, imrMap_AB, curveTableMap_AB, M_CONV_AB);
  // init(ceus_j_bc, imrMap_J, curveTableMap_J, M_CONV_J);
  //
  // }
  //
  // private static void init(File nshmResults,
  // Map<AttenRelRef, ScalarIMR> imrMap,
  // Map<AttenRelRef, Map<Period, CurveTable>> curveTableMap,
  // FaultCode magConv) {
  // try {
  // List<String> lines = Files
  // .readLines(nshmResults, Charsets.US_ASCII);
  // // determine which rate tables to build
  // Set<Period> periods = Sets.newHashSet();
  // Set<AttenRelRef> imrRefs = Sets.newHashSet();
  // for (String line : lines) {
  // if (line.startsWith("GM")) {
  // String[] parts = StringUtils.split(line);
  // periods.add(Period.valueOf(parts[0]));
  // imrRefs.add(AttenRelRef.valueOf(parts[1]));
  // }
  // }
  //
  // for (AttenRelRef imrRef : imrRefs) {
  // Map<Period, CurveTable> tableMap = curveTableMap.get(imrRef);
  // if (tableMap == null) {
  // tableMap = Maps.newEnumMap(Period.class);
  // curveTableMap.put(imrRef, tableMap);
  // }
  //
  // for (Period period : periods) {
  // ScalarIMR imr = imrRef.instance(null);
  // imr.setParamDefaults();
  // imr.setIntensityMeasure((period == GM0P00) ?
  // PGA_Param.NAME : SA_Param.NAME);
  // try {
  // imr.getParameter(PeriodParam.NAME).setValue(period.getValue());
  // } catch (ConstraintException ce) { /* do nothing */ }
  // imrMap.put(imrRef, imr);
  //
  //
  // Map<ScalarIMR, Double> imrWtMap = Maps.newHashMap();
  // imrWtMap.put(imr, 1.0);
  //
  // CurveTable table = CurveTable.create(200d, 5d, 5.0, 7.4,
  // 0.1, imrWtMap, period.getFunction(), magConv);
  // tableMap.put(period, table);
  // }
  // }
  // } catch (IOException ioe) {
  // ioe.printStackTrace();
  // Assert.fail();
  // }
  // }
  //
  // @Test
  // public void ceus_Jconv_BC_Test() {
  // runTest(ceus_j_bc, imrMap_J, curveTableMap_J, M_CONV_J);
  // }
  //
  // @Test
  // public void ceus_ABconv_BC_Test() {
  // runTest(ceus_ab_bc, imrMap_AB, curveTableMap_AB, M_CONV_AB);
  // }
  //
  // private void runTest(File nshmpResults, Map<AttenRelRef, ScalarIMR> imrMap,
  // Map<AttenRelRef, Map<Period, CurveTable>> curveTableMap, FaultCode magConv)
  //// {
  // try {
  // List<String> lines = Files.readLines(nshmpResults,
  // Charsets.US_ASCII);
  // AttenRelRef imrRef = null;
  // ScalarIMR imr = null;
  // Period per = null;
  // // read result lines
  // for (String line : lines) {
  // if (line.startsWith("#") || StringUtils.isBlank(line))
  // continue;
  // // update the period and imr if necessary
  // if (line.startsWith("GM")) {
  // String[] parts = StringUtils.split(line);
  // per = Period.valueOf(parts[0]);
  // imrRef = AttenRelRef.valueOf(parts[1]);
  // imr = imrMap.get(imrRef);
  // imr.setIntensityMeasure((per == GM0P00) ? PGA_Param.NAME
  // : SA_Param.NAME);
  // imr.getParameter(PeriodParam.NAME).setValue(per.getValue());
  // continue;
  // }
  // // process result lines
  // String[] results = StringUtils.split(line);
  // double R = Double.valueOf(results[0]);
  // double M = Double.valueOf(results[1]);
  // double nshmMean = Double.valueOf(results[2]);
  // double nshmStd = Double.valueOf(results[3]);
  //
  // // set mag and distance
  // double mm = (imrRef != TORO_1997) ? Utils.mblgToMw(magConv, M)
  // : M;
  // imr.getParameter(MagParam.NAME).setValue(mm);
  //
  // double rr = R + 2.5; // ceus table offset
  // try {
  // // try to set rjb first
  // Parameter<Double> rjbParam = imr
  // .getParameter(DistanceJBParameter.NAME);
  // rjbParam.setValue(rr);
  // } catch (ParameterException pe1) {
  // try {
  // // then try rRup if rjb fails
  // Parameter<Double> rrupParam = imr
  // .getParameter(DistanceRupParameter.NAME);
  // rrupParam.setValue(Math.sqrt(rr * rr + 25)); // 5km dtor
  // } catch (ParameterException pe2) {
  // pe2.printStackTrace();
  // }
  // }
  //
  // // perform mean and std comparisons
  // double mean = imr.getMean();
  // double std = imr.getStdDev();
  //
  // // no real good reason to test absolute and pct differences;
  // // pct diffs were added later to check smaller values but it
  // // turns out in this case fortran precision deteriorates at
  // // about 7 to 8 decimal places before conversion to scientific
  // // notation
  // double diff = DataUtils.getPercentDiff(nshmMean, mean);
  //
  // assertTrue(nshmMean + " " + mean + " " + " Diff: " + diff,
  // diff < MEAN_TOL_PCT);
  // assertEquals(nshmMean, mean, MEAN_TOL_ABS);
  // assertEquals(nshmStd, std, MEAN_TOL_ABS);
  //
  // String info = per + " " + imrRef + " " + M + " " + R;
  // // System.out.println(info);
  // // create sha curve
  // DiscretizedFunc f1 = curveTableMap.get(imrRef).get(per).get(R, M);
  //
  // // there are general rules that can be followed from the nshmp
  // // for additional truncation possibly below 3sigma. For these
  // // tests we can catch PGA (3g) and set the rest to 6g
  // DiscretizedFunc f2 = per.getFunction();
  // double truncVal = (per == GM0P00) ? 3.0 : (per == GM0P20) ? 6.0
  // : 0.0;
  // f2 = Utils.getExceedProbabilities(f2, mean, std, true, truncVal);
  //
  // boolean test = false;
  // if (test) {
  // System.out.println("====");
  // System.out.println(M+" "+R);
  // System.out.println(per);
  // System.out.println(f1);
  // System.out.println(f2);
  // }
  //
  // compareCurves(f1, f2, FUNC_TOL, info);
  // }
  // } catch (IOException ioe) {
  // ioe.printStackTrace();
  // }
  // }
  //
  // /*
  // * Compares y-values of the supplied functions to within the supplied
  // * tolerance. x-values are also checked. Any error will return a message
  // * String, otheriwse method returns null.
  // */
  // private static void compareCurves(DiscretizedFunc f1, DiscretizedFunc f2,
  // double tol, String info) {
  // f1.setTolerance(tol);
  // f2.setTolerance(tol);
  // for (Point2D p : f1) {
  // // check that x value exists
  // assertTrue("Function 2 missing point:" + p, f2.hasX(p.getX()));
  // // compare pct diff of y
  // double y1 = p.getY();
  // double y2 = f2.getY(p.getX());
  // double diff = DataUtils.getPercentDiff(y1, y2);
  // assertTrue(info + "\n" + y1 + " " + y2 + " " + " Diff: " + diff,
  // diff < tol);
  // }
  // }

}
