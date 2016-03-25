Example 4: A simple hazard map
------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/4-hazard-map`

A hazard map is just a collection of values plucked from a lot of hazard curves. To compute curves at reqularly spaced intervals in latitude and longitude over a region, a [GeoJSON site file](https://github.com/usgs/nshmp-haz/wiki/Sites) may instead specify a polygon and a site spacing.

```Shell
hazard ../../peer/models/Set1-Case1 map.geojson config.json
```

#### Next: [Example 5 – A more complex model](../5-complex-model)
