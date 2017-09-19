Example 6: Enhanced output
--------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/6-enhanced-output`

While mean hazard is of broad interest, it can be useful to preserve individual components of a total curve, particularly with more complex models. Execute the following to write curves for each source type and ground motion model (GMM) used in the 2008 NSHM:

```Shell
hazard ../../../../nshm-cous-2008/Western\ US sites.geojson config.json
```

The [config](https://github.com/usgs/nshmp-haz/blob/master/etc/examples/6-enhanced-output/config.json) file for this example specified `GMM` and `SOURCE` as [output curve types](https://github.com/usgs/nshmp-haz/wiki/configuration#calculation-configuration-parameters). Note that the output curves directory now contains additional directories of curves by source type and GMM. We also specified an [output flush limit](https://github.com/usgs/nshmp-haz/wiki/configuration#calculation-configuration-parameters) of `1`. Doing so gives feedback on how long it takes each site calculation to run on a particular system.

See the `nshmp-haz` wiki and JavDocs for more information on source types ([Wiki](https://github.com/usgs/nshmp-haz/wiki/source-types), [JavaDoc](http://usgs.github.io/nshmp-haz/javadoc/index.html?gov/usgs/earthquake/nshmp/eq/model/SourceType.html)) and GMMs ([Wiki](https://github.com/usgs/nshmp-haz/wiki/ground-motion-models), [JavaDoc](http://usgs.github.io/nshmp-haz/javadoc/index.html?gov/usgs/earthquake/nshmp/gmm/Gmm.html)).


#### Directory structure and output files

<pre style="background: #f7f7f7">
|- <a href="../../example_outputs/6-enhanced-output">6-enhanced-output/ </a>
|- config.json 
|- <a href="../../example_outputs/6-enhanced-output/curves">curves/ </a>
  |- HazadCalc.log 
  |- <a href="../../example_outputs/6-enhanced-output/curves/PGA">PGA/ </a>
    |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm">gmm/ </a>
      |- AB_03_CASC_SLAB.csv
      |- AB_03_GLOB_INTER.csv 
      |- AB_03_GLOB_SLAB.csv
      |- BA_08.csv
      |- CB_08.csv 
      |- CY_08.csv 
      |- YOUNGS_97_INTER.csv 
      |- YOUNGS_97_SLAB.csv 
      |- ZHAO_06_INTER.csv 
    |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/source">source/ </a>
      |- Fault.csv 
      |- Grid.csv
      |- Interface.csv 
      |- Slab.csv
    |- total.csv 
  |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2">SA0P2/ </a>
    |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm">gmm/ </a>
      |- AB_03_CASC_SLAB.csv
      |- AB_03_GLOB_INTER.csv 
      |- AB_03_GLOB_SLAB.csv
      |- BA_08.csv
      |- CB_08.csv 
      |- CY_08.csv 
      |- YOUNGS_97_INTER.csv 
      |- YOUNGS_97_SLAB.csv 
      |- ZHAO_06_INTER.csv 
    |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/source">source/ </a>
      |- Fault.csv 
      |- Grid.csv
      |- Interface.csv 
      |- Slab.csv
    |- total.csv 
  |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0">SA1P0/ </a>
    |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm">gmm/ </a>
      |- AB_03_CASC_SLAB.csv
      |- AB_03_GLOB_INTER.csv 
      |- AB_03_GLOB_SLAB.csv
      |- BA_08.csv
      |- CB_08.csv 
      |- CY_08.csv 
      |- YOUNGS_97_INTER.csv 
      |- YOUNGS_97_SLAB.csv 
      |- ZHAO_06_INTER.csv 
    |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/source">source/ </a>
      |- Fault.csv 
      |- Grid.csv
      |- Interface.csv 
      |- Slab.csv
    |- total.csv 
  |- config.json
|- sites.geojson 
</pre>  


#### Next: [Example 7 – Deaggregation](../7-deaggregation)

