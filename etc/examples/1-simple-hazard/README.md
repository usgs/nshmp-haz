Example 1: A simple hazard calculation
--------------------------------------

On the command line, navigate to this directory and execute the following:

```Shell
hazard ../../peer/models/Set1-Case1
```

The PEER models, such as that designated above, consist of simple cases for different source types commonly encountered in a PSHA and are included in the nshmp-haz repository to support testing. See the [PEER directory](../../peer/) for more information.

The result of this calculation should be available as a single comma-delimited file containing several hazard curves for PGA in a newly created 'curves' directory. In this example, the calculation configuration was derived from the model directory. Note that not all calculation [configuration](https://github.com/usgs/nshmp-haz/wiki/Configuration) parameters need be supplied; see the [configuration file](../peer/models/Set1-Case1/config.json) for this example model.

Also note that when only a model is supplied to the HazardCalc program, all output is written to the current working directory. In the next example, we'll override the model supplied configuration with a custom file.

#### Next: [Example 2](../2-custom-config)
