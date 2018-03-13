#!/usr/bin/env python
## nshmp-haz Ground Motion Model (GMM) calculator example script

# =========================================================================
# This script provides instruction on how to access ground motion models
# (GMMs) implemented in the nshmp-haz library.
# =========================================================================

from jpype import *
import numpy as np

# Specify path to nshmp-haz library:
classpath = '/path/to/repository/nshmp-haz-master/build/libs/nshmp-haz.jar'

# Start Java Virtual Machine and add nshmp-haz to classpath:
startJVM(getDefaultJVMPath(), "-ea", 
         "-Djava.class.path={0}".format(classpath))

# Import packages:
nshmp = JPackage("gov").usgs.earthquake.nshmp.etc

# =========================================================================
# Single ground motion calcuation:

# Initialize calculator:
hazMat = nshmp.HazMat.init(classpath)

# Note that hazMat is stateless and reusable and should therefore be
# initialized only once in a script if doing many calculations.

# Set up a GMM input parameter object. These data are a source and site
# parameterization that will satisfy all currently implemented Gmms. Note
# that not all models will necessarily use all parameters.    
gmmparams = nshmp.GmmParams()
gmmparams.Mw = 6.5
gmmparams.rJB = 5.0
gmmparams.rRup = 5.1
gmmparams.rX = 5.1
gmmparams.dip = 90.0
gmmparams.width = 10.0
gmmparams.zTop = 1.0
gmmparams.zHyp = 6.0
gmmparams.rake = 0.0
gmmparams.vs30 = 760.
gmmparams.vsInf = True
gmmparams.z2p5 = np.nan
gmmparams.z1p0 = np.nan

# Specify a ground motion model. GMM identifiers:
# http://usgs.github.io/nshmp-haz/javadoc/gov/usgs/earthquake/nshmp/gmm/Gmm.html
gmm = 'ASK_14';

# Specify an intensity measure type (IMT). IMT identifiers:
# http://usgs.github.io/nshmp-haz/javadoc/gov/usgs/earthquake/nshmp/gmm/Imt.html
imt = 'PGA';

# Do a calculation. The MatUtil.calc(gmm, imt, gmmInput) method returns an
# array of [ln(median ground motion), sigma]
ln_med_gm, sigma = hazMat.gmmMean(gmm, imt, gmmparams)

print('ln(median ground motion), sigma:')
print(ln_med_gm, sigma)

# =========================================================================
# Determinisitic response spectrum calculation:

# The object returned by the MatUtil.spectrum(gmm, gmmInput) method may
# be converted to NumPy arrays.
# The returned HazMat Spectrum object is not iterable, so do this array 
# by array.
spectrumResult = hazMat.gmmSpectrum(gmm, gmmparams)
pds = np.array(spectrumResult.periods)
means = np.array(spectrumResult.means)
sigmas = np.array(spectrumResult.sigmas)
print('period, mean, sigma:')
for i in range(len(pds)):
    print(pds[i], means[i], sigmas[i])
# =========================================================================
