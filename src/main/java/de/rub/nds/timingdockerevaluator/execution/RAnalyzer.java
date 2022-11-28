package de.rub.nds.timingdockerevaluator.execution;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.RAnalyzerTask;
import de.rub.nds.timingdockerevaluator.task.eval.RAdditionalOutput;
import de.rub.nds.timingdockerevaluator.util.LibraryInstance;
import de.rub.nds.timingdockerevaluator.util.CsvFileGroup;
import de.rub.nds.timingdockerevaluator.util.LibraryInstanceComparator;
import de.rub.nds.timingdockerevaluator.util.RDataFileGroup;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;

public class RAnalyzer {

    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger();

    private static int csvsToProcess = 0;
    private static int processedCsvs = 0;
    private static long startedTimestamp = 0;
    
    public static void analyzeGivenCSVs(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        provideRResults(evaluationConfig);
        printResults(evaluationConfig.getCsvInput(), evaluationConfig);
    }

    public static void printResults(String path, TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        List<File> additionalRDataFiles = findRDataAdditionalFiles(path);
        Map<LibraryInstance, RDataFileGroup> libraryRDataMap = buildLibraryAdditionalDataMap(additionalRDataFiles);
        Set<String> vectorNames = new HashSet<>();
        for (LibraryInstance listedInstance : libraryRDataMap.keySet()) {
            vectorNames.addAll(libraryRDataMap.get(listedInstance).getAdditionalRData().stream().map(RAdditionalOutput::getVectorName).collect(Collectors.toList()));
        }
        List<String> sortedVectorNames = sortVectorsByAttack(vectorNames);
        List<LibraryInstance> sortedLibraryInstances = new LinkedList<>(libraryRDataMap.keySet());
        sortedLibraryInstances.sort(new LibraryInstanceComparator());
        printResultData(sortedLibraryInstances, sortedVectorNames, libraryRDataMap, evaluationConfig);
        printF1AData(sortedLibraryInstances, sortedVectorNames, libraryRDataMap, evaluationConfig);
        printDecisionCounters(sortedLibraryInstances, sortedVectorNames, libraryRDataMap);
    }

    private static List<String> sortVectorsByAttack(Set<String> vectorNames) {
        List<String> sortedVectorNames = new LinkedList<>();
        sortedVectorNames.addAll(vectorNames);
        Collections.sort(sortedVectorNames);
        String entry = sortedVectorNames.stream().filter(vector -> vector.contains("LONG_PADDING")).findFirst().orElse(null);
        if(entry != null) {
            sortedVectorNames.remove(entry);
            sortedVectorNames.add(entry);
        }
        return sortedVectorNames;
    }

    private static void provideRResults(TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        List<File> csvFiles = new LinkedList<>();
        findCSVs(csvFiles, evaluationConfig);
        csvsToProcess = csvFiles.size();
        LOGGER.info("Found {} CSVs", csvsToProcess);
        Map<LibraryInstance, CsvFileGroup> libraryResultsMap = buildLibraryResultsMap(csvFiles);
        LOGGER.info("Found {} library instances", libraryResultsMap.keySet().size());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        List<RAnalyzerTask> libraryTasks = new LinkedList<>();
        computeROutputs(libraryResultsMap, evaluationConfig, libraryTasks, executor);
    }

