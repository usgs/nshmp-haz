package gov.usgs.earthquake.nshmp.tree;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.gmm.DefaultScalarGroundMotion;
import gov.usgs.earthquake.nshmp.tree.Branch;
import gov.usgs.earthquake.nshmp.tree.LogicTree.Builder;

/**
 * JUnit tests for LogicTree
 */
@SuppressWarnings("javadoc")
public class LogicTreeTest {

  private static final String[] KEYS = new String[] {
      "Branch1",
      "Branch2",
      "Branch3",
      "Branch4" };

  private static final double[] WEIGHTS = new double[] { 0.40, 0.30, 0.20, 0.10 };

  private static final double[] CUML_WEIGHTS = new double[] { 0.40, 0.70, 0.90, 1.0 };

  private static final DefaultScalarGroundMotion GM = DefaultScalarGroundMotion
      .create(1.0, 0.5);

  private static final LogicTree<DefaultScalarGroundMotion> TREE = LogicTree
      .<DefaultScalarGroundMotion> builder()
      .add(KEYS[0], WEIGHTS[0], GM)
      .add(KEYS[1], WEIGHTS[1], GM)
      .add(KEYS[2], WEIGHTS[2], GM)
      .add(KEYS[3], WEIGHTS[3], GM)
      .build();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public final void builderEmpty() {
    thrown.expect(IllegalStateException.class);

    LogicTree.<DefaultScalarGroundMotion> builder().build();
  }

  @Test
  public final void builderAlreadyBuild() {
    thrown.expect(IllegalStateException.class);

    Builder<DefaultScalarGroundMotion> builder = LogicTree
        .<DefaultScalarGroundMotion> builder()
        .add("branch", 1.0, GM);

    builder.build();
    builder.build();
  }

  @Test
  public final void builderNullKey() {
    thrown.expect(NullPointerException.class);

    LogicTree.<DefaultScalarGroundMotion> builder()
        .add(null, 1.0, GM)
        .build();
  }

  @Test
  public final void builderNullValue() {
    thrown.expect(NullPointerException.class);

    LogicTree.<DefaultScalarGroundMotion> builder()
        .add("branch", 1.0, null)
        .build();
  }

  @Test
  public final void builderBuildBadWeights() {
    thrown.expect(IllegalArgumentException.class);

    LogicTree.<DefaultScalarGroundMotion> builder()
        .add("branch1", 1.0, GM)
        .add("branch2", 1.0, GM)
        .build();
  }

  @Test
  public final void equals() {
    int index = 0;

    List<Branch<DefaultScalarGroundMotion>> sampleBranches = TREE.sample(CUML_WEIGHTS);

    for (Branch<DefaultScalarGroundMotion> branch : TREE) {
      String key = KEYS[index];
      double weight = WEIGHTS[index];

      assertEquals(key, branch.id());
      assertEquals(weight, branch.weight(), 0);
      assertEquals(GM.mean(), branch.value().mean(), 0);
      assertEquals(GM.sigma(), branch.value().sigma(), 0);

      Branch<DefaultScalarGroundMotion> sampleBranch = TREE.sample(CUML_WEIGHTS[index]);
      assertEquals(key, sampleBranch.id());
      assertEquals(weight, sampleBranch.weight(), 0);
      assertEquals(GM.mean(), sampleBranch.value().mean(), 0);
      assertEquals(GM.sigma(), sampleBranch.value().sigma(), 0);

      assertEquals(key, sampleBranches.get(index).id());
      assertEquals(weight, sampleBranches.get(index).weight(), 0);
      assertEquals(GM.mean(), sampleBranches.get(index).value().mean(), 0);
      assertEquals(GM.sigma(), sampleBranches.get(index).value().sigma(), 0);

      index++;
    }

  }

}
