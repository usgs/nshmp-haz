Example 7: Deaggregation
------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/7-deaggregation`

To perform a deaggregation of hazard, one must use the program `DeaggCalc`. Internally, `DeaggCalc` calls `HazardCalc` and then reprocesses the data to generate a comma-delimited file of distance, magnitude, and epsilon bins, and a text file of summary statistics and primary contributing sources. For this, it can be helpful to create a second system alias:

```Shell
alias deagg='java -Xms1g -Xmx4g -cp /path/to/nshmp-haz/build/libs/nshmp-haz.jar gov.usgs.earthquake.nshmp.DeaggCalc'
```

`DeaggCalc` is similar to `HazardCalc` in every way except that the return-period of interest must be specified. For example, execute:

```Shell
deagg ../../../../nshm-cous-2008/Western\ US sites.geojson 2475 config.json
```

The results of the deaggregation are saved along with hazard curves in `deagg` directories. As with `HazardCalc`, if the `GMM` ddata type has been specified (as it has in the [config](https://github.com/usgs/nshmp-haz/blob/master/etc/examples/7-deaggregation/config.json) file for this example) additional deaggregation results for each GMM are generated as well. Deaggregations by individual `SOURCE` type are also possible.

See the following pages for more information on [deaggregation](https://github.com/usgs/nshmp-haz/wiki/about-deaggregation) and the meaning of [epsilon](https://github.com/usgs/nshmp-haz/wiki/what-is-epsilon%3F).

__Results directory structure:__
```
7-deaggregation/
  └─ hazout/
      ├─ config.json
      ├─ DeaggCalc.log
      ├─ PGA/
      │   ├─ curves.csv
      │   ├─ deagg/
      │   │   ├─ Los Angeles CA/
      │   │   │   ├─ data.csv
      │   │   │   └─ summary.txt
      │   │   ├─ Salt Lake City UT/
      │   │   │   ├─ data.csv
      │   │   │   └─ summary.txt
      │   │   ├─ San Francisco CA/
      │   │   │   ├─ data.csv
      │   │   │   └─ summary.txt
      │   │   └─ Seattle WA/
      │   │       ├─ data.csv
      │   │       └─ summary.txt
      │   └─ gmm/
      │       ├─ AB_03_CASCADIA_SLAB/
      │       │   ├─ curves.csv
      │       │   └─ deagg/
      │       │       ├─ San Francisco CA/
      │       │       │   ├─ data.csv
      │       │       │   └─ summary.txt
      │       │       └─ Seattle WA/
      │       │           ├─ data.csv
      │       │           └─ summary.txt
      │       ├─ ...
      │       ├─ CB_08/
      │       │   ├─ curves.csv
      │       │   └─ deagg/
      │       │       ├─ Los Angeles CA/
      │       │       │   ├─ data.csv
      │       │       │   └─ dsummary.txt
      │       │       ├─ Salt Lake City UT/
      │       │       │   ├─ data.csv
      │       │       │   └─ summary.txt
      │       │       ├─ San Francisco CA/
      │       │       │   ├─ data.csv
      │       │       │   └─ summary.txt
      │       │       └─ Seattle WA/
      │       │           ├─ data.csv
      │       │           └─ summary.txt
      │       └─ ...
      ├─ SA0P2/
      │   └─ ...
      └─ SA1P0/
          └─ ...
```
Note that in the output above, there are only deaggregation results for subduction GMMs (e.g. `AB_03_CASCADIA_SLAB`) for sites closer to the Cascadia subduction zone; empty results will not be saved.

#### Next: [Example 8 – Earthquake probabilities and rates](../8-probabilities)

