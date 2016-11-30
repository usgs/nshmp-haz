%% nshmp-haz Ground Motion Model (GMM) calculator example script

% =========================================================================
% This script provides instruction on how to access ground motion models
% (GMMs) implemented in the nshmp-haz library.
%
% See the README for instructions on how to set up Matlab to use nshmp-haz.
% =========================================================================

clear

% Specify path to nshmp-haz library:
% nshmpHaz = '/path/to/repository/nshmp-haz/dist/nshmp-haz.jar';
nshmpHaz = '/Users/pmpowers/projects/git/nshmp-haz/dist/nshmp-haz.jar';

% Make Matlab aware of nshmp-haz by adding it to the 'dynamic' classpath:
javaaddpath(nshmpHaz);

% Alternatively, one can add nshmp-haz to the faster 'static' classpath by
% saving a file with the name `javaclasspath.txt` to the Matlab preferences
% directory, as specified by the `prefdir` command, and with single line:
%
%     /path/to/repository/nshmp-haz/dist/nshmp-haz.jar
%
% Although the static classpath is generally a little faster, you must
% restart Matlab any time nshmp-haz.jar is rebuilt with updated code.

import org.opensha2.etc.*

% =========================================================================
% Single model ground motion calculation:

% Initialize calculator:
hazMat = HazMat.init(nshmpHaz);

% Note that hazMat is stateless and reusable and should therefore be
% initialized external to this script if doing many calculations.

% Set up a GMM input parameter object. These data are a source and site
% parameterization that will satisfy all currently implemented Gmms. Note
% that not all models will necessarily use all parameters.
input = GmmParams();
input.Mw    =   6.5; % moment magnitude
input.rJB   =   5.0; % Joyner-Boore distance
input.rRup  =   5.1; % distance to closest point on rupture surface
input.rX    =   5.1; % distance from source trace; hanging (+); foot (-) wall
input.dip   =  90.0; % in degrees
input.width =  10.0; % in km
input.zTop  =   1.0; % in km
input.zHyp  =   6.0; % in km
input.rake  =   0.0; % in degrees
input.vs30  = 760.0; % in m/s
input.vsInf =  true; % boolean
input.z2p5  =   NaN; % in km; NaN triggers default basin depth model
input.z1p0  =   NaN; % in km; NaN triggers default basin depth model

% Specify a ground motion model. GMM identifiers:
% http://usgs.github.io/nshmp-haz/javadoc/org/opensha2/gmm/Gmm.html
gmm = 'ASK_14';

% Specify an intensity measure type (IMT). IMT identifiers:
% http://usgs.github.io/nshmp-haz/javadoc/org/opensha2/gmm/Imt.html
imt = 'PGA';

% Do a calculation. The MatUtil.calc(gmm, imt, gmmInput) method returns an
% array of [ln(median ground motion), sigma]
result = hazMat.gmmMean(gmm, imt, input)

% =========================================================================
% Determinisitic response spectrum calculation:

% The object returned by the MatUtil.spectrum(gmm, gmmInput) method may
% be dumped into a struct.
spectrumResult = struct(hazMat.gmmSpectrum(gmm, input))
% =========================================================================
