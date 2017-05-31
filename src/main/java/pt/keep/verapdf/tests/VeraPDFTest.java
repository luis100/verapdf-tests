package pt.keep.verapdf.tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.verapdf.core.VeraPDFException;
import org.verapdf.features.FeatureExtractorConfig;
import org.verapdf.features.FeatureFactory;
import org.verapdf.metadata.fixer.FixerFactory;
import org.verapdf.metadata.fixer.MetadataFixerConfig;
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.validation.validators.ValidatorConfig;
import org.verapdf.pdfa.validation.validators.ValidatorFactory;
import org.verapdf.processor.BatchProcessingHandler;
import org.verapdf.processor.BatchProcessor;
import org.verapdf.processor.FormatOption;
import org.verapdf.processor.ProcessorConfig;
import org.verapdf.processor.ProcessorFactory;
import org.verapdf.processor.TaskType;
import org.verapdf.processor.plugins.PluginsCollectionConfig;
import org.verapdf.processor.reports.BatchSummary;

/**
 * Hello world!
 *
 */
public class VeraPDFTest {

  private BatchProcessor createProcessor() {
    VeraGreenfieldFoundryProvider.initialise();
    PDFAFlavour flavour = PDFAFlavour.NO_FLAVOUR;

    ValidatorConfig validatorConfig = ValidatorFactory.createConfig(flavour, true, 10);
    FeatureExtractorConfig featureConfig = FeatureFactory.defaultConfig();
    MetadataFixerConfig fixerConfig = FixerFactory.defaultConfig();
    PluginsCollectionConfig pluginConfig = PluginsCollectionConfig.defaultConfig();
    EnumSet<TaskType> tasks = EnumSet.of(TaskType.VALIDATE);
    // tasks.add(TaskType.EXTRACT_FEATURES);

    ProcessorConfig processorConfig = ProcessorFactory.fromValues(validatorConfig, featureConfig, pluginConfig,
      fixerConfig, tasks);

    return ProcessorFactory.fileBatchProcessor(processorConfig);
  }

  private BatchProcessingHandler createHandler(File output) throws FileNotFoundException, VeraPDFException {
    boolean verbose = true;
    int maxFailsChecksPerRule = 10;
    boolean logPassed = true;

    // TODO close output
    // XXX getHandler should not receive an output stream as it cannot be closed
    // by the same code that creates it, giving many chances for file leaks on
    // implementation that are not careful. Recommend a lambda expression that
    // **provides** outputstream, and for it to be created and closed within the
    // getHandler method.

    return ProcessorFactory.getHandler(FormatOption.MRR, verbose, new FileOutputStream(output), maxFailsChecksPerRule,
      logPassed);

  }

  public void testMultiThreaded(final List<File> files, int threads, int times) throws IOException, VeraPDFException {
    final BatchProcessor processor = createProcessor();
    File report = File.createTempFile("veraPDF", ".xml");
    final BatchProcessingHandler handler = createHandler(report);

    // aggregate results
    final List<BatchSummary> summaries = new ArrayList<>();
    final Map<Integer, Exception> exceptions = new HashMap<>();

    System.out.println("Report output at " + report.getAbsolutePath());

    ExecutorService pool = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < times; i++) {
      final int index = i + 1;

      pool.execute(() -> {
        System.out.println("Start #" + index);
        try {
          BatchSummary summary = processor.process(files, handler);
          summaries.add(summary);
        } catch (VeraPDFException | RuntimeException e) {
          exceptions.put(index, e);
        }
        System.out.println("End #" + index);
      });
    }

    pool.shutdown();
    try {
      pool.awaitTermination(1, TimeUnit.HOURS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    System.out.println("Terminated parallel execution");
    System.out.println("Failed parsing: "
      + summaries.stream().map(s -> s.getFailedParsingJobs()).distinct().collect(Collectors.toList()));
    System.out.println("Compliant: " + summaries.stream().map(s -> s.getValidationSummary().getCompliantPdfaCount())
      .distinct().collect(Collectors.toList()));
    System.out.println("NonCompliant: " + summaries.stream()
      .map(s -> s.getValidationSummary().getNonCompliantPdfaCount()).distinct().collect(Collectors.toList()));

    exceptions.entrySet().stream().forEach(entry -> {
      System.err.println("Execution #" + entry.getKey() + " exception: [" + entry.getValue().getClass().getSimpleName()
        + "] " + entry.getValue().getMessage());
      // entry.getValue().printStackTrace();
    });

  }

  public static void main(String[] args) {
    List<String> argsList = Arrays.asList(args);
    if (argsList.size() < 3) {
      System.err.println("Syntax: VeraPDFTest NUMBER_OF_THREADS NUMBER_OF_TIMES FILES...");
      System.exit(1);
    }

    int threads = Integer.parseInt(argsList.get(0));
    int times = Integer.parseInt(argsList.get(1));

    List<File> files = argsList.subList(2, argsList.size()).stream().map(filename -> new File(filename))
      .collect(Collectors.toList());

    try {
      new VeraPDFTest().testMultiThreaded(files, threads, times);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (VeraPDFException e) {
      e.printStackTrace();
    }
  }
}
