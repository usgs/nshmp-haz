package org.opensha.gmm;

import static com.google.common.base.Charsets.US_ASCII;
import static org.opensha.gmm.GMM.*;
import static org.opensha.gmm.IMT.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.calc.ScalarGroundMotion;
import org.opensha.util.Parsing;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class Tests_NGAW2 {

	private static final String D_DIR = "data/";
	static final String GMM_INPUTS = "NGAW2_inputs.csv";
	private static final String GMM_RESULTS = "NGAW2_results.csv";
	private static final double TOL = 0.000001; // results precision = 1e-6
	private static List<GMM_Input> inputsList;
	
	
	static {
		try {
			inputsList = loadInputs(GMM_INPUTS);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}
	
    @Parameters(name = "{index}: {0} {2} {1}")
    public static Collection<Object[]> data() throws IOException {
    	return loadResults(GMM_RESULTS);
    }

    private int idx;
    private GMM gmm;
    private IMT imt;
    private double exMedian;
    private double exSigma;
	
	public Tests_NGAW2(int idx, GMM gmm, IMT imt, double exMedian,
		double exSigma) {

		this.idx = idx;
		this.gmm = gmm;
		this.imt = imt;
		this.exMedian = exMedian;
		this.exSigma = exSigma;
	}

    @Test
    public void test() {
    	ScalarGroundMotion sgm = gmm.instance(imt).calc(inputsList.get(idx));
        assertEquals(exMedian, Math.exp(sgm.mean()), TOL);
        assertEquals(exSigma, sgm.stdDev(), TOL);
    }

	public static void main(String[] args) throws IOException {
		computeGM();
	}
    
	/* Result generation sets */
	private static Set<GMM> gmms = EnumSet.of(ASK_14, BSSA_14, CB_14, CY_14, IDRISS_14);
	private static Set<IMT> imts = EnumSet.of(PGA, SA0P02, SA0P2, SA1P0, SA3P0);
	
	/* Use to generate GMM result file */
	private static void computeGM() throws IOException {
		List<GMM_Input> inputs = loadInputs(GMM_INPUTS);
		File out = new File("tmp/GMM-tests/" + GMM_RESULTS);
		Files.write("", out, Charsets.US_ASCII);
		for (GMM gmm : gmms) {
			for (IMT imt : imts) {
				GroundMotionModel gmModel = gmm.instance(imt);
				int modelIdx = 0;
				String id = gmm.name() + "-" + imt;
				for (GMM_Input input : inputs) {
					ScalarGroundMotion sgm = gmModel.calc(input);
					String result = Parsing.joinOnCommas(
						Lists.newArrayList(modelIdx++ + "-" + id,
						String.format("%.6f", Math.exp(sgm.mean())),
						String.format("%.6f", sgm.stdDev()))) +
						StandardSystemProperty.LINE_SEPARATOR.value();
					Files.append(result, out, Charsets.US_ASCII);
				}
			}
		}
	}
	
	double[] hcVals = new double[] { 0.0010, 0.0013, 0.0018, 0.0024, 0.0033, 
		0.0044, 0.0059, 0.0080, 0.0108, 0.0145, 0.0195, 0.0263, 0.0353, 0.0476, 
		0.0640, 0.0862, 0.1160, 0.1562, 0.2102, 0.2829, 0.3808, 0.5125, 0.6898, 
		0.9284, 1.2496, 1.6819, 2.2638, 3.0470, 4.1011, 5.5200, 7.4296, 10.0000 };
	
	// for each model, output 
	private static void computeUHRS() throws IOException {
		
	}
	
	// @formatter:off
	
	private static List<Object[]> loadResults(String resource) throws IOException {
		URL url = Resources.getResource(Tests_NGAW2.class, D_DIR + resource);
		return FluentIterable
				.from(Resources.readLines(url, US_ASCII))
				.transform(ResultsToObjectsFunction.INSTANCE)
				.toList();
	}
	
	private enum ResultsToObjectsFunction implements Function<String, Object[]> {
		INSTANCE;
		@Override
		public Object[] apply(String line) {
			Iterator<String> lineIt = Parsing.splitOnCommas(line).iterator();
			Iterator<String> idIt = Parsing.splitOnDash(lineIt.next()).iterator();
			return new Object[] {
				Integer.valueOf(idIt.next()),	// inputs index
				GMM.valueOf(idIt.next()),		// GMM
				IMT.valueOf(idIt.next()),		// IMT
				Double.valueOf(lineIt.next()),	// median
				Double.valueOf(lineIt.next())	// sigma
			};
		}
	}
	
	static List<GMM_Input> loadInputs(String resource) throws IOException {
		URL url = Resources.getResource(Tests_NGAW2.class, D_DIR + resource);
		return FluentIterable
				.from(Resources.readLines(url, US_ASCII))
				.skip(1)
				.transform(ArgsToInputFunction.INSTANCE)
				.toList();
	}
	
	private enum ArgsToInputFunction implements Function<String, GMM_Input> {
		INSTANCE;
		@Override
		public GMM_Input apply(String line) {

			Iterator<Double> it = FluentIterable
				.from(Parsing.splitOnCommas(line))
				.transform(Parsing.doubleValueFunction())
				.iterator();

			return GMM_Input.builder()
				.mag(it.next())
				.distances(it.next(), it.next(), it.next())
				.dip(it.next())
				.width(it.next())
				.zTop(it.next())
				.zHyp(it.next())
				.rake(it.next())
				.vs30(it.next(), it.next() > 0.0)
				.z2p5(it.next())
				.z1p0(it.next())
				.build();
		}
	}
	
}
