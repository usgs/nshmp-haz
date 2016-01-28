Example 4: A simple hazard map
------------------------------

Working directory: `/path/to/nshmp-haz/etc/examples`

A hazard map is just a lot of hazard curves. To compute curves at reqularly spaced intervals in latitude and longitude, the sites configuration can instead be specified as a polygon.

```Shell
hazard ../peer/models/Set1-Case1 3-sites-file/config.json
```

See the [site file](3-sites-file/sites.csv) itself for details on the expected file structure. Under all use cases, if the name of a site is supplied, it will be included in the first column of any result files.

#### Next: [Example 5](../5-complex-model)
