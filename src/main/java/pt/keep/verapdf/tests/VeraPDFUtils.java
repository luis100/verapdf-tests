package pt.keep.verapdf.tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class VeraPDFUtils {

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

  public int testMultiThreaded(final List<Path> files, int threads)
    throws IOException, VeraPDFException, InterruptedException {
    final BatchProcessor processor = createProcessor();

    Path report = Files.createTempFile("veraPDF", ".xml");

    final BatchProcessingHandler handler = createHandler(report.toFile());

    // aggregate results
    final List<BatchSummary> summaries = new ArrayList<>();
    final Map<Path, Exception> exceptions = new HashMap<>();

    System.out.println("Report output at " + report);

    ExecutorService pool = Executors.newFixedThreadPool(threads);

    for (final Path file : files) {
      pool.execute(() -> {
        System.out.println("Start #" + file);
        try {
          BatchSummary summary = processor.process(Arrays.asList(file.toFile()), handler);
          summaries.add(summary);
        } catch (VeraPDFException | RuntimeException e) {
          exceptions.put(file, e);
        }
        System.out.println("End #" + file);
      });
    }

    pool.shutdown();
    pool.awaitTermination(1, TimeUnit.HOURS);
    
    System.out.println("TERMINATED PARALLEL EXECUTION");
    
    exceptions.entrySet().stream().forEach(entry -> System.err.println("Execution #" + entry.getKey() + " exception: ["
      + entry.getValue().getClass().getSimpleName() + "] " + entry.getValue().getMessage()));
    
    System.out.println("");
    System.out.println("SUMMARY");
    
    
    System.out.println("Files: " + files.size());
    System.out.println("Exceptions: " + exceptions.size());
    System.out.println("Failed parsing: " + summaries.stream().mapToInt(BatchSummary::getFailedParsingJobs).sum());
    System.out.println(
      "Compliant: " + summaries.stream().mapToInt(s -> s.getValidationSummary().getCompliantPdfaCount()).sum());
    System.out.println(
      "NonCompliant: " + summaries.stream().mapToInt(s -> s.getValidationSummary().getNonCompliantPdfaCount()).sum());
    System.out.println("");
    

    return exceptions.size();

  }

  public static void main(String[] args) {
    List<String> argsList = Arrays.asList(args);
    if (argsList.size() < 2) {
      System.err.println("Syntax: VeraPDFTest NUMBER_OF_THREADS FILES...");
      System.exit(1);
    }

    int threads = Integer.parseInt(argsList.get(0));

    List<Path> files = argsList.subList(1, argsList.size()).stream().map(Paths::get).collect(Collectors.toList());

    try {
      new VeraPDFUtils().testMultiThreaded(files, threads);
    } catch (IOException | VeraPDFException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}
