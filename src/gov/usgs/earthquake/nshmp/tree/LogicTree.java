package gov.usgs.earthquake.nshmp.tree;

/**
 * Add comments here
 */
public class LogicTree {

  /*
   * Develop classes/support for modeling single logic tree (node with multiple
   * branches)
   * 
   * 
   * LogicTree.class
   * 
   * Fields: String[] keys, double[] weights, double[] cumulativeWeights[], T[])
   * 
   * (possible alternative List<Branch<T>> double[] cumWeights)
   * 
   * Builder: with put | add(String key, double value, T obj), on build(), use
   * Data.checkWeights() add overloads as necessary for double[], create
   * cumulative array of weight values
   * 
   * Iterator: Iterable<Branch<T>> - iterator() take a look at LocationGrid.Row
   * 
   * Sampling methods: sample(double p) return Branch<T> or sample(double[] p)
   * return List<Branch<T>>; this method will cycle up the cumulative weight
   * array and return the Branch corresponding to the supplied weight.
   * 
   * 
   * 
   * Branch<T>.class (could be nested in LogicTree)
   * 
   * (generic class) get() method return T
   * 
   * field: id (String)
   * 
   * field: weight (double)
   * 
   * field: T
   * 
   * 
   * For further consideration: serialization=, consider adding
   * LogicTree.asGraph(), see Guava graph classes
   */

}
