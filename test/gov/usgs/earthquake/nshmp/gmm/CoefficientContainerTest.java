package gov.usgs.earthquake.nshmp.gmm;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class CoefficientContainerTest {

  @Test(expected = NumberFormatException.class)
  public void testParseImt1() {
    System.out.println(CoefficientContainer.parseImt("garbage"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParseImt2() {
    System.out.println(CoefficientContainer.parseImt("0.1234"));
  }

}
