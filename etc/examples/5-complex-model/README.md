Example 5: A more complex model
-------------------------------

Working directory: `/path/to/nshmp-haz/etc/examples`

Most PSHAs involve the use of more complex source models, the components of which might use different ground motion models. For this and ensuing examples, we'll use the 2008 USGS National Seismic Hazard Model (NSHM) for the western U.S.

First, clone the 2008 USGS NSHM. Assuming `examples/` is the current working directory, the following will create a copy of the model adjacent to nshmp-haz:

```Shell
git clone https://github.com/usgs/nshmp-model-cous-2008.git ../../..
```

The 2008 NSHM repository contains two source models: one for the western U.S. and a one for the central and eastern U.S. More complex models make for longer, per-site calculations. To compute hazard for a few sites in the Western U.S., execute:

```Shell
hazard ../../../nshmp-model-cous-2008/Western\ US 5-complex-model/config.json
```

#### Next: [Example 6](../4-enhanced-output)
