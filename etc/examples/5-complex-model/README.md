Example 5: A more complex model
-------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/5-complex-model`

Most PSHAs involve the use of more complex source models, the components of which might use different ground motion models. For this and ensuing examples, we'll use the 2008 USGS National Seismic Hazard Model (NSHM) for the western U.S. `nshmp-haz` uses an in-memory source model. The USGS NSHMs are quite large, so it's helpful to increase the amount memory available to Java when calling `HazardCalc`. For example, set your alias to:

```Shell
alias hazard='java -Xms1024m -Xmx4096m -cp /path/to/nshmp-haz/build/libs/nshmp-haz.jar gov.usgs.earthquake.nshmp.HazardCalc'
```

This will increase the minimum amount of memory Java requires to 1GB and will allow it to claim up to 4GB, assuming that much is available.

First, clone the 2008 USGS NSHM. Assuming you are in the current working directory (above), the following will create a copy of the model adjacent to nshmp-haz:

```Shell
git clone https://github.com/usgs/nshm-cous-2008.git ../../../../nshmp-model-cous-2008
```

The 2008 NSHM repository contains two source models: one for the western U.S. and a one for the central and eastern U.S. To compute hazard for a few sites in the Western U.S. at 1.0s and 2.0s spectral periods, execute:

```Shell
hazard ../../../../nshm-cous-2008/Western\ US sites.geojson config-sites.json
```

Note that more complex models take longer to initialize, although this only occurs once per calculation, and make for longer, per-site calculations. However, `HazardCalc` will automatically use all cores available by default and therefore performs better on multi-core systems.

To compute a small, low-resolution map for the central San Francisco bay area, execute:

```Shell
hazard ../../../../nshm-cous-2008/Western\ US map.geojson config-map.json
```

This computes 121 curves over a 2° by 2° area and will give you a sense of how long a larger map might take. Note that in the above two examples we specified different output directories in the config files for each calculation.


#### Directory structure and output files

<pre style="background: #f7f7f7">
5-complex-model/
|- <a href="../../example_outputs/5-complex-model/config-map.json">config-map.json </a>
|- <a href="../../example_outputs/5-complex-model/config-sites.json">config-sites.json </a>
|- curves-map/
        |- <a href="../../example_outputs/5-complex-model/curves-map/HazardCalc.log">HazadCalc.log </a>
        |- SA1P0/
              |- <a href="../../example_outputs/5-complex-model/curves-map/SA1P0/total.csv">total.csv </a>
        |- SA2P0/
              |- <a href="../../example_outputs/5-complex-model/curves-map/SA2P0/total.csv">total.csv </a>
        |- <a href="../../example_outputs/5-complex-model/curves-map/config.json">config.json </a>
|- curves-sites/
        |- <a href="../../example_outputs/5-complex-model/curves-sites/HazardCalc.log">HazadCalc.log </a>
        |- SA1P0/
              |- <a href="../../example_outputs/5-complex-model/curves-sites/SA1P0/total.csv">total.csv </a>
        |- SA2P0/
              |- <a href="../../example_outputs/5-complex-model/curves-sites/SA2P0/total.csv">total.csv </a>
        |- <a href="../../example_outputs/5-complex-model/curves-sites/config.json">config.json </a>
|- <a href="../../example_outputs/5-complex-model/map.geojson">map.geojson </a>
|- <a href="../../example_outputs/5-complex-model/sites.geojson">sites.geojson </a>
</pre>


#### Next: [Example 6 – Enhanced output](../6-enhanced-output)
