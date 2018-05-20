package gov.usgs.earthquake.nshmp.internal;

import java.util.function.Predicate;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.util.Comparator;
import java.util.EnumSet;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.util.NamedLocation;

/**
 * Locations that are used for NSHMP hazard comparisons.
 *
 * @author Allison Shumway
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum NshmpSite implements NamedLocation {

  /*
   * Items are organized broadly by state and states are grouped as belonging
   * either to northern or southern California, the intermountain west, the
   * Pacific northwest, of the central and eastern US.
   */

  /* Northern CA (16) */
  BIG_SUR_CA(-121.75, 36.25),
  COALINGA_CA(-120.40, 36.15),
  CONCORD_CA(-122.00, 37.95),
  EUREKA_CA(-124.20, 40.80),
  FRESNO_CA(-119.75, 36.75),
  MONTEREY_CA(-121.90, 36.60),
  MORGAN_HILL_CA(-121.65, 37.15),
  OAKLAND_CA(-122.25, 37.80),
  REDDING_CA(-122.40, 40.60),
  SACRAMENTO_CA(-121.50, 38.60),
  SAN_FRANCISCO_CA(-122.40, 37.75),
  SAN_JOSE_CA(-121.90, 37.35),
  SAN_MATEO_CA(-122.30, 37.55),
  SANTA_CRUZ_CA(-122.05, 36.95),
  SANTA_ROSA_CA(-122.70, 38.45),
  VALLEJO_CA(-122.25, 38.10),

  /* Southern CA (22) */
  BAKERSFIELD_CA(-119.35, 35.35),
  BRAWLEY_CA(-115.55, 33.00),
  CENTURY_CITY_CA(-118.40, 34.05),
  CUCAMONGA_CA(-117.55, 34.20),
  DEATH_VALLEY_CA(-116.85, 36.35),
  DIABLO_CANYON_CA(-120.85, 35.20),
  IRVINE_CA(-117.80, 33.65),
  LONG_BEACH_CA(-118.20, 33.80),
  LOS_ANGELES_CA(-118.25, 34.05),
  MALIBU_WEST_CA(-118.95, 34.05),
  MAMMOTH_LAKES_CA(-119.00, 37.65),
  NORTHRIDGE_CA(-118.55, 34.20),
  PALMDALE_CA(-118.00, 34.50),
  PALM_SPRINGS_CA(-116.55, 33.85),
  PASADENA_CA(-118.15, 34.15),
  RIVERSIDE_CA(-117.40, 33.95),
  SAN_BERNARDINO_CA(-117.30, 34.10),
  SAN_DIEGO_CA(-117.15, 32.70),
  SAN_LUIS_OBISPO_CA(-120.65, 35.30),
  SAN_ONOFRE_CA(-117.55, 33.40),
  SANTA_BARBARA_CA(-119.70, 34.45),
  VENTURA_CA(-119.30, 34.30),

  /* WUS excepting CA (32) */
  GRAND_CANYON_VILLAGE_AZ(-112.15, 36.05),
  PALO_VERDE_AZ(-112.85, 33.40),
  PHOENIX_AZ(-112.10, 33.45),
  TUCSON_AZ(-110.95, 32.20),

  DENVER_CO(-105.00, 39.75),
  LA_JUNTA_CO(-103.55, 38.00),
  PARADOX_CO(-108.95, 38.40),
  RANGELY_CO(-108.80, 40.10),
  TRINIDAD_CO(-104.50, 37.20),

  BOISE_ID(-116.20, 43.60),
  IDAHO_NATIONAL_LAB_ID(-112.85, 43.60),

  LIMA_MT(-112.60, 44.65),
  BOZEMAN_MT(-111.05, 45.70),
  BILLINGS_MT(-108.50, 45.80),
  MISSOULA_MT(-114.00, 46.90),

  CARSON_CITY_NV(-119.75, 39.15),
  ELKO_NV(-115.75, 40.85),
  LAS_VEGAS_NV(-115.15, 36.20),
  RENO_NV(-119.80, 39.55),

  ALBUQUERQUE_NM(-106.60, 35.10),
  ARTESIA_NM(-104.40, 32.85),
  LOS_ALAMOS_NATIONAL_LAB_NM(-106.30, 35.85),
  LAS_CRUCES_NM(-106.75, 32.30),

  BRIGHAM_CITY_UT(-112.00, 41.50),
  CEDAR_CITY_UT(-113.05, 37.70),
  GREEN_RIVER_UT(-110.15, 39.00),
  PROVO_UT(-111.65, 40.25),
  SALT_LAKE_CITY_UT(-111.90, 40.75),

  CASPER_WY(-106.30, 42.85),
  CHEYENNE_WY(-104.80, 41.15),
  JACKSON_WY(-110.75, 43.50),
  YELLOWSTONE_WY(-110.55, 44.40),

  /* PNW (21) */
  ASTORIA_OR(-123.85, 46.20),
  BEND_OR(-121.30, 44.05),
  BROOKINGS_OR(-124.25, 42.05),
  COOS_BAY_OR(-124.20, 43.40),
  EUGENE_OR(-123.10, 44.05),
  KLAMATH_FALLS_OR(-121.80, 42.20),
  MEDFORD_OR(-122.90, 42.35),
  NEWPORT_OR(-124.05, 44.65),
  PENDLETON_OR(-118.80, 45.70),
  PORTLAND_OR(-122.65, 45.50),
  SALEM_OR(-123.05, 44.95),

  ABERDEEN_WA(-123.80, 47.00),
  BELLINGHAM_WA(-122.50, 48.75),
  ELLENSBURG_WA(-120.55, 47.00),
  EVERETT_WA(-122.20, 48.00),
  HANFORD_SITE_WA(-119.60, 46.55),
  OLYMPIA_WA(-122.90, 47.05),
  SEATTLE_WA(-122.30, 47.60),
  SPOKANE_WA(-117.40, 47.65),
  TACOMA_WA(-122.45, 47.25),
  YAKIMA_WA(-120.50, 46.60),

  /* CEUS (68) */
  WASHINGTON_DC(-77.05, 38.90),
  ATMORE_AL(-87.50, 31.00),
  BIRMINGHAM_AL(-86.80, 33.50),
  EL_DORADO_AR(-92.70, 33.20),
  GREENBRIER_AR(-92.40, 35.20),
  LITTLE_ROCK_AR(-92.30, 34.75),
  HARTFORD_CT(-72.70, 41.75),
  WILMINGTON_DE(-75.55, 39.75),
  JACKSONVILLE_FL(-81.65, 30.35),
  MIAMI_FL(-80.20, 25.75),
  ATLANTA_GA(-84.40, 33.75),
  LINCOLNTON_GA(-82.50, 33.80),
  SAVANNAH_GA(-81.10, 32.10),
  CENTRAL_IL(-90.00, 40.00),
  CHICAGO_IL(-87.65, 41.85),
  EVANSVILLE_IN(-87.60, 38.00),
  INDIANAPOLIS_IN(-86.15, 39.80),
  DES_MOINES_IA(-93.60, 41.60),
  TOPEKA_KS(-95.70, 39.05),
  WICHITA_KS(-97.35, 37.70),
  LOUISVILLE_KY(-85.75, 38.25),
  NEW_ORLEANS_LA(-90.05, 29.95),
  AUGUSTA_ME(-69.80, 44.30),
  BANGOR_ME(-68.80, 44.80),
  PORTLAND_ME(-70.25, 43.65),
  BALTIMORE_MD(-76.60, 39.30),
  BOSTON_MA(-71.05, 42.35),
  DETROIT_MI(-83.05, 42.35),
  MINNEAPOLIS_MN(-93.30, 45.00),
  JACKSON_MS(-90.20, 32.30),
  CAPE_GIRARDEAU_MO(-89.50, 37.30),
  NEW_MADRID_MO(-89.55, 36.60),
  ST_LOUIS_MO(-90.20, 38.60),
  OMAHA_NE(-96.00, 41.25),
  MANCHESTER_NH(-71.45, 43.00),
  TRENTON_NJ(-74.75, 40.20),
  BATAVIA_NY(-78.20, 43.00),
  MALONE_NY(-74.30, 44.85),
  NEW_YORK_NY(-74.00, 40.75),
  CHARLOTTE_NC(-80.85, 35.25),
  FARGO_ND(-96.80, 46.90),
  COLUMBUS_OH(-83.00, 39.95),
  SIDNEY_OH(-84.15, 40.30),
  YOUNGSTOWN_OH(-80.65, 41.10),
  ELGIN_OK(-98.30, 34.80),
  OKLAHOMA_CITY_OK(-97.50, 35.50),
  PHILADELPHIA_PA(-75.15, 39.95),
  PITTSBURG_PA(-80.00, 40.45),
  PROVIDENCE_RI(-71.40, 41.80),
  CHARLESTON_SC(-79.95, 32.80),
  EDGEMONT_SD(-103.85, 43.30),
  PLATTE_SD(-98.85, 43.40),
  SIOUX_FALLS_SD(-96.75, 43.55),
  CHATTANOOGA_TN(-85.25, 35.05),
  KNOXVILLE_TN(-83.90, 35.95),
  MARYVILLE_TN(-84.00, 35.75),
  MEMPHIS_TN(-90.05, 35.15),
  AMARILLO_TX(-101.85, 35.20),
  DALLAS_TX(-96.80, 32.80),
  HOUSTON_TX(-95.35, 29.75),
  KERMIT_TX(-103.10, 31.85),
  SAN_ANTONIO_TX(-98.50, 29.40),
  SNYDER_TX(-100.90, 32.70),
  BURLINGTON_VT(-73.20, 44.50),
  BLACKSBURG_VA(-80.40, 37.25),
  RICHMOND_VA(-77.45, 37.55),
  CHARLESTON_WV(-81.65, 38.35),
  MILWAUKEE_WI(-87.90, 43.05),

  /* Alaska (26) */
  ADAK_AK(-176.65, 51.9),
  ANCHORAGE_AK(-149.90, 61.2),
  BARROW_AK(-156.75, 71.3),
  BETHEL_AK(-161.80, 60.8),
  DELTA_JUNCTION_AK(-145.70, 64),
  DILLINGHAM_AK(-158.45, 59.05),
  DUTCH_HARBOR_AK(-166.55, 53.9),
  EVANSVILLE_AK(-151.50, 66.9),
  FAIRBANKS_AK(-147.70, 64.85),
  GLENNALLEN_AK(-145.55, 62.1),
  HAINES_AK(-135.45, 59.25),
  HOMER_AK(-151.50, 59.65),
  JUNEAU_AK(-134.40, 58.3),
  KENAI_AK(-151.25, 60.55),
  KETCHIKAN_AK(-131.65, 55.35),
  KODIAK_AK(-152.40, 57.8),
  KOTZEBUE_AK(-162.60, 66.9),
  MCGRATH_AK(-155.60, 62.95),
  NOME_AK(-165.40, 64.5),
  PAXSON_AK(-145.50, 63.05),
  PRUDHOE_BAY_AK(-148.35, 70.25),
  SITKA_AK(-135.35, 57.05),
  TOK_AK(-143.00, 63.3),
  VALDEZ_AK(-146.35, 61.15),
  WASILLA_AK(-149.45, 61.6),
  YAKUTAT_AK(-139.70, 59.55),

  /* Hawaii (26) */
  BRADSHAW_AIRFIELD_HI(-155.55, 19.75), // Hawai'i
  HILO_HI(-155.05, 19.7),
  KAILUA_KONA_HI(-156, 19.65),
  KILAUEA_HI(-155.25, 19.4),
  MAUNA_KEA_HI(-155.45, 19.8),
  OCEAN_VIEW_HI(-155.75, 19.1),
  WAIMEA_HI(-155.7, 20),
  KAHEAWA_WIND_HI(-156.55, 20.8), // Maui
  KAHULUI_HI(-156.5, 20.9),
  HALEAKALA_CRATER_HI(-156.25, 20.70),
  LANAI_CITY_HI(-156.95, 20.8), // Lanai
  KAUNAKAKAI_HI(-157, 21.1), // Moloka'i
  BARBERS_POINT_HI(-158.1, 21.3), // O'ahu
  DIAMOND_HEAD_HI(-157.8, 21.25),
  HONOLULU_HI(-157.85, 21.3),
  KANEOHE_HI(-157.8, 21.4),
  LAIE_HI(-157.95, 21.65),
  MARINE_CORPS_BASE_HI(-157.75, 21.45),
  PEARL_HARBOR_HI(-157.95, 21.35),
  WAHIAWA_HI(-158, 21.5),
  WAIANAE_HI(-158.2, 21.45),
  WAIPAHU_HI(-158, 21.4),
  BARKING_SANDS_HI(-159.75, 22.05), // Kauai
  HANAPEPE_HI(-159.6, 21.9),
  LIHUE_HI(-159.35, 21.95),
  PUUWAI_HI(-160.2, 21.9); // Ni'ihau

  private final Location location;
  private final UsRegion state;

  private NshmpSite(double lon, double lat) {
    this.location = Location.create(lat, lon);
    this.state = UsRegion.valueOf(name().substring(name().lastIndexOf('_') + 1));
  }

  /**
   * The state containing this location.
   */
  public UsRegion state() {
    return state;
  }

  @Override
  public Location location() {
    return location;
  }

  @Override
  public String id() {
    return this.name();
  }

  @Override
  public String toString() {
    String label = Parsing.enumLabelWithSpaces(this, true);
    if (label.startsWith("Mc")) {
      StringBuilder sb = new StringBuilder(label);
      sb.setCharAt(2, Character.toUpperCase(sb.charAt(2)));
      label = sb.toString();
    }
    int stripIndex = label.lastIndexOf(' ');
    return label.substring(0, stripIndex) + " " + state.name();
  }

  /**
   * The set of sites used to test the Central &amp; Eastern US NSHM. This
   * includes all NSHMP sites east of -115.0°.
   */
  public static EnumSet<NshmpSite> ceus() {
    return Sets.newEnumSet(Iterables.filter(
        EnumSet.allOf(NshmpSite.class),
        new Predicate<NshmpSite>() {
          @Override
          public boolean test(NshmpSite site) {
            return site.location.lon() >= -115.0;
          }
        }::test), NshmpSite.class);
  }

  /**
   * The set of sites used to test the Western US NSHM. This includes all NSHMP
   * sites west of -100.0°.
   */
  public static EnumSet<NshmpSite> wus() {
    return Sets.newEnumSet(Iterables.filter(
        EnumSet.allOf(NshmpSite.class),
        new Predicate<NshmpSite>() {
          @Override
          public boolean test(NshmpSite site) {
            return site.location.lon() <= -100.0 && site.location.lon() >= -125.0;
          }
        }::test), NshmpSite.class);
  }

  /**
   * The combination of CEUS and WUS
   */
  public static EnumSet<NshmpSite> cous() {
    EnumSet<NshmpSite> cous = wus();
    cous.addAll(ceus());
    return cous;
  }

  /**
   * The set of sites used to test the Alaska NSHM.
   */
  public static EnumSet<NshmpSite> alaska() {
    return Sets.newEnumSet(Iterables.filter(
        EnumSet.allOf(NshmpSite.class),
        new Predicate<NshmpSite>() {
          @Override
          public boolean test(NshmpSite site) {
            return site.state == UsRegion.AK;
          }
        }::test), NshmpSite.class);
  }

  /**
   * The set of sites used to test the Hawaii NSHM.
   */
  public static EnumSet<NshmpSite> hawaii() {
    return Sets.newEnumSet(Iterables.filter(
        EnumSet.allOf(NshmpSite.class),
        new Predicate<NshmpSite>() {
          @Override
          public boolean test(NshmpSite site) {
            return site.state == UsRegion.HI;
          }
        }::test), NshmpSite.class);
  }

  /**
   * The set of sites corresponding to U.S. national labs and other Dept. of
   * Energy facilities.
   */
  public static EnumSet<NshmpSite> facilities() {
    return EnumSet.of(
        DIABLO_CANYON_CA,
        SAN_ONOFRE_CA,
        PALO_VERDE_AZ,
        HANFORD_SITE_WA,
        IDAHO_NATIONAL_LAB_ID,
        LOS_ALAMOS_NATIONAL_LAB_NM);
  }

  /**
   * A restricted set of CEUS sites that is clipped at -105.5°.
   */
  public static EnumSet<NshmpSite> nrc() {
    return Sets.newEnumSet(Iterables.filter(
        EnumSet.allOf(NshmpSite.class),
        new Predicate<NshmpSite>() {
          @Override
          public boolean test(NshmpSite site) {
            return site.location.lon() >= -105.5;
          }
        }::test), NshmpSite.class);
  }

  /**
   * The set of sites corresponding to the NEHRP test cities.
   *
   * <p>This is a list of 34 city sites in the United States with high seismic
   * risk as specified in the 2009 edition of the <a
   * href="http://www.fema.gov/library/viewRecord.do?id=4103"
   * target=_blank">NEHRP Recommended Seismic Provisions</a>.
   */
  public static EnumSet<NshmpSite> nehrp() {
    return EnumSet.of(
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
        NEW_YORK_NY);
  }

  static class StateComparator implements Comparator<NshmpSite> {
    @Override
    public int compare(NshmpSite s1, NshmpSite s2) {
      return ComparisonChain.start()
          .compare(s1.state.name(), s2.state.name())
          .compare(s1.name(), s2.name())
          .result();
    }
  }

}