    private static void printDecisionCounters(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup> libraryRDataMap) {
        LOGGER.info("Biggest Differences for Decision in Quantiles:");
        for (String listedVector : sortedVectorNames) {
            LOGGER.info("({})", listedVector.replace(".csv-postEval-", ""));
            Map<Integer, Integer> counterMap = new HashMap<>();
            for (LibraryInstance listedInstance : sortedLibraryInstances) {
                appendDecisionCounter(counterMap, libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector));
            }
            int overall = counterMap.values().stream().reduce(0, Integer::sum);
            for (Integer entry : counterMap.keySet()) {
                LOGGER.info("{} : {}", entry, (double) ((double) (counterMap.get(entry)) / (double) (overall)));
            }
        }
    }

    private static void printResultData(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup> libraryRDataMap, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        LOGGER.info("Results:");
        printHeader(sortedVectorNames, evaluationConfig);
        for (LibraryInstance listedInstance : sortedLibraryInstances) {
            List<String> parts = new LinkedList<>();
            parts.add(listedInstance.getImplementationType().name());
            parts.add("v" + listedInstance.getVersion());
            for (String listedVector : sortedVectorNames) {
                parts.add(printPower(libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector)));
            }
            printToOutput(evaluationConfig, parts);
        }
    }

    private static void printToOutput(TimingDockerEvaluatorCommandConfig evaluationConfig, List<String> parts) {
        if(evaluationConfig.isPrintToConsole()) {
            System.out.println(parts.stream().collect(Collectors.joining(" , ")));
        } else {
            LOGGER.info("{}", parts.stream().collect(Collectors.joining(" , ")));
        }
    }

    private static void printHeader(List<String> sortedVectorNames, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        String headerParts = sortedVectorNames.stream().map(vectorName -> vectorName.replace(".csv-postEval-", "").replace(",", "_")).collect(Collectors.joining(" , "));
        if(evaluationConfig.isPrintToConsole()) {
            System.out.println("Library , Version , " + headerParts);
        } else {
            
            LOGGER.info("Library , Version , {}", headerParts);      
        }
    }

    private static void printF1AData(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup> libraryRDataMap, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        LOGGER.info("F1A: ");
        printHeader(sortedVectorNames, evaluationConfig);
        for (LibraryInstance listedInstance : sortedLibraryInstances) {
            List<String> parts = new LinkedList<>();
            parts.add(listedInstance.getDockerName());
            for (String listedVector : sortedVectorNames) {
                parts.add(printF1A(libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector)));
            }
            printToOutput(evaluationConfig, parts);
        }
    }

    private static Map<LibraryInstance, RDataFileGroup> buildLibraryAdditionalDataMap(List<File> additionalRDataFiles) {
        Map<LibraryInstance, RDataFileGroup> libraryRAdditionalDataMap = new HashMap<>();
        for (File additionalDataFile : additionalRDataFiles) {
            LibraryInstance libInstance = LibraryInstance.fromDockerName(additionalDataFile.getParentFile().getParentFile().getName());
            if (!libraryRAdditionalDataMap.containsKey(libInstance)) {
                libraryRAdditionalDataMap.put(libInstance, new RDataFileGroup(libInstance));
            }
            RAdditionalOutput additionalOutput = RAdditionalOutput.fromFile(additionalDataFile);
            libraryRAdditionalDataMap.get(libInstance).insertFile(additionalOutput);
        }
        return libraryRAdditionalDataMap;
    }

    private static void computeROutputs(Map<LibraryInstance, CsvFileGroup> libraryResultsMap, TimingDockerEvaluatorCommandConfig evaluationConfig, List<RAnalyzerTask> libraryTasks, ThreadPoolExecutor executor) {
        LOGGER.info("Deploying tasks...");
        startedTimestamp = System.currentTimeMillis();
        for (LibraryInstance givenLibrary : libraryResultsMap.keySet()) {
            for (File listedFile : libraryResultsMap.get(givenLibrary).getAllUniqueFiles()) {
                RAnalyzerTask libraryTask = new RAnalyzerTask(evaluationConfig, listedFile, givenLibrary);
                libraryTasks.add(libraryTask);
                executor.execute(() -> {
                    libraryTask.execute();
                    finishedCsv();
                });
            }

        }
        executor.shutdown();
        try {
            executor.awaitTermination(20, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
        }
        LOGGER.info("Determined all R outputs");
    }
    
    private static synchronized void finishedCsv() {
        processedCsvs += 1;
        long timeNow = System.currentTimeMillis();
        long minutes = TimeUnit.MINUTES.convert(timeNow - startedTimestamp, TimeUnit.MILLISECONDS);
        LOGGER.info("Progress: {} / {} CSVs in {} minutes", processedCsvs, csvsToProcess, minutes);
    }

    private static Map<LibraryInstance, CsvFileGroup> buildLibraryResultsMap(List<File> csvFiles) {
        Map<LibraryInstance, CsvFileGroup> libraryResultsMap = new HashMap<>();
        for (File csv : csvFiles) {
            LibraryInstance csvsLibraryInstance = LibraryInstance.fromDockerName(csv.getParentFile().getParentFile().getName());
            if (!libraryResultsMap.containsKey(csvsLibraryInstance)) {
                libraryResultsMap.put(csvsLibraryInstance, new CsvFileGroup(csvsLibraryInstance));
            }
            libraryResultsMap.get(csvsLibraryInstance).insertFile(csv);
        }

        return libraryResultsMap;
    }

    protected static void findCSVs(List<File> csvFiles, TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        try {
            csvFiles.addAll(Files.find(Paths.get(evaluationConfig.getCsvInput()), 999, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().endsWith(".csv")).map(Path::toFile).collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new RuntimeException("Provided path not found: " + evaluationConfig.getCsvInput());
        }
    }

    protected static List<File> findRDataAdditionalFiles(String path) throws RuntimeException {
        List<File> rDataAdditionalFiles = new LinkedList<>();
        try {
            rDataAdditionalFiles.addAll(Files.find(Paths.get(path), 999, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().endsWith(".RDATA.add")).map(Path::toFile).collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new RuntimeException("Provided path not found: " + path);
        }
        return rDataAdditionalFiles;
    }

    private static String printPower(RAdditionalOutput givenOutput) {
        if (givenOutput == null) {
            return "-";
        }
        return Integer.toString(givenOutput.getHighestPower());
    }

    private static String printF1A(RAdditionalOutput givenOutput) {
        if (givenOutput == null) {
            return "-";
        }
        return Integer.toString(givenOutput.getHighestF1a());
    }

    private static void appendDecisionCounter(Map<Integer, Integer> decisionCount, RAdditionalOutput additionalOutput) {
        if (additionalOutput != null) {
            if (!decisionCount.containsKey(additionalOutput.getBigestDecisionDifferenceIndex())) {
                decisionCount.put(additionalOutput.getBigestDecisionDifferenceIndex(), 0);
            }
            decisionCount.put(additionalOutput.getBigestDecisionDifferenceIndex(), decisionCount.get(additionalOutput.getBigestDecisionDifferenceIndex()) + 1);
        }
    }
}
