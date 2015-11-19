%% nshmp-haz Ground Motion Model (GMM) explanatory script

% =========================================================================
% This script provides instruction on how to access ground motion models
% implemented in the nshmp-haz library. The models are written in Java and
% require little more than to be identified to Matlab to function properly.
%
% See the README for instructions on how to set up Matlab to use nshmp-haz.
% =========================================================================

% (Optional)
% Import classes (or whole packages using wildcards) to reduce verbosity:
import org.opensha2.gmm.*

% =========================================================================
% Single model ground motion calculation:

% Set up an input object. These data are a source and site parameterization
% that will satisfy all currently implemented ground motion models. Not all
% models will use all parameters.

Mw    =   6.5; % moment magnitude
rJB   =   5.0; % Joyner-Boore distance
rRup  =   5.1; % distance to closest point on rupture surface
rX    =   5.1; % distance from source trace; hanging (+); foot (-) wall
dip   =  90.0; % in degrees
width =  10.0; % in km
zTop  =   1.0; % in km
zHyp  =   6.0; % in km
rake  =   0.0; % in degrees
vs30  = 760.0; % in m/s
vsInf =  true; % boolean
z2p5  =   NaN; % in km; NaN triggers default basin depth model
z1p0  =   NaN; % in km; NaN triggers default basin depth model

% At present, all parameters, whether required or not, must be set. This is
% accomplished using a builder. While perhaps verbose, a builder ensures
% that all parameters are, in fact, set and provides greater argument
% specificity (i.e. it is very easy to supply out-of-order numeric
% arguments when many are required).
b = GmmInput.builder();
b.mag(Mw);
b.rJB(rJB);
b.rRup(rRup);
b.rX(rX); 
b.dip(dip);
b.width(width);
b.zTop(zTop);
b.zHyp(zHyp);
b.rake(rake);
b.vs30(vs30);
b.vsInf(vsInf);
b.z1p0(z1p0);
b.z2p5(z2p5);
gmmInput = b.build()

% However, a builder is reusable. Now that 'b' has been fully initialized,
% single parameter can be updated and a new input object created. 
b.mag(7.0);
gmmInput = b.build()

% As a shortcut to specifying all parameters, one can also start with a
% fully initialized builder.
b.withDefaults();
gmmInput = b.build()

% Set a ground motion model. Ground motion model identifiers may by found
% in the javadocs accompanying the java source:
% http://usgs.github.io/nshmp-haz/javadoc/org/opensha2/gmm/Gmm.html
gmm = Gmm.ASK_14;

% For information on which parameters a model requires and their
% recommended ranges, use:
gmm.constraints()

% Set an intensity measure type. Ground motion modelIntensity measure
% identifiers may by found in the javadocs accompanying the java source:
% http://usgs.github.io/nshmp-haz/javadoc/org/opensha2/gmm/Imt.html
imt = Imt.PGA;

% Do a calculation. The MatUtil.calc(gmm, imt, gmmInput) method returns an
% array of [ln(median ground motion), sigma]
calcResult = MatUtil.calc(gmm, imt, gmmInput)

% =========================================================================
% Determinisitic response spectrum calculation:

% The object returned by the MatUtil.spectrum(gmm, gmmInput) method may
% be dumped into a struct.
spectrumResult = struct(MatUtil.spectrum(gmm, gmmInput))

% =========================================================================
