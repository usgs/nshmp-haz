package gov.usgs.earthquake.nshmp.internal;

import static gov.usgs.earthquake.nshmp.internal.NshmpSite.ADAK_AK;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.BOISE_ID;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.BRADSHAW_AIRFIELD_HI;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.CENTURY_CITY_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.CHARLESTON_SC;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.CHICAGO_IL;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.CONCORD_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.DIABLO_CANYON_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.EVERETT_WA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.HANFORD_SITE_WA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.IDAHO_NATIONAL_LAB_ID;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.IRVINE_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.LAS_VEGAS_NV;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.LONG_BEACH_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.LOS_ALAMOS_NATIONAL_LAB_NM;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.LOS_ANGELES_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.MCGRATH_AK;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.MEMPHIS_TN;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.MONTEREY_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.NEW_YORK_NY;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.NORTHRIDGE_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.OAKLAND_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.PALO_VERDE_AZ;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.PORTLAND_OR;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.PUUWAI_HI;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.RENO_NV;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.RIVERSIDE_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SACRAMENTO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SALT_LAKE_CITY_UT;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SANTA_BARBARA_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SANTA_CRUZ_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SANTA_ROSA_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_BERNARDINO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_DIEGO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_FRANCISCO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_JOSE_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_LUIS_OBISPO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_MATEO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_ONOFRE_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SEATTLE_WA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.ST_LOUIS_MO;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.TACOMA_WA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.VALLEJO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.VENTURA_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.WASHINGTON_DC;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.YAKUTAT_AK;
import static org.junit.Assert.assertEquals;

import java.util.Comparator;
import java.util.EnumSet;

import org.junit.Test;

import java.util.function.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.internal.NshmpSite.StateComparator;

@SuppressWarnings("javadoc")
public class NshmpSiteTests {

  @Test
  public final void methodsTest() {
    NshmpSite s = WASHINGTON_DC;
    assertEquals(s.state(), UsRegion.DC);
    assertEquals(s.location(), Location.create(38.90, -77.05));
    assertEquals(s.id(), s.name());
    assertEquals(s.toString(), "Washington DC");

    s = MCGRATH_AK;
    assertEquals(s.toString(), "McGrath AK");
  }

  @Test
  public final void groupsTest() {

    /* WUS */
    assertEquals(
        NshmpSite.wus(),
        Sets.newEnumSet(Iterables.filter(
            EnumSet.allOf(NshmpSite.class),
            new Predicate<NshmpSite>() {
              @Override
              public boolean apply(NshmpSite site) {
                return site.location().lon() <= -100.0 && site.location().lon() >= -125.0;
              }
            }), NshmpSite.class));

    /* CEUS */
    assertEquals(
        NshmpSite.ceus(),
        Sets.newEnumSet(Iterables.filter(
            EnumSet.allOf(NshmpSite.class),
            new Predicate<NshmpSite>() {
              @Override
              public boolean apply(NshmpSite site) {
                return site.location().lon() >= -115.0;
              }
            }), NshmpSite.class));

    /* Alaska */
    assertEquals(
        NshmpSite.alaska(),
        EnumSet.range(ADAK_AK, YAKUTAT_AK));

    /* Hawaii */
    assertEquals(
        NshmpSite.hawaii(),
        EnumSet.range(BRADSHAW_AIRFIELD_HI, PUUWAI_HI));

    /* NRC comparision sites */
    assertEquals(
        NshmpSite.nrc(),
        Sets.newEnumSet(Iterables.filter(
            EnumSet.allOf(NshmpSite.class),
            new Predicate<NshmpSite>() {
              @Override
              public boolean apply(NshmpSite site) {
                return site.location().lon() >= -105.5;
              }
            }), NshmpSite.class));

    /* DOE facilities */
    assertEquals(
        NshmpSite.facilities(),
        EnumSet.of(
            DIABLO_CANYON_CA,
            SAN_ONOFRE_CA,
            PALO_VERDE_AZ,
            HANFORD_SITE_WA,
            IDAHO_NATIONAL_LAB_ID,
            LOS_ALAMOS_NATIONAL_LAB_NM));

    /* NEHRP test cites */
    assertEquals(
        NshmpSite.nehrp(),
        EnumSet.of(
            // SoCal
            LOS_ANGELES_CA,
            CENTURY_CITY_CA,
            NORTHRIDGE_CA,
            LONG_BEACH_CA,
            IRVINE_CA,
            RIVERSIDE_CA,
            SAN_BERNARDINO_CA,
            SAN_LUIS_OBISPO_CA,
            SAN_DIEGO_CA,
            SANTA_BARBARA_CA,
            VENTURA_CA,
            // NoCal
            OAKLAND_CA,
            CONCORD_CA,
            MONTEREY_CA,
            SACRAMENTO_CA,
            SAN_FRANCISCO_CA,
            SAN_MATEO_CA,
            SAN_JOSE_CA,
            SANTA_CRUZ_CA,
            VALLEJO_CA,
            SANTA_ROSA_CA,
            // PNW
            SEATTLE_WA,
            TACOMA_WA,
            EVERETT_WA,
            PORTLAND_OR,
            // B&R
            SALT_LAKE_CITY_UT,
            BOISE_ID,
            RENO_NV,
            LAS_VEGAS_NV,
            // CEUS
            ST_LOUIS_MO,
            MEMPHIS_TN,
            CHARLESTON_SC,
            CHICAGO_IL,
            NEW_YORK_NY));
  }

  @Test
  public final void comparatorTest() {
    Comparator<NshmpSite> c = new StateComparator();
    assertEquals(c.compare(CHICAGO_IL, LOS_ANGELES_CA), 1);
    assertEquals(c.compare(LOS_ANGELES_CA, SAN_FRANCISCO_CA), -1);
  }

}
