Example 3: Using a custom sites file
------------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples`

One may also supply a comma-delimited site data file, which may be easier to work with in some applications.

```Shell
hazard ../peer/models/Set1-Case1 3-sites-file/config.json 3-sites-file/sites.csv
```

See the [site file](sites.csv) itself for details on the expected file structure. Under all use cases, if the name of a site is supplied, it will be included in the first column of any curve files.

#### Next: [Example 4 – A simple hazard map](../4-hazard-map)
