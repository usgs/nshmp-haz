package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.DIP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RAKE;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RJB;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RX;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VSINF;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.WIDTH;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.Z1P0;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.Z2P5;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZHYP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.gmm.GmmInput.Builder;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;

/**
 * JUnit test for GmmInput
 *
 */
@SuppressWarnings("javadoc")
public class GmmInputTest {
	
	private static final double dip = DIP.defaultValue;
	private static final double Mw = MW.defaultValue;
	private static final double rake = RAKE.defaultValue;
	private static final double rJB = RJB.defaultValue;
	private static final double rRup = RRUP.defaultValue;
	private static final double rX = RX.defaultValue;
	private static final double vs30 = VS30.defaultValue;
	private static final boolean vsInf = VSINF.defaultValue > 0.0;
	private static final double width = WIDTH.defaultValue;
	private static final double z1p0 = Z1P0.defaultValue;
	private static final double z2p5 = Z2P5.defaultValue;
	private static final double zHyp = ZHYP.defaultValue;
	private static final double zTop = ZTOP.defaultValue;
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public final void builderAlreadySetDip() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + DIP.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.dip(dip);
		gmmBuilder.dip(dip);
	}
	
	@Test
	public final void builderAlreadySetDistances() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + RJB.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.distances(rJB, rRup, rX);
		gmmBuilder.distances(rJB, rRup, rX);
	}
	
  @Test
	public final void builderAlreadySetMag() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + MW.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.mag(Mw);
		gmmBuilder.mag(Mw);
	}
	
	@Test
	public final void builderAlreadySetRake() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + RAKE.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.rake(rake);
		gmmBuilder.rake(rake);
	}
	
  @Test
	public final void builderAlreadySetRjb() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + RJB.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.rJB(rJB);
		gmmBuilder.rJB(rJB);
	}
	
	@Test
	public final void builderAlreadySetRrup() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + RRUP.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.rRup(rRup);
		gmmBuilder.rRup(rRup);
	}
	
	@Test
	public final void builderAlreadySetRx() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + RX.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.rX(rX);
		gmmBuilder.rX(rX);
	}
	
	@Test
	public final void builderAlreadySetVs30() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + VS30.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.vs30(vs30);
		gmmBuilder.vs30(vs30);
	}
	
	@Test
	public final void builderAlreadySetVs30Vsinf() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + VS30.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.vs30(vs30, vsInf);
		gmmBuilder.vs30(vs30, vsInf);
	}
	
  @Test
	public final void builderAlreadySetVsinf() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + VSINF.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.vsInf(vsInf);
		gmmBuilder.vsInf(vsInf);
	}
	
	@Test
	public final void builderAlreadySetWidth() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + WIDTH.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.width(width);
		gmmBuilder.width(width);
	}
	
	@Test
	public final void builderAlreadySetZ1p0() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + Z1P0.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.z1p0(z1p0);
		gmmBuilder.z1p0(z1p0);
	}
	
	@Test
	public final void builderAlreadySetZ2p5() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + Z2P5.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.z2p5(z2p5);
		gmmBuilder.z2p5(z2p5);
	}
	
	@Test
	public final void builderAlreadySetZhyp() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + ZHYP.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.zHyp(zHyp);
		gmmBuilder.zHyp(zHyp);
	}
  
  @Test
	public final void builderAlreadySetZtop() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Field " + ZTOP.toString() + " already set");
		
		Builder gmmBuilder = GmmInput.builder().withDefaults();
		gmmBuilder.zTop(zTop);
		gmmBuilder.zTop(zTop);
	}
	
	@Test
	public final void builderBuildNotSet() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Not all fields set");
		
		Builder gmmBuilder = GmmInput.builder();
		gmmBuilder.build();
	}
	
  @Test
	public final void builderFromCopy() {
		GmmInput model = GmmInput.builder().withDefaults().build();
		GmmInput gmmCopy = GmmInput.builder().fromCopy(model).build();
		
		assertEquals("dip not equal-", gmmCopy.dip, model.dip, 0);
		assertEquals("Mw not equal-", gmmCopy.Mw, model.Mw, 0);
		assertEquals("rake not equal-", gmmCopy.rake, model.rake, 0);
		assertEquals("rJB not equal-", gmmCopy.rJB, model.rJB, 0);
		assertEquals("rRup not equal-", gmmCopy.rRup, model.rRup, 0);
		assertEquals("rX not equal-", gmmCopy.rX, model.rX, 0);
		assertEquals("vs30 not equal-", gmmCopy.vs30, model.vs30, 0);
		assertEquals("vInf not equal-", gmmCopy.vsInf, model.vsInf);
		assertEquals("width not equal-", gmmCopy.width, model.width, 0);
		assertEquals("z1p0 not equal-", gmmCopy.z1p0, model.z1p0, 0);
		assertEquals("z2p5 not equal-", gmmCopy.z2p5, model.z2p5, 0);
		assertEquals("zHyp not equal-", gmmCopy.zHyp, model.zHyp, 0);
		assertEquals("zTop not equal-", gmmCopy.zTop, model.zTop, 0);
	}
	
	@Test 
	public final void builderSet() {
		double val = 10.0;
		boolean bool = true;
		
		Builder gmmBuilder = GmmInput.builder();
		gmmBuilder.set(DIP, Double.toString(val));
		gmmBuilder.set(MW, Double.toString(val));
		gmmBuilder.set(RAKE, Double.toString(val));
		gmmBuilder.set(RJB, Double.toString(val));
		gmmBuilder.set(RRUP, Double.toString(val));
		gmmBuilder.set(RX, Double.toString(val));
		gmmBuilder.set(VS30, Double.toString(val));
		gmmBuilder.set(VSINF, Boolean.toString(bool));
		gmmBuilder.set(WIDTH, Double.toString(val));
		gmmBuilder.set(Z1P0, Double.toString(val));
		gmmBuilder.set(Z2P5, Double.toString(val));
		gmmBuilder.set(ZHYP, Double.toString(val));
		gmmBuilder.set(ZTOP, Double.toString(val));
		GmmInput gmm = gmmBuilder.build();
		
		assertEquals("dip not equal-", gmm.dip, val, 0);
		assertEquals("Mw not equal-", gmm.Mw, val, 0);
		assertEquals("rake not equal-", gmm.rake, val, 0);
		assertEquals("rJB not equal-", gmm.rJB, val, 0);
		assertEquals("rRup not equal-", gmm.rRup, val, 0);
		assertEquals("rX not equal-", gmm.rX, val, 0);
		assertEquals("vs30 not equal-", gmm.vs30, val, 0);
		assertEquals("vsInf not equal-", gmm.vsInf, bool);
		assertEquals("width not equal-", gmm.width, val, 0);
		assertEquals("z1p0 not equal-", gmm.z1p0, val, 0);
		assertEquals("z2p5 not equal-", gmm.z2p5, val, 0);
		assertEquals("zHyp not equal-", gmm.zHyp, val, 0);
		assertEquals("zTop not equal-", gmm.zTop, val, 0);
	}
	
  @Test
	public final void builderSetUnhandledField() {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Unhandled field: " + MW.toString());
		
		GmmInput.builder().set(MW, "true");
	}
  
	@Test
	public final void builderWithDefaults() {
		GmmInput gmm = GmmInput.builder().withDefaults().build();
		
		assertEquals("dip not equal-", gmm.dip, dip, 0);
		assertEquals("Mw not equal-", gmm.Mw, Mw, 0);
		assertEquals("rake not equal-", gmm.rake, rake, 0);
		assertEquals("rJB not equal-", gmm.rJB, rJB, 0);
		assertEquals("rRup not equal-", gmm.rRup, rRup, 0);
		assertEquals("rX not equal-", gmm.rX, rX, 0);
		assertEquals("vs30 not equal-", gmm.vs30, vs30, 0);
		assertEquals("vInf not equal-", gmm.vsInf, vsInf);
		assertEquals("width not equal-", gmm.width, width, 0);
		assertEquals("z1p0 not equal-", gmm.z1p0, z1p0, 0);
		assertEquals("z2p5 not equal-", gmm.z2p5, z2p5, 0);
		assertEquals("zHyp not equal-", gmm.zHyp, zHyp, 0);
		assertEquals("zTop not equal-", gmm.zTop, zTop, 0);
	}
	
	@Test 
	public final void fieldFromString() {
		Field mwField = Field.fromString("mw");
		
		assertEquals("Enums not equal-", MW, mwField);
		assertEquals("Values not equal-", MW.defaultValue, mwField.defaultValue, 0);
		assertEquals("IDs not equal-", MW.id, mwField.id);
		assertEquals("Info not equal-", MW.info, mwField.info);
		assertEquals("Labels not equal-", MW.label, mwField.label);
		assertEquals("Units not equal-", MW.units, mwField.units);
	}
	
	@Test
	public final void fieldFromStringIllegalArgument() {
		thrown.expect(IllegalArgumentException.class);
		
		Field.fromString("test");
	}
	
  @Test
	public final void gmmInput() {
		GmmInput gmm = new GmmInput(Mw, rJB, rRup, rX, dip, width,
				zTop, zHyp, rake, vs30, vsInf, z1p0, z2p5);
		
		assertEquals("dip not equal-", gmm.dip, dip, 0);
		assertEquals("Mw not equal-", gmm.Mw, Mw, 0);
		assertEquals("rake not equal-", gmm.rake, rake, 0);
		assertEquals("rJB not equal-", gmm.rJB, rJB, 0);
		assertEquals("rRup not equal-", gmm.rRup, rRup, 0);
		assertEquals("rX not equal-", gmm.rX, rX, 0);
		assertEquals("vs30 not equal-", gmm.vs30, vs30, 0);
		assertEquals("vInf not equal-", gmm.vsInf, vsInf);
		assertEquals("width not equal-", gmm.width, width, 0);
		assertEquals("z1p0 not equal-", gmm.z1p0, z1p0, 0);
		assertEquals("z2p5 not equal-", gmm.z2p5, z2p5, 0);
		assertEquals("zHyp not equal-", gmm.zHyp, zHyp, 0);
		assertEquals("zTop not equal-", gmm.zTop, zTop, 0);
	}
	
	@Test 
	public final void gmmInputEquals() {
		double v = 25.0;
		Builder gmmBuilder = GmmInput.builder();
		GmmInput gmm = gmmBuilder.withDefaults().build();
		GmmInput gmmCopy = GmmInput.builder().fromCopy(gmm).build();
			
		assertTrue(gmm.equals(gmmCopy));
		assertTrue(gmm.equals(gmm));
		assertFalse(gmm.equals(null));
		assertFalse(gmm.equals(new Object()));
		assertFalse("dip-", gmm.equals(gmmBuilder.withDefaults().dip(v).build()));
		assertFalse("Mw-", gmm.equals(gmmBuilder.withDefaults().mag(v).build()));
		assertFalse("rake-", gmm.equals(gmmBuilder.withDefaults().rake(v).build()));
		assertFalse("rJB-", gmm.equals(gmmBuilder.withDefaults().rJB(v).build()));
		assertFalse("rRup-", gmm.equals(gmmBuilder.withDefaults().rRup(v).build()));
		assertFalse("rX-", gmm.equals(gmmBuilder.withDefaults().rX(v).build()));
		assertFalse("vs30-", gmm.equals(gmmBuilder.withDefaults().vs30(v).build()));
		assertFalse("vsInf-", gmm.equals(gmmBuilder.withDefaults().vsInf(false).build()));
		assertFalse("width-", gmm.equals(gmmBuilder.withDefaults().width(v).build()));
		assertFalse("z1p0-", gmm.equals(gmmBuilder.withDefaults().z1p0(v).build()));
		assertFalse("z2p5-", gmm.equals(gmmBuilder.withDefaults().z2p5(v).build()));
		assertFalse("zHyp-", gmm.equals(gmmBuilder.withDefaults().zHyp(v).build()));
		assertFalse("zTop-", gmm.equals(gmmBuilder.withDefaults().zTop(v).build()));
	}	
	
  @Test 
	public final void gmmInputHashCode() {
		GmmInput gmm = GmmInput.builder().withDefaults().build();
		GmmInput gmmCopy = GmmInput.builder().fromCopy(gmm).build();
		GmmInput gmmDiff = GmmInput.builder().withDefaults()
				.mag(10.0).z1p0(10.0).z2p5(10.0).build();
		
		assertEquals("Not equal-", gmm.hashCode(), gmmCopy.hashCode());
		assertNotEquals("Equal-", gmm.hashCode(), gmmDiff.hashCode());
	}
  
  @Test
	public final void gmmInputToString() {
		GmmInput gmm = GmmInput.builder().withDefaults().build();
		GmmInput gmmCopy = GmmInput.builder().fromCopy(gmm).build();
		String toString = gmm.toString();
		String toStringCopy = gmmCopy.toString();
		
		assertTrue("Does not contain dip-", toString.contains("dip="));
		assertTrue("Does not contain Mw-", toString.contains("Mw="));
		assertTrue("Does not contain rake-", toString.contains("rake="));
		assertTrue("Does not contain rJB-", toString.contains("rJB="));
		assertTrue("Does not contain rRup-", toString.contains("rRup="));
		assertTrue("Does not contain rX-", toString.contains("rX="));
		assertTrue("Does not contain vs30-", toString.contains("vs30="));
		assertTrue("Does not contain vsInf-", toString.contains("vsInf="));
		assertTrue("Does not contain width-", toString.contains("width="));
		assertTrue("Does not contain z1p0-", toString.contains("z1p0="));
		assertTrue("Does not contain z2p5-", toString.contains("z2p5="));
		assertTrue("Does not contain zHyp-", toString.contains("zHyp="));
		assertTrue("Does not contain zTop-", toString.contains("zTop="));
		assertEquals("Strings not equal-", toString, toStringCopy);
	}
	
}