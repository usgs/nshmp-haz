Example 2: A custom configuration
-------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/2-custom-config`

Navigate to the directory above and execute the following:

```Shell
hazard ../../peer/models/Set1-Case1 "San Francisco, -122.40, 37.75" config.json
```

In this example we've overridden the configuration supplied by the model. Specifically:

* The upper end of each hazard curve has been truncated at 3 standard deviations.
* Hazard curves have been saved as poisson probability instead of annual rate.
* Hazard curves have been calculated for 3 `imts` ([intensity measures](http://usgs.github.io/nshmp-haz/javadoc/index.html?gov/usgs/earthquake/nshmp/gmm/Imt.html), or spectral periods).
* The `imls` (intensity measure levels or x-values) of the resultant curves have been explicitely defined for each `imt`.

See the [configuration specification](https://github.com/usgs/nshmp-haz/wiki/configuration) for details on default values and supported options and formats.


#### Directory structure and output files

<pre style="background: #f7f7f7">
2-custom-config/   
|- <a href="../../example_outputs/2-custom-config/config.json">config.json </a>
|- curves/       
        |- <a href="../../example_outputs/2-custom-config/curves/HazardCalc.log">HazadCalc.log </a>
        |- PGA/           
              |- <a href="../../example_outputs/2-custom-config/curves/PGA/total.csv">total.csv </a>
        |- SA0P2/           
              |- <a href="../../example_outputs/2-custom-config/curves/SA0P2/total.csv">total.csv </a>
        |- SA1P0/           
              |- <a href="../../example_outputs/2-custom-config/curves/SA1P0/total.csv">total.csv </a>
        |- <a href="../../example_outputs/2-custom-config/curves/config.json">config.json </a>
</pre>


#### Next: [Example 3 – Using a custom sites file](../3-sites-file)
