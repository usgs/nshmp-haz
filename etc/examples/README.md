##### Hazard Curves
The simplest way to run the HazardCalc program via the command-line is to supply it with a source model; all model initialization and calculation configuration data will be read from the model itself. Navigate to the `/etc` in the repository and run:
```
java -cp ../dist/nshmp-haz.jar org.opensha2.programs.HazardCalc peer/models/Set1-Case1
```
The result of this calculation should be available as a single file containing several hazard curves for PGA in a newly created 'results' directory. Note that not all calculation [configuration](https://github.com/usgs/nshmp-haz/wiki/Configuration) parameters need be supplied; see the [configuration file](../peer/models/Set1-Case1/config.json) for this example model.  
One can override calculation configuration parameters by supplying an alternate configuration file. For example:
```
java -cp ../dist/nshmp-haz.jar org.opensha2.programs.HazardCalc peer/models/Set1-Case1 examples/config-sites.json
```
In this case:
* a truncation will be applied at 3 standard deviations.
* the list of `imts` (intensity measure types, or periods) for which curves will be calculated has been expanded to 3.
* the `imls` (the intensity measure levels or x-values) of the resultant curves, have been explicitely defined for each `imt`.
* two sites have been specified
The 'results' directory should now include 3 files, one for each `imt`.

One can also supply a comma-delimited site data file, which may be easier to work with in some applications.
```
java -cp ../dist/nshmp-haz.jar org.opensha2.programs.HazardCalc peer/models/Set1-Case1 examples/config-sites.json examples/sites-sf.csv
```
See the site file itself for details on the expected file structure. Under all use cases, if the name of a site is supplied, it will be included in the first column of any result files.

##### Hazard Maps
Hazard maps are generated from numerous uniformely spaced hazard curves. To compute such a curve set, the same program is used, but sites are instead specified as a region.
```
java -cp ../dist/nshmp-haz.jar org.opensha2.programs.HazardCalc peer/models/Set1-Case1 examples/config-region-sf.json
```

