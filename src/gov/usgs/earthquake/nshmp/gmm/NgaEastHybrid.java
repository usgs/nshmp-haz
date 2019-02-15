package gov.usgs.earthquake.nshmp.gmm;

import gov.usgs.earthquake.nshmp.gmm.NgaEastUsgs_2017.SiteAmp;
import gov.usgs.earthquake.nshmp.gmm.NgaEastUsgs_2017.SiteAmp.Value;

/**
 * Wrapper class for GMM instances that mix CEUS 2014 GMMs and NGA-East site and
 * sigma values.
 * 
 * @author pmpowers
 */
class NgaEastHybrid {

  private static final String NAME_SIGMA = "NGA-East sigma : ";
  private static final String NAME_SITE = "NGA-East site : ";

  /*
   * 2014 CEUS GMM containters that mix in NGA-East site terms and sigmas for
   * comparative analysis.
   * 
   * Implementation notes:
   * 
   * Sigma models only support Vs30=760 and 2000.
   * 
   * Site models support all Vs30s. However, to make comparisons of consistency
   * to the original CEUS 2014 implementation requires separate windows in the
   * reponse spectra application. The GMMs with NGA-East site effects should be
   * computed at Vs30=3000 and the originals at Vs30=2000 to demonstrate that
   * they are identical when no site effect is applicable.
   * 
   * Each swap class extends NGAEast due to sigma and site imt-dependent
   * instance method calls.
   */
  static abstract class SigmaSwap extends NgaEastUsgs_2017 {

    final GroundMotionModel gmm;

    private SigmaSwap(Gmm gmm, Imt imt) {
      super(imt);
      this.gmm = gmm.instance(imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      return new DefaultScalarGroundMotion(
          gmm.calc(in).mean(),
          sigmaLogicTree(in.Mw, in.vs30));
    }

    static final class AB06p extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "AB06p";

      AB06p(Imt imt) {
        super(Gmm.AB_06_PRIME, imt);
      }
    }

    static final class A08p extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "A08p";

      A08p(Imt imt) {
        super(Gmm.ATKINSON_08_PRIME, imt);
      }
    }

    static final class Camp03 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "Camp03";

      Camp03(Imt imt) {
        super(Gmm.CAMPBELL_03, imt);
      }
    }

    static final class Fea96 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "FEA96";

      Fea96(Imt imt) {
        super(Gmm.FRANKEL_96, imt);
      }
    }

    static final class Pezeshk11 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "P11";

      Pezeshk11(Imt imt) {
        super(Gmm.PEZESHK_11, imt);
      }
    }

    static final class Silva02 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "Silva02";

      Silva02(Imt imt) {
        super(Gmm.SILVA_02, imt);
      }
    }

    static final class Somer01 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "Somer01";

      Somer01(Imt imt) {
        super(Gmm.SOMERVILLE_01, imt);
      }
    }

    static final class TP05 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "TP05";

      TP05(Imt imt) {
        super(Gmm.TP_05, imt);
      }
    }

    static final class Toro97 extends SigmaSwap {
      static final String NAME = NAME_SIGMA + "Toro97";

      Toro97(Imt imt) {
        super(Gmm.TORO_97_MW, imt);
      }
    }
  }

  /*
   * Extensions of the CEUS 2014 GMMs to support substitution of the NGA-East
   * siteamp model.
   */
  static abstract class SiteSwap extends NgaEastUsgs_2017 {

    final GroundMotionModel gmmImt;
    final GroundMotionModel gmmPga;
    final SiteAmp siteAmp;

    private SiteSwap(Gmm gmm, Imt imt) {
      super(imt);
      this.gmmImt = gmm.instance(imt);
      this.gmmPga = gmm.instance(Imt.PGA);
      siteAmp = new SiteAmp(imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {

      /* Hard rock input; vs30=2000. */
      GmmInput inRock = GmmInput.builder()
          .fromCopy(in)
          .vs30(2000.0)
          .build();

      /* Median pga rock ground motion for siteamp model. */
      double meanRockPga = gmmPga.calc(inRock).mean();
      Value siteTerm = siteAmp.calc(
          Math.exp(meanRockPga),
          in.vs30);

      /* CEUS14 sigmas are the same for hard and soft rock sites. */
      ScalarGroundMotion sgmRock = gmmImt.calc(inRock);
      return new DefaultScalarGroundMotion(
          siteTerm.apply(sgmRock.mean()),
          sgmRock.sigma());
    }

    static final class AB06p extends SiteSwap {
      static final String NAME = NAME_SITE + "AB06p";

      AB06p(Imt imt) {
        super(Gmm.AB_06_PRIME, imt);
      }
    }

    static final class A08p extends SiteSwap {
      static final String NAME = NAME_SITE + "A08p";

      A08p(Imt imt) {
        super(Gmm.ATKINSON_08_PRIME, imt);
      }
    }

    static final class Camp03 extends SiteSwap {
      static final String NAME = NAME_SITE + "Camp03";

      Camp03(Imt imt) {
        super(Gmm.CAMPBELL_03, imt);
      }
    }

    static final class Fea96 extends SiteSwap {
      static final String NAME = NAME_SITE + "FEA96";

      Fea96(Imt imt) {
        super(Gmm.FRANKEL_96, imt);
      }
    }

    static final class Pezeshk11 extends SiteSwap {
      static final String NAME = NAME_SITE + "P11";

      Pezeshk11(Imt imt) {
        super(Gmm.PEZESHK_11, imt);
      }
    }

    static final class Silva02 extends SiteSwap {
      static final String NAME = NAME_SITE + "Silva02";

      Silva02(Imt imt) {
        super(Gmm.SILVA_02, imt);
      }
    }

    static final class Somer01 extends SiteSwap {
      static final String NAME = NAME_SITE + "Somer01";

      Somer01(Imt imt) {
        super(Gmm.SOMERVILLE_01, imt);
      }
    }

    static final class TP05 extends SiteSwap {
      static final String NAME = NAME_SITE + "TP05";

      TP05(Imt imt) {
        super(Gmm.TP_05, imt);
      }
    }

    static final class Toro97 extends SiteSwap {
      static final String NAME = NAME_SITE + "Toro97";

      Toro97(Imt imt) {
        super(Gmm.TORO_97_MW, imt);
      }
    }

  }
}
