Example 2: Custom configuration
-------------------------------

Working directory: `/path/to/repo/nshmp-haz/etc/examples`

Navigate up one level to the `examples/` directory and execute the following:

```Shell
hazard ../peer/models/Set1-Case1 2-custom-config/config.json
```

In this example we've overridden the configuration supplied by the model. Specifically:

* The upper end of each hazard curve has been truncated at 3 standard deviations.
* Hazard curves have been calculated for 3 `imts` (intensity measure types, or spectral periods) and written to the directory containing the config file.
* The `imls` (intensity measure levels or x-values) of the resultant curves have been explicitely defined for each `imt`.
* And two different sites have been specified.

**A note on output:** Because we supplied a specific configuration file, all program output will be written to the directory where the config resides, thus keeping a record of calculation settings along with any results.

#### Next: [Example 3](../3-sites-file)
