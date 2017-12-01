Example 2: A custom configuration
-------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/2-custom-config`

Navigate to the directory above and execute the following:

```Shell
hazard ../../peer/models/Set1-Case1 "San Francisco, -122.40, 37.75" config.json
```

In this example we've overridden the configuration supplied by the model. Specifically:

* The upper end of each hazard curve has been truncated at 3 standard deviations.
* Hazard curves have been saved as poisson probability instead of annual rate.
* Hazard curves have been calculated for 3 `imts` ([intensity measures](http://usgs.github.io/nshmp-haz/javadoc/index.html?gov/usgs/earthquake/nshmp/gmm/Imt.html), or spectral periods).
* The `imls` (intensity measure levels or x-values) of the resultant curves have been explicitely defined for each `imt`.

See the [configuration specification](https://github.com/usgs/nshmp-haz/wiki/configuration) for details on default values and supported options and formats.

__Results directory structure:__
```
2-custom-config/
  └─ hazout/
      ├─ config.json
      ├─ HazardCalc.log
      ├─ PGA/
      │   └─ curves.csv
      ├─ SA0P2/
      │   └─ curves.csv
      └─ SA1P0/
          └─ curves.csv
```

#### Next: [Example 3 – Using a custom sites file](../3-sites-file)
