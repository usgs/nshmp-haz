Example 4: A simple hazard map
------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/4-hazard-map`

A hazard map is just a collection of values plucked from a lot of hazard curves. To compute curves at reqularly spaced intervals in latitude and longitude over a region, a [GeoJSON site file](https://github.com/usgs/nshmp-haz/wiki/sites#geojson-format-geojson) may instead specify a polygon and a site spacing.

```Shell
hazard ../../peer/models/Set1-Case1 map.geojson config.json
```

#### Directory structure and output files

<pre style="background: #f7f7f7">
|- <a href="../../example_outputs/4-hazard-map">4-hazard-map/ </a>
|- config.json 
|- <a href="../../example_outputs/4-hazard-map/curves">curves/ </a>
  |- HazadCalc.log 
  |- <a href="../../example_outputs/4-hazard-map/curves/PGA">PGA/ </a>
     |- total.csv 
  |- <a href="../../example_outputs/4-hazard-map/curves/SA0P2">SA0P2/ </a>
     |- total.csv 
  |- <a href="../../example_outputs/4-hazard-map/curves/SA1P0">SA1P0/ </a>
     |- total.csv 
  |- config.json 
|- map.geojson
</pre>

#### Next: [Example 5 – A more complex model](../5-complex-model)
