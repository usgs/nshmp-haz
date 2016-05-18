package org.opensha2.util;

import java.util.EnumSet;
import java.util.Set;

/**
 * Identifiers for U.S. states, populated territories, and federal districts.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum UsRegion {

  AK("Alaska", "Juneau"),
  AL("Alabama", "Montgomery"),
  AR("Arkansas", "Little Rock"),
  AZ("Arizona", "Phoenix"),
  CA("California", "Sacramento"),
  CO("Colorado", "Denver"),
  CT("Connecticut", "Hartford"),
  DE("Delaware", "Dover"),
  FL("Florida", "Tallahassee"),
  GA("Georgia", "Atlanta"),
  HI("Hawaii", "Honolulu"),
  IA("Iowa", "Des Moines"),
  ID("Idaho", "Boise"),
  IL("Illinois", "Springfield"),
  IN("Indiana", "Indianapolis"),
  KS("Kansas", "Topeka"),
  KY("Kentucky", "Frankfort"),
  LA("Louisiana", "Baton Rouge"),
  MA("Massachusetts", "Boston"),
  MD("Maryland", "Annapolis"),
  ME("Maine", "Augusta"),
  MI("Michigan", "Lansing"),
  MN("Minnesota", "St. Paul"),
  MO("Missouri", "Jefferson City"),
  MS("Mississippi", "Jackson"),
  MT("Montana", "Helena"),
  NC("North Carolina", "Raleigh"),
  ND("North Dakota", "Bismarck"),
  NE("Nebraska", "Lincoln"),
  NH("New Hampshire", "Concord"),
  NJ("New Jersey", "Trenton"),
  NM("New Mexico", "Santa Fe"),
  NV("Nevada", "Carson City"),
  NY("New York", "Albany"),
  OH("Ohio", "Columbus"),
  OK("Oklahoma", "Oklahoma City"),
  OR("Oregon", "Salem"),
  PA("Pennsylvania", "Harrisburg"),
  RI("Rhode Island", "Providence"),
  SC("South Carolina", "Columbia"),
  SD("South Dakota", "Pierre"),
  TN("Tennessee", "Nashville"),
  TX("Texas", "Austin"),
  UT("Utah", "Salt Lake City"),
  VA("Virginia", "Richmond"),
  VT("Vermont", "Montpelier"),
  WA("Washington", "Olympia"),
  WI("Wisconsin", "Madison"),
  WV("West Virginia", "Charleston"),
  WY("Wyoming", "Cheyenne"),

  DC("District of Columbia", "District of Columbia"),

  AS("American Samoa", "Pago Pago"),
  GU("Guam", "Hagåtña"),
  MP("Northern Mariana Islands", "Saipan"),
  PR("Puerto Rico", "San Juan"),
  VI("U.S. Virgin Islands Charlotte", "Amali");

  private final String label;
  private final String capitol;

  private UsRegion(String label, String capitol) {
    this.label = label;
    this.capitol = capitol;
  }

  @Override
  public String toString() {
    return label;
  }

  /**
   * Returns the capitol of this state or territory. Washington D.C. returns
   * itself.
   */
  public String capitol() {
    return capitol;
  }

  /**
   * Returns the set of U.S. federal districts. This set currently this only
   * includes Washington D.C.
   */
  public static Set<UsRegion> districts() {
    return EnumSet.of(DC);
  }

  /**
   * Returns the set of populated U.S. territories.
   */
  public static EnumSet<UsRegion> territories() {
    return EnumSet.of(AS, GU, MP, PR, VI);
  }

  /**
   * Returns the set of 50 U.S. states.
   */
  public static EnumSet<UsRegion> states() {
    EnumSet<UsRegion> notStates = territories();
    notStates.add(DC);
    return EnumSet.complementOf(notStates);
  }

  /**
   * Returns the states making up the 'lower 48', including Washington D.C.
   */
  public static EnumSet<UsRegion> conterminous() {
    EnumSet<UsRegion> lower48 = states();
    lower48.removeAll(EnumSet.of(AK, HI));
    lower48.add(DC);
    return lower48;
  }

}
