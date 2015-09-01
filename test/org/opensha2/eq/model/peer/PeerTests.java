package org.opensha2.eq.model.peer;

import java.io.IOException;
import java.util.Collection;

import org.junit.Ignore;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha2.calc.Site;
import org.opensha2.eq.model.HazardModel;

@SuppressWarnings("javadoc")
@RunWith(Enclosed.class)
public class PeerTests {

	/*
	 * This is the primary entry point for running all PEER test cases.
	 * 
	 * All tolerances are percentages.
	 * 
	 * Although HazardCurve calculations return results in annual rate, all PEER
	 * test case comparisons are done as Poisson probability.
	 */

	private static final double TOL = 1e-6;

	@RunWith(Parameterized.class)
	public static class Set1Case1 extends PeerTest {

		public Set1Case1(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C1, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case2 extends PeerTest {

		public Set1Case2(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C2, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case2_Fast extends PeerTest {

		public Set1Case2_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C2_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case3 extends PeerTest {

		public Set1Case3(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C3, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case3_Fast extends PeerTest {

		public Set1Case3_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C3_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case4 extends PeerTest {

		public Set1Case4(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C4, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case4_Fast extends PeerTest {

		public Set1Case4_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C4_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case5 extends PeerTest {

		public Set1Case5(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C5, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case5_Fast extends PeerTest {

		public Set1Case5_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C5_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case6 extends PeerTest {

		public Set1Case6(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C6, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case6_Fast extends PeerTest {

		public Set1Case6_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C6_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case7 extends PeerTest {

		public Set1Case7(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C7, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case7_Fast extends PeerTest {

		public Set1Case7_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C7_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case8a extends PeerTest {

		public Set1Case8a(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C8A, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case8b extends PeerTest {

		public Set1Case8b(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C8B, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case8c extends PeerTest {

		public Set1Case8c(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C8C, TOL);
		}
	}
	
	@RunWith(Parameterized.class)
	@Ignore
	public static class Set1Case10 extends PeerTest {

		public Set1Case10(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C10, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set1Case10_Fast extends PeerTest {

		public Set1Case10_Fast(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S1_C10_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case2a extends PeerTest {

		public Set2Case2a(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2A, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case2a_Fast extends PeerTest {

		public Set2Case2a_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2A_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case2b extends PeerTest {

		public Set2Case2b(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2B, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case2b_Fast extends PeerTest {

		public Set2Case2b_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2B_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case2c extends PeerTest {

		public Set2Case2c(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2C, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case2c_Fast extends PeerTest {

		public Set2Case2c_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2C_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case2d extends PeerTest {

		public Set2Case2d(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2D, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case2d_Fast extends PeerTest {

		public Set2Case2d_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C2D_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case3a extends PeerTest {

		public Set2Case3a(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3A, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case3a_Fast extends PeerTest {

		public Set2Case3a_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3A_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case3b extends PeerTest {

		public Set2Case3b(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3B, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case3b_Fast extends PeerTest {

		public Set2Case3b_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3B_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case3c extends PeerTest {

		public Set2Case3c(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3C, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case3c_Fast extends PeerTest {

		public Set2Case3c_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3C_F, TOL);
		}
	}
	
	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case3d extends PeerTest {

		public Set2Case3d(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3D, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case3d_Fast extends PeerTest {

		public Set2Case3d_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C3D_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case4a extends PeerTest {

		public Set2Case4a(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C4A, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case4a_Fast extends PeerTest {

		public Set2Case4a_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C4A_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	@Ignore
	public static class Set2Case4b extends PeerTest {

		public Set2Case4b(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C4B, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case4b_Fast extends PeerTest {

		public Set2Case4b_Fast(String modelId, HazardModel model, Site site, double[] values,
				double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C4B_F, TOL);
		}
	}

	@RunWith(Parameterized.class)
	public static class Set2Case5a extends PeerTest {

		public Set2Case5a(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C5A, TOL);
		}
	}


	@RunWith(Parameterized.class)
	public static class Set2Case5b extends PeerTest {

		public Set2Case5b(String modelId, HazardModel model, Site site, double[] values, double tol) {
			super(modelId, model, site, values, tol);
		}

		@Parameters(name = "{0}, Site{index}") public static Collection<Object[]> data()
				throws IOException {
			return load(S2_C5B, TOL);
		}
	}


}
