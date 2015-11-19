Using nshmp-haz with Matlab
---------------------------

All recent versions of Matlab include a Java runtime environment and it is therefore relatively straightforward to use the nshmp-haz library.

#### Requirements

*  Matlab R2013B or higher (nshmp-haz targets Java 7; prior versions of Matlab use Java 6).
*  A [build](/usgs/nshmp-haz/wiki/Building-&-Running) of nshmp-haz.
*  Nshmp-haz.jar on Matlab's classpath. Save a file with the name `javaclasspath.txt` to the Matlab preferences directory (as specified by the `prefdir` command) and with contents: 

  ```
  <before>
  /path-to-repository/nshmp-haz/dist/nshmp-haz.jar
  ```

 **Note:** Although Java classes can be loaded dynamically when running a script, Matlab includes some outdated versions of 3rd party libraries required by nshmp-haz and it must therefore be loaded earlier.

