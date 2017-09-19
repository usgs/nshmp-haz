Example 7: Deaggregation
------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/7-deaggregation`

To perform a deaggregation of hazard, one must use the program `DeaggCalc`. Internally, `DeaggCalc` calls `HazardCalc` and then reprocesses the data to generate a comma-delimited file of distance, magnitude, and epsilon bins, and a text file of summary statistics and primary contributing sources. For this, it can be helpful to create a second system alias:

```Shell
alias deagg='java -Xms1g -Xmx4g -cp /path/to/nshmp-haz/build/libs/nshmp-haz.jar gov.usgs.earthquake.nshmp.DeaggCalc'
```

`DeaggCalc` is similar to `HazardCalc` in every way except that the return-period of interest must be specified. For example, execute:

```Shell
deagg ../../../../nshm-cous-2008/Western\ US sites.geojson 2475 config.json
```

The results of the deaggregation are saved to a `deagg` directory along with hazard curves. As with `HazardCalc`, if `GMM` has been specified (as it has in the [config](https://github.com/usgs/nshmp-haz/blob/master/etc/examples/7-deaggregation/config.json) file for this example) additional deaggregation results for each GMM are generated as well.

See the following pages for more information on [deaggregation](https://github.com/usgs/nshmp-haz/wiki/about-deaggregation) and the meaning of [epsilon](https://github.com/usgs/nshmp-haz/wiki/what-is-epsilon%3F).


#### Directory structure and output files

<pre style="background: #f7f7f7">
|- <a href="../../example_outputs/7-deaggregation" >7-deaggregation/</a> 
|- config.json 
|- <a href="../../example_outputs/7-deaggregation/curves">curves/</a> 
  |- DeaggCalc.log 
  |- <a href="../../example_outputs/7-deaggregation/curves/PGA" >PGA/ </a> 
    |- <a href="../../example_outputs/7-deaggregation/curves/PGA/deagg" >deagg/</a> 
      |- Los Angeles CA-data.csv 
      |- Los Angeles CA-summary.csv
      |- Salt Lake City UT-data.csv
      |- Salt Lake City UT-summary.csv
      |- San Francisco CA-data.csv 
      |- San Francisco CA-summary.csv 
      |- Seattle WA-data.csv 
      |- Seattle WA-summary.csv 
    |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm" >gmm/</a> 
      |- AB_03_CASC_SLAB.csv  
      |- AB_03_GLOB_INTER.csv 
      |- AB_03_GLOB_SLAB.csv  
      |- BA_08.csv            
      |- CB_08.csv            
      |- CY_08.csv            
      |- YOUNGS_97_INTER.csv  
      |- YOUNGS_97_SLAB.csv   
      |- ZHAO_06_INTER.csv    
      |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg" >deagg/</a> 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/AB_03_CASC_SLAB" >AB_03_CASC_SLAB/  </a>
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/AB_03_CASC_INTER">AB_03_GLOB_INTER/ </a>
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/AB_03_GLOB_SLAB" >AB_03_GLOB_SLAB/  </a>
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/BA_08">BA_08/</a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/CB_08">CB_08/</a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/CY_08">CY_08/</a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/YOUNGS_97_INTER" >YOUNGS_97_INTER/  </a>
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/YOUNGS_97_SLAB"  >YOUNGS_97_SLAB/   </a>
          |- san francisco ca-data.csv 
          |- san francisco ca-summary.csv 
          |- seattle wa-data.csv 
          |- seattle wa-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/PGA/gmm/deagg/ZHAO_06_INTER"   >ZHAO_06_INTER/    </a>
          |- seattle wa-data.csv 
          |- seattle wa-summary.csv 
    |- total.csv
  |- <a href="../../example_outputs/7-deaggregation/curves/PSA0P2" >SA0P2/</a> 
    |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/deagg" >deagg/</a> 
      |- Los Angeles CA-data.csv 
      |- Los Angeles CA-summary.csv
      |- Salt Lake City UT-data.csv
      |- Salt Lake City UT-summary.csv
      |- San Francisco CA-data.csv 
      |- San Francisco CA-summary.csv 
      |- Seattle WA-data.csv 
      |- Seattle WA-summary.csv 
    |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm" >gmm/</a> 
      |- AB_03_CASC_SLAB.csv  
      |- AB_03_GLOB_INTER.csv 
      |- AB_03_GLOB_SLAB.csv  
      |- BA_08.csv            
      |- CB_08.csv            
      |- CY_08.csv            
      |- YOUNGS_97_INTER.csv  
      |- YOUNGS_97_SLAB.csv   
      |- ZHAO_06_INTER.csv    
      |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg" >deagg/</a> 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/AB_03_CASC_SLAB" >AB_03_CASC_SLAB/  </a>
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/AB_03_CASC_INTER">AB_03_GLOB_INTER/ </a>
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/AB_03_GLOB_SLAB" >AB_03_GLOB_SLAB/  </a>
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/BA_08">BA_08/ </a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/CB_08">CB_08/ </a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/CY_08">CY_08/ </a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/YOUNGS_97_INTER" >YOUNGS_97_INTER/  </a>
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/YOUNGS_97_SLAB"  >YOUNGS_97_SLAB/   </a>
          |- san francisco ca-data.csv 
          |- san francisco ca-summary.csv 
          |- seattle wa-data.csv 
          |- seattle wa-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA0P2/gmm/deagg/ZHAO_06_INTER"   >ZHAO_06_INTER/    </a>
          |- seattle wa-data.csv 
          |- seattle wa-summary.csv 
    |- total.csv
  |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0" >SA1P0/ </a> 
    |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/deagg" >deagg/</a> 
      |- Los Angeles CA-data.csv 
      |- Los Angeles CA-summary.csv
      |- Salt Lake City UT-data.csv
      |- Salt Lake City UT-summary.csv
      |- San Francisco CA-data.csv 
      |- San Francisco CA-summary.csv 
      |- Seattle WA-data.csv 
      |- Seattle WA-summary.csv 
    |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm" >gmm/</a> 
      |- AB_03_CASC_SLAB.csv  
      |- AB_03_GLOB_INTER.csv 
      |- AB_03_GLOB_SLAB.csv  
      |- BA_08.csv            
      |- CB_08.csv            
      |- CY_08.csv            
      |- YOUNGS_97_INTER.csv  
      |- YOUNGS_97_SLAB.csv   
      |- ZHAO_06_INTER.csv    
      |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg" >deagg/</a> 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/AB_03_CASC_SLAB" >AB_03_CASC_SLAB/  </a>
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/AB_03_CASC_INTER">AB_03_GLOB_INTER/ </a>
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/AB_03_GLOB_SLAB" >AB_03_GLOB_SLAB/  </a>
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/BA_08">BA_08/ </a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/CB_08">CB_08/ </a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/CY_08">CY_08/ </a>
          |- Los Angeles CA-data.csv 
          |- Los Angeles CA-summary.csv
          |- Salt Lake City UT-data.csv
          |- Salt Lake City UT-summary.csv
          |- San Francisco CA-data.csv 
          |- San Francisco CA-summary.csv 
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/YOUNGS_97_INTER" >YOUNGS_97_INTER/  </a>
          |- Seattle WA-data.csv 
          |- Seattle WA-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/YOUNGS_97_SLAB"  >YOUNGS_97_SLAB/   </a>
          |- san francisco ca-data.csv 
          |- san francisco ca-summary.csv 
          |- seattle wa-data.csv 
          |- seattle wa-summary.csv 
        |- <a href="../../example_outputs/7-deaggregation/curves/SA1P0/gmm/deagg/ZHAO_06_INTER"   >ZHAO_06_INTER/    </a>
          |- seattle wa-data.csv 
          |- seattle wa-summary.csv 
    |- total.csv
  |- config.json 
|- sites.geojson 
</pre>

#### Next: [Example 8 – Earthquake probabilities and rates](../8-probabilities)

