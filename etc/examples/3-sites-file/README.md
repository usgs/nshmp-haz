Example 3: Using a custom sites file
------------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/3-sites-file`

To compute hazard at more than one site, one may supply a comma-delimited (\*.csv) or [GeoJSON](http://geojson.org) (\*.geojson) formatted site data file instead:

```Shell
hazard ../../peer/models/Set1-Case1  sites.csv config.json
```

or

```Shell
hazard ../../peer/models/Set1-Case1 sites.geojson config.json
```

The [site specification](https://github.com/usgs/nshmp-haz/wiki/sites) wiki page provides details on the two file formats. Note that with either format, if the name of a site is supplied, it will be included in the first column of any output curve files.

Note that both formats ([CSV](sites.csv) and [GeoJSON](sites.geojson)) are elegantly rendered by GitHub.

#### Directory structure and output files

<pre style="background: #f7f7f7">
3-sites-file/
|- <a href="../../example_outputs/3-sites-file/config.json">config.json </a>
|- curves/
        |- <a href="../../example_outputs/3-sites-file/curves/HazardCalc.log">HazadCalc.log </a>
        |- PGA/           
              |- <a href="../../example_outputs/3-sites-file/curves/PGA/total.csv">total.csv </a>
        |- SA0P2/
              |- <a href="../../example_outputs/3-sites-file/curves/SA0P2/total.csv">total.csv </a>
        |- SA1P0/
              |- <a href="../../example_outputs/3-sites-file/curves/SA1P0/total.csv">total.csv </a>
        |- <a href="../../example_outputs/3-sites-file/curves/config.json">config.json </a>
|- <a href="../../example_outputs/3-sites-file/sites.csv">sites.csv </a>
|- <a href="../../example_outputs/3-sites-file/sites.geojson">sites.geojson </a>
</pre>



#### Next: [Example 4 – A simple hazard map](../4-hazard-map)
