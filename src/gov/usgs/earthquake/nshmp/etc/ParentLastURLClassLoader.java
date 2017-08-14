package gov.usgs.earthquake.nshmp.etc;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

class ParentLastURLClassLoader extends ClassLoader {

  private ChildURLClassLoader childClassLoader;

  ParentLastURLClassLoader(List<URL> classpath) {
    super(Thread.currentThread().getContextClassLoader());
    URL[] urls = classpath.toArray(new URL[classpath.size()]);
    childClassLoader = new ChildURLClassLoader(urls, new FindClassClassLoader(this.getParent()));
  }

  /* Class permits calls to protected Classloader.findClass(). */
  private static class FindClassClassLoader extends ClassLoader {

    public FindClassClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
      return super.findClass(name);
    }
  }

  /*
   * This class delegates (child then parent) the protected findClass() method
   * of a URLClassLoader.
   */
  private static class ChildURLClassLoader extends URLClassLoader {

    private FindClassClassLoader realParent;

    public ChildURLClassLoader(URL[] urls, FindClassClassLoader realParent) {
      super(urls, null);
      this.realParent = realParent;
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
      try {
        // try to use the URLClassLoader findClass
        return super.findClass(name);
      } catch (ClassNotFoundException e) {
        // otherwise ask the real parent classloader to load the class
        return realParent.loadClass(name);
      }
    }
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    try {
      // try to find a class inside the child classloader
      return childClassLoader.findClass(name);
    } catch (ClassNotFoundException e) {
      // then try the parent
      return super.loadClass(name, resolve);
    }
  }
}
