Example 5: A more complex model
-------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/5-complex-model`

Most PSHAs involve the use of more complex source models, the components of which might use different ground motion models. For this and ensuing examples, we'll use the 2008 USGS National Seismic Hazard Model (NSHM) for the western U.S.

First, clone the 2008 USGS NSHM. Assuming you are in the current working directory (above), the following will create a copy of the model adjacent to nshmp-haz:

```Shell
git clone https://github.com/usgs/nshmp-model-cous-2008.git ../../../../nshmp-model-cous-2008
```

The 2008 NSHM repository contains two source models: one for the western U.S. and a one for the central and eastern U.S. To compute hazard for a few sites in the Western U.S. at 1.0s and 2.0s spectral periods, execute:

```Shell
hazard ../../../../nshmp-model-cous-2008/Western\ US sites.geojson config-sites.json
```

Note that more complex models take longer to initialize, although this only occurs once per calculation, and make for longer, per-site calculations. However, `HazardCalc` will automatically use all cores available by default and therefore performs better on multi-core systems.

To compute a small, low-resolution map for the central San Francisco bay area, execute:

```Shell
hazard ../../../../nshmp-model-cous-2008/Western\ US map.geojson config-map.json
```

This computes 121 curves over a 2° by 2° area and will give you a sense of how long a larger map might take. Note that in the above two examples we specified different output directories for each calculation.

#### Next: [Example 6 – Enhanced output](../6-enhanced-output)
