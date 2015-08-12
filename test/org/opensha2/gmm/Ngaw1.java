package org.opensha2.gmm;

import static org.opensha2.gmm.Gmm.*;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.SA0P02;
import static org.opensha2.gmm.Imt.SA0P2;
import static org.opensha2.gmm.Imt.SA1P0;
import static org.opensha2.gmm.Imt.SA3P0;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class Ngaw1 extends GmmTest {

	private static String GMM_INPUTS = "NGA_inputs.csv";
	private static String GMM_RESULTS = "NGAW1_results.csv";

	static {
		try {
			inputsList = loadInputs(GMM_INPUTS);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	@Parameters(name = "{index}: {0} {2} {1}") public static Collection<Object[]> data()
			throws IOException {
		return loadResults(GMM_RESULTS);
	}

	public Ngaw1(int idx, Gmm gmm, Imt imt, double exMedian, double exSigma) {
		super(idx, gmm, imt, exMedian, exSigma);
	}

	/* Result generation sets */
	private static Set<Gmm> gmms = EnumSet.of(BA_08, CB_08, CY_08);
	private static Set<Imt> imts = EnumSet.of(PGA, SA0P02, SA0P2, SA1P0, SA3P0);

	public static void main(String[] args) throws IOException {
		GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
	}

}
