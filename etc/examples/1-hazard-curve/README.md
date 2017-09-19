Example 1: A simple hazard calculation
--------------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples/1-hazard-curve`

On the command line, navigate to the directory above and execute the following:

```Shell
hazard ../../peer/models/Set1-Case1 "Test Site, -122.0, 38.0"
```

The PEER models, such as that designated above, consist of simple cases for different source types commonly encountered in a PSHA and are included in the nshmp-haz repository to support testing. See the [PEER directory](../../peer/) for more information.

The result of this calculation should be available as a single comma-delimited file containing several total mean hazard curves for PGA in a newly created 'curves' directory. In this example, the calculation configuration was derived from the model directory and the site was specified as a comma-delimited string. The string must have the form: `name,lon,lat[,vs30,vsInf[,z1p0,z2p5]]`, where `vs30`, `vsInf`, `z1p0`, and `z2p5` are optional. See the [site specification](https://github.com/usgs/nshmp-haz/wiki/sites) page for more details.

Note that not all [calculation configuration](https://github.com/usgs/nshmp-haz/wiki/Configuration) parameters need be supplied; see the [configuration file](../../peer/models/Set1-Case1/config.json) for this example model.

Also note that all output is written to a `curves` directory by default, but the output destination can be specified via the [`output.directory`](https://github.com/usgs/nshmp-haz/wiki/configuration#config-output) parameter. In addition to hazard curves, the calculation configuration and a log of the calculation are also saved.


### Directory structure and output files

<pre style="background: #f7f7f7">
1-hazard-curve/   
|- curves/        
        |- HazardCalc.log
        |- PGA/           
              |- <a href="../../example_outputs/1-hazard-curves/curves/PGA/total.csv">Test </a>
</pre>

In the next example, we'll override the model supplied configuration with a custom file.

#### Next: [Example 2 – A custom configuration](../2-custom-config)
