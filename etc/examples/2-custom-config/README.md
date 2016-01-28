Example 2: A custom configuration
-------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples`

Navigate up one level to the `examples/` directory and execute the following:

```Shell
hazard ../peer/models/Set1-Case1 2-custom-config/config.json
```

In this example we've overridden the configuration supplied by the model. Specifically:

* The upper end of each hazard curve has been truncated at 3 standard deviations.
* Hazard curves have been calculated for 3 `imts` ([intensity measure types](http://usgs.github.io/nshmp-haz/javadoc/index.html?org/opensha2/gmm/Imt.html), or spectral periods) and written to the directory containing the config file.
* The `imls` (intensity measure levels or x-values) of the resultant curves have been explicitely defined for each `imt`.
* And two different sites have been specified.

See the [configuration specification](https://github.com/usgs/nshmp-haz/wiki/Configuration) for details on default values and supported options and formats.

**A note on output:** Because we supplied a specific configuration file, all program output is written to the directory where the config resides, thus keeping a record of calculation settings along with any results.

#### Next: [Example 3 – Using a custom sites file](../3-sites-file)
