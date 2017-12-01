Example 8: Earthquake probabilities and rates
---------------------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/8-probabilities`

`nshmp-haz` can also calculate earthquake probabilities and rates at a location. As with the `HazardCalc` and `DeaggCalc` programs, `RateCalc` takes a model, a site data file or string, and an optional config file, which will control whether the distributions generated are incremental or cumulative, and whether the distribution values are expressed as annual rates or Poisson probabilities. The default (no config supplied) settings are for incremental annual-rates. The `config.rate` elements also specify the cutoff `distance`, within which all sources should be included, and a `timespan` used for conversion to Poisson probabilities.

For this example, the following system alias is helpful:

```Shell
alias rate='java -Xms1g -Xmx4g -cp /path/to/nshmp-haz/build/libs/nshmp-haz.jar gov.usgs.earthquake.nshmp.RateCalc'
```

Assuming a copy of the 2008 USGS NSHM is available (see [Example 5](../5-complex-model)), execute:

```Shell
rate ../../../../nshm-cous-2008/Western\ US sites.csv config-sites.json
```

to generate incremental, annual-rate output for a list of sites, or

```Shell
rate ../../../../nshm-cous-2008/Western\ US map.geojson config-map.json
```

to generate a map of cumulative Poisson probabilities (i.e. P ≥ M).

Like `HazardCalc`, `RateCalc` observes the `config.output.dataTypes` `SOURCE` option and will include a `source` directory with rates or probabilities for all contributing source types.

__Results directory structure:__
```
8-probabilities/
  ├─ hazout-rate-sites/
  │   ├─ config.json
  │   ├─ RateCalc.log
  │   └─ rates.csv
  └─ hazout-prob-map/
      ├─ config.json
      ├─ RateCalc.log
      └─ probs.csv
```

