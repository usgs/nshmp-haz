Example 4: A simple hazard map
------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/4-hazard-map`

A hazard map is just a collection of values plucked from a lot of hazard curves. To compute curves at reqularly spaced intervals in latitude and longitude over a region, a [GeoJSON site file](https://github.com/usgs/nshmp-haz/wiki/Sites) may instead specify a polygon and a site spacing.

```Shell
hazard ../../peer/models/Set1-Case1 map.geojson config.json
```

#### Directory structure and output files

<pre style="background: #f7f7f7">
4-hazard-map/
|- <a href="../../example_outputs/4-hazard-map/config.json">config.json </a>
|- curves/
        |- <a href="../../example_outputs/4-hazard-map/curves/HazardCalc.log">HazadCalc.log </a>
        |- PGA/
              |- <a href="../../example_outputs/4-hazard-map/curves/PGA/total.csv">total.csv </a>
        |- SA0P2/
              |- <a href="../../example_outputs/4-hazard-map/curves/SA0P2/total.csv">total.csv </a>
        |- SA1P0/
              |- <a href="../../example_outputs/4-hazard-map/curves/SA1P0/total.csv">total.csv </a>
        |- <a href="../../example_outputs/4-hazard-map/curves/config.json">config.json </a>
|- <a href="../../example_outputs/4-hazard-map/map.geojson">map.geojson </a>
</pre>

#### Next: [Example 5 – A more complex model](../5-complex-model)
