package pt.keep.verapdf.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.verapdf.core.VeraPDFException;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class VeraPDFTest extends TestCase {
  /**
   * Create the test case
   *
   * @param testName
   *          name of the test case
   */
  public VeraPDFTest(String testName) {
    super(testName);
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(VeraPDFTest.class);
  }

  /**
   * 
   * 
   * @throws VeraPDFException
   * @throws IOException
   * @throws InterruptedException
   */
  public void testThreadSafe() throws IOException, VeraPDFException, InterruptedException {
    VeraPDFUtils utils = new VeraPDFUtils();

    List<Path> paths = getCorpora();
    int threads = 10;

    int errors = utils.testMultiThreaded(paths, threads);
    Assert.assertEquals(0, errors);
  }

  private List<Path> getCorpora() throws IOException {
    Path corpus = Paths.get(System.getProperty("user.home"), "verapdf", "corpus");
    return Files.walk(corpus).filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());
  }
}
