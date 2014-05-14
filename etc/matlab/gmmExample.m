%% nshmp-sha Ground Motion Model explanatory script

% =========================================================================
% This script describes how to access the ground motion models (GMMs)
% implemented in the nshmp-sha hazard codes. The models are written in
% Java and require little more than to be identified to Matlab to function
% properly.
% =========================================================================

% Set up:

% (Required) Have the nshmp-sha project cloned somewhere local. The
% instructions below that the project has also been compiled such
% that a 'classes' directory exsists.

% (Required) Have Matlab R2012B or higher

% (Required) Due to the numerous outdated Java libraries included with
% Matlab, one first needs save a 'javaclasspath.txt' file to the directory
% specified by the 'prefdir' command, that contains the following two
% lines (modify the path below for your system):
%
%     <before>
%     /PathToGitProjects/nshmp-sha/lib/guava-16.0.1.jar
%
% This will add the google guava library to the static classpath, which
% updates only when Matlab is restarted.

% (Optional) This line is only necessary if models are being actively
% modified and one wants to keep abreast of those changes in Matlab.
clear java

% (Required) Add the root of the nshmp-sha Java class heirarchy to the
% Matlab dynamic classpath; modify the path below for your system (1):
% javaaddpath('/PathToGitProjects/nshmp-sha/classes')
javaaddpath('/Users/pmpowers/projects/git/nshmp-sha/classes')

% (Optional) Import classes (2):
import org.opensha.gmm.*

% =========================================================================
% Single model ground motion calculation:

% Set up a source object. These data are a source parameterization that
% will satisfy all currently implemented ground motion models. Not all
% models will use all values.
Mw    =   6.5; % moment magnitude
rJB   =   5.0; % Joyner-Boore distance
rRup  =   5.1; % distance to closest point on rupture surface
rX    =   5.1; % distance from source trace; hanging wall (+); foot-wall (-)
dip   =  90.0; % in degrees
width =  10.0; % in km
zTop  =   1.0; % in km
zHyp  =   6.0; % in km
rake  =   0.0; % in dsegrees
vs30  = 760.0; % in m/s
vsInf =  true; % boolean
z2p5  =   NaN; % in km; NaN triggers default basin depth model
z1p0  =   NaN; % in km; NaN triggers default basin depth model

source = GMM_Source.create(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, ...
	rake, vs30, vsInf, z2p5, z1p0);

% Set ground motion model. Ground motion model identifiers may by found in
% the javadocs accompanying the java source.
gmm = GMM.ASK_14;

% Set an intensity measure type. Ground motion modelIntensity measure
% identifiers may by found in the javadocs accompanying the java source.
imt = IMT.PGA;

% Do a calculation. The MatUtil.calc(gmm, imt, source) method returns an
% array of [ln(median ground motion), sigma]
calcResult = MatUtil.calc(gmm, imt, source)

% =========================================================================
% Determinisitic response spectrum calculation:

% The object returned by the MatUtil.spectrum(gmm, source) method may
% conveniently be dumped into a struct.
spectrumResult = struct(MatUtil.spectrum(gmm, source))

% =========================================================================
% Notes:
%
%   1) This is the dynamic classpath and may be slower when making repeated
%      calls to the models. Alternatively, this and other paths may be
%      added to the 'javaclasspath.txt' file, however Matlab will have to
%      be restarted to see any changes made to linked Java classes.
%
%   2) Java organizes code into packages; one can think of these as folders
%      for the time being, however nested packages are separated with dots.
%      Now that Matlab knows where to look for classes, calls to the
%      classes themselves must include the full package declaration (e.g.
%      org.opensha.gmm.GMM_Source). To cut down on verbosity, one may
%      'import' a specific class for use in a script:
%
%         import org.opensha.gmm.GMM_Source
%
%      or import all the classes in a package using a wildcard:
%
%         import org.opensha.gmm.*
%
%      Note that if GMM_Source is not imported above, then the subsequent
%      source declaration would have to modified to:
%
%         source = org.opensha.gmm.GMM_Source.create( ...
% 
% 

