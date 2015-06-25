#### Tutorial

##### Hazard Curves
The simplest way to run the HazardCurve program via the command-line is to supply it with a source model; all model initialization and calculation configuration data will be read from the model itself. Navigate to the examples directory and run:
```
java -cp ../../dist/nshmp-haz.jar org.opensha2.programs.HazardCurve model
```
By way of example, the [configuration file](https://github.com/usgs/nshmp-haz/blob/master/etc/examples/model/config.json) in the model above omits any site data and a default Los Angeles site is used. Remember that [calculation configuration](https://github.com/usgs/nshmp-haz/wiki/Configuration) parameters need not be supplied. The result of this calculation should be available as a single file containing one hazard curve for PGA in a newly created 'results' directory.

One can override calculation configuration parameters by supplying an alternate configuration file. For example:
```
java -cp ../../dist/nshmp-haz.jar org.opensha2.programs.HazardCurve model config-sites.json
```
In this case:
* the `truncationLevel` has been increased to `3.0`.
* the list of `imts` (intensity measure types, or periods) for which curves will be calculated has been expanded to 3.
* the `imls` (the intensity measure levels or x-values) of the resultant curves, have been explicitely defined.
* two sites have been specified
The 'results' directory should now include 3 files, one for each intensity measure type.

One can also supply a comma-delimited site data file, which may be easier to work with in some applications.
```
java -cp ../../dist/nshmp-haz.jar org.opensha2.programs.HazardCurve model config-sites.json sites-wus.csv
```
See the site file itself for details on the expected file structure. Under all use cases, if the name of a site is supplied, it will be included in the first column of any result files.

##### Hazard Maps
Hazard maps are generated from numerous uniformely spaced hazard curves. To compute such a curve set, the same program is used, but sites are instead specified as a region.
```
java -cp ../../dist/nshmp-haz.jar org.opensha2.programs.HazardCurve model config-region.json
```

