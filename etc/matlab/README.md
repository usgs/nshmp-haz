Using nshmp-haz with Matlab
---------------------------

>**NOTE:** *nshmp-haz* was recently upgraded to Java 8, which supercedes and is incompatable with the Java 7 JVM that ships with Matlab. Users will need to set the `MATLAB_JAVA` environment variable to point to a Java 8 runtime.

All recent versions of Matlab include a Java runtime environment and it is therefore relatively straightforward to use the nshmp-haz library.

#### Requirements

1.  Matlab R2013B or higher (nshmp-haz targets Java 7; prior versions of Matlab use Java 6).
2.  A [build](https://github.com/usgs/nshmp-haz/wiki/building-&-running) of nshmp-haz.
