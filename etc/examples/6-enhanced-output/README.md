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
6-enhanced-output/
|- <a href="../../example_outputs/6-enhanced-output/config.json">config.json </a>
|- curves/
  |- <a href="../../example_outputs/6-enhanced-output/curves/HazardCalc.log">HazadCalc.log </a>
  |- PGA/
    |- gmm/
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/AB_03_CASC_SLAB.csv">AB_03_CASC_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/AB_03_GLOB_INTER.csv">AB_03_GLOB_INTER.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/AB_03_GLOB_SLAB.csv">AB_03_GLOB_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/BA_08.csv">BA_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/CB_08.csv">CB_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/CY_08.csv">CY_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/YOUNGS_97_INTER.csv">YOUNGS_97_INTER.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/YOUNGS_97_SLAB.csv">YOUNGS_97_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/gmm/ZHAO_06_INTER.csv">ZHAO_06_INTER.csv </a>
    |- source/
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/source/Fault.csv">Fault.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/source/Grid.csv">Grid.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/source/Interface.csv">Interface.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/source/Slab.csv">Slab.csv </a>
    |- <a href="../../example_outputs/6-enhanced-output/curves/PGA/total.csv">total.csv </a>
  |- SA0P2/
    |- gmm/
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/AB_03_CASC_SLAB.csv">AB_03_CASC_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/AB_03_GLOB_INTER.csv">AB_03_GLOB_INTER.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/AB_03_GLOB_SLAB.csv">AB_03_GLOB_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/BA_08.csv">BA_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/CB_08.csv">CB_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/CY_08.csv">CY_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/YOUNGS_97_INTER.csv">YOUNGS_97_INTER.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/YOUNGS_97_SLAB.csv">YOUNGS_97_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/gmm/ZHAO_06_INTER.csv">ZHAO_06_INTER.csv </a>
    |- source/
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/source/Fault.csv">Fault.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/source/Grid.csv">Grid.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/source/Interface.csv">Interface.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/source/Slab.csv">Slab.csv </a>
    |- <a href="../../example_outputs/6-enhanced-output/curves/SA0P2/total.csv">total.csv </a>
  |- SA1P0/
    |- gmm/
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/AB_03_CASC_SLAB.csv">AB_03_CASC_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/AB_03_GLOB_INTER.csv">AB_03_GLOB_INTER.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/AB_03_GLOB_SLAB.csv">AB_03_GLOB_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/BA_08.csv">BA_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/CB_08.csv">CB_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/CY_08.csv">CY_08.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/YOUNGS_97_INTER.csv">YOUNGS_97_INTER.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/YOUNGS_97_SLAB.csv">YOUNGS_97_SLAB.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/gmm/ZHAO_06_INTER.csv">ZHAO_06_INTER.csv </a>
    |- source/
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/source/Fault.csv">Fault.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/source/Grid.csv">Grid.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/source/Interface.csv">Interface.csv </a>
      |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/source/Slab.csv">Slab.csv </a>
    |- <a href="../../example_outputs/6-enhanced-output/curves/SA1P0/total.csv">total.csv </a>
  |- <a href="../../example_outputs/6-enhanced-output/curves/config.json">config.json </a>
|- <a href="../../example_outputs/6-enhanced-output/sites.geojson">sites.geojson </a>
</pre>  


#### Next: [Example 7 – Deaggregation](../7-deaggregation)

