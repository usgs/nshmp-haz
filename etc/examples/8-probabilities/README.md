Example 8: Earthquake probabilities and rates
---------------------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/8-probabilities`

`nshmp-haz` can also calculate earthquake probabilities and rates at a location. As with the `HazardCalc` and `DeaggCalc` programs, `RateCalc` takes a model, a site data file or string, and an optional config file, which will control whether the distributions generated are incremental or cumulative, and whether the distribution values are expressed as annual rates or Poisson probabilities. The default (no config supplied) settings are for incremental annual-rates. The `config.rate` elements also specify the cutoff `distance`, within which all sources should be included, and a `timespan` used for conversion to Poisson probabilities.

For this example, the following system alias is helpful:

```Shell
alias rate='java -Xms1024m -Xmx4096m -cp /path/to/nshmp-haz/build/libs/nshmp-haz.jar gov.usgs.earthquake.nshmp.RateCalc'
```

Assuming a copy of the 2008 USGS NSHM is available (see [Example 5](../5-complex-model)), execute:

```Shell
rate ../../../../nshmp-model-cous-2008/Western\ US sites.csv config-sites.json
```

to generate incremental, annual-rate output for a list of sites, or

```Shell
rate ../../../../nshmp-model-cous-2008/Western\ US map.geojson config-map.json
```

to generate a map of cumulative Poisson probabilities (i.e. P ≥ M).

Unless an output directory is specified in a supplied config, output will be placed in either an `eq-rate` or `eq-prob` directory. Like `HazardCalc`, `RateCalc` observes the `config.output.curveTypes` `SOURCE` option and will include a `source` directory with rates or probabilities for all contributing source types.
