Examples
--------

1. **Note:** All examples avoid a lengthy call to Java and the HazardCalc program by using the following system alias:
   
        ```Shell
        alias hazard='java -cp /path/to/repo/nshmp-haz/dist/nshmp-haz.jar org.opensha2.programs.HazardCalc'
        ```
   
2. **Note:** All HazardCalc output is written to the current directory from which the program was called.



#### Calculating hazard maps
Hazard maps are generated from numerous uniformely spaced hazard curves. To compute such a curve set, the same program is used, but sites are instead specified as a region.
```
java -cp ../dist/nshmp-haz.jar org.opensha2.programs.HazardCalc peer/models/Set1-Case1 examples/config-region-sf.json
```

