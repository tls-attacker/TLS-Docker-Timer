package de.rub.nds.timingdockerevaluator.execution;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.RAnalyzerTask;
import de.rub.nds.timingdockerevaluator.task.eval.RAdditionalOutput;
import de.rub.nds.timingdockerevaluator.task.eval.RPostAnalyzedInfo;
import de.rub.nds.timingdockerevaluator.task.eval.RQuantileDetails;
import de.rub.nds.timingdockerevaluator.task.eval.RScriptManager;
import de.rub.nds.timingdockerevaluator.util.LibraryInstance;
import de.rub.nds.timingdockerevaluator.util.CsvFileGroup;
import de.rub.nds.timingdockerevaluator.util.LibraryInstanceComparator;
import de.rub.nds.timingdockerevaluator.util.RDataFileGroup;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    
    private static final String[] BB_VECTORS = new String[] {
        "Wrong_second_byte_(0x02_set_to_0x17)vsCorrectly_formatted_PKCS#1_PMS_message",
        "Invalid_TLS_version_in_PMSvsCorrectly_formatted_PKCS#1_PMS_message",
        "0x00_on_the_next_to_last_position_(|PMS|_=_1)vsCorrectly_formatted_PKCS#1_PMS_message",
        "No_0x00_in_messagevsCorrectly_formatted_PKCS#1_PMS_message",
        "Wrong_second_byte_(0x02_set_to_0x17)vsInvalid_TLS_version_in_PMS",
        "0x00_on_the_next_to_last_position_(|PMS|_=_1)vsWrong_second_byte_(0x02_set_to_0x17)",
        "No_0x00_in_messagevsWrong_second_byte_(0x02_set_to_0x17)",
        "0x00_on_the_next_to_last_position_(|PMS|_=_1)vsInvalid_TLS_version_in_PMS",
        "No_0x00_in_messagevsInvalid_TLS_version_in_PMS",
        "No_0x00_in_messagevs0x00_on_the_next_to_last_position_(|PMS|_=_1)"
    };
    
    private static final String[] PO_VECTORS = new String[] {
        "InvPadValMac-[0]-0-59vsValPadInvMac-[0]-0-59",
        "InvPadValMac-[0]-0-59vsPlain_FF",
        "InvPadValMac-[0]-0-59vsPlain_XF_(0xXF=#padding_bytes)",
        "Plain_FFvsValPadInvMac-[0]-0-59",
        "ValPadInvMac-[0]-0-59vsPlain_XF_(0xXF=#padding_bytes)",
        "Plain_FFvsPlain_XF_(0xXF=#padding_bytes)"
    };
    
    private static final String[] L13_VECTORS = new String[] {
        "NO_PADDINGvsLONG_PADDING"
    };
    
    private static final String[] ORDERED_VECTORS = ArrayConverter.concatenate(BB_VECTORS, PO_VECTORS, L13_VECTORS); 
    
    public static void analyzeGivenCSVs(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        provideRResults(evaluationConfig);
        if(!evaluationConfig.isGenericCsvs()) {
            printResults(evaluationConfig.getCsvInput(), evaluationConfig);
        }
    }

    public static void printResults(String path, TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        if(evaluationConfig.isExtractQuantileDetails()) {
            printQuantileDetails(path, evaluationConfig);
        } else {
            printAdditionalDataResults(path, evaluationConfig);
        }
    }

    private static void printAdditionalDataResults(String path, TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        List<File> additionalRDataFiles = findRDataAdditionalFiles(path);
        Map<LibraryInstance, RDataFileGroup<RAdditionalOutput>> libraryRDataMap = buildLibraryAdditionalDataMap(additionalRDataFiles);
        List<String> sortedVectorNames = Arrays.asList(ORDERED_VECTORS);
        List<LibraryInstance> sortedLibraryInstances = new LinkedList<>(libraryRDataMap.keySet());
        sortedLibraryInstances.sort(new LibraryInstanceComparator());
        printResultData(sortedLibraryInstances, sortedVectorNames, libraryRDataMap, evaluationConfig);
        printF1AData(sortedLibraryInstances, sortedVectorNames, libraryRDataMap, evaluationConfig);
        printDecisionCounters(sortedLibraryInstances, sortedVectorNames, libraryRDataMap);
    }

    private static void printQuantileDetails(String path, TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        List<File> rDataFiles = findRDataFiles(path);
        Map<LibraryInstance, RDataFileGroup<RQuantileDetails>> libraryRDataMap = buildLibraryQuantileDetailsMap(rDataFiles);
        List<String> sortedVectorNames = Arrays.asList(ORDERED_VECTORS);
        List<LibraryInstance> sortedLibraryInstances = new LinkedList<>(libraryRDataMap.keySet());
        sortedLibraryInstances.sort(new LibraryInstanceComparator());
        printQuantileData(sortedLibraryInstances, sortedVectorNames, libraryRDataMap, evaluationConfig);
        //printAttackSummaryDetails(sortedLibraryInstances, libraryRDataMap, evaluationConfig);
    }

    private static void provideRResults(TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        List<File> csvFiles = new LinkedList<>();
        findCSVs(csvFiles, evaluationConfig);
        csvsToProcess = csvFiles.size();
        LOGGER.info("Found {} CSVs", csvsToProcess);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        if(evaluationConfig.isGenericCsvs()) {
            analyzeGenericCsvs(evaluationConfig, executor, csvFiles);
        } else {
            analyzeSpecificCsvs(csvFiles, evaluationConfig, executor);
        }
    }

    private static void analyzeGenericCsvs(TimingDockerEvaluatorCommandConfig evaluationConfig, ThreadPoolExecutor executor, List<File> csvFiles) {
        LOGGER.info("Deploying tasks...");
        startedTimestamp = System.currentTimeMillis();
        for(File csvFile : csvFiles) {
            RAnalyzerTask genericCsvTask = new RAnalyzerTask(evaluationConfig, csvFile);
            executor.execute(() -> {
                    genericCsvTask.execute();
                    finishedCsv();
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(20, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
        }
        LOGGER.info("Determined all R outputs");
    }

    private static void analyzeSpecificCsvs(List<File> csvFiles, TimingDockerEvaluatorCommandConfig evaluationConfig, ThreadPoolExecutor executor) {
        Map<LibraryInstance, CsvFileGroup> libraryResultsMap = buildLibraryResultsMap(csvFiles);
        LOGGER.info("Found {} library instances", libraryResultsMap.keySet().size());
        List<RAnalyzerTask> libraryTasks = new LinkedList<>();
        computeROutputs(libraryResultsMap, evaluationConfig, libraryTasks, executor);
    }

    private static void printDecisionCounters(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup<RAdditionalOutput>> libraryRDataMap) {
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

    private static void printQuantileData(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup<RQuantileDetails>> libraryRDataMap, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        LOGGER.info("Quantile Details:");
        printHeader(sortedVectorNames, evaluationConfig);
        for (LibraryInstance listedInstance : sortedLibraryInstances) {
            List<String> parts = getLibraryInformation(listedInstance);
            for (String listedVector : sortedVectorNames) {
                RQuantileDetails quantileDetails = libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector);
                if(quantileDetails == null) {
                    parts.add("-");
                    continue;
                }
                parts.add(quantileDetails.getDetails());
            }
            printToOutput(evaluationConfig, parts);
        }
        
    }
    
    private static void printAttackSummaryDetails(List<LibraryInstance> sortedLibraryInstances, Map<LibraryInstance, RDataFileGroup<RQuantileDetails>> libraryRDataMap, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        for (LibraryInstance listedInstance : sortedLibraryInstances) {
            List<String> parts = getLibraryInformation(listedInstance);
            
            Double valBB = getMaxNormedQuantileValue(libraryRDataMap, listedInstance, BB_VECTORS);
            Double valPO = getMaxNormedQuantileValue(libraryRDataMap, listedInstance, PO_VECTORS);          
            Double valL13 = getMaxNormedQuantileValue(libraryRDataMap, listedInstance, L13_VECTORS);
            
            parts.add(valBB == null ? "-" : valBB.toString());
            parts.add(valPO == null ? "-" : valPO.toString());
            parts.add(valL13 == null ? "-" : valL13.toString());
            printToOutput(evaluationConfig, parts);
        }
    }

    private static Double getMaxNormedQuantileValue(Map<LibraryInstance, RDataFileGroup<RQuantileDetails>> libraryRDataMap, LibraryInstance listedInstance, String[] baseArray) throws NumberFormatException {
        double[] values = new double[15];
        int ctr = 0;
        for(String vector : baseArray) {
            RQuantileDetails quantileDetails = libraryRDataMap.get(listedInstance).getOutputForVectorName(vector);
            if(quantileDetails == null) {
                continue;
            }
            values[ctr] = Double.valueOf(quantileDetails.getDetails());
            ctr++;
        }
        if(Arrays.stream(Arrays.copyOf(values, ctr)).max().isPresent()) {
            return Arrays.stream(Arrays.copyOf(values, ctr)).max().getAsDouble();
        }
        return null;
    }
    
    private static void printResultData(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup<RAdditionalOutput>> libraryRDataMap, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        LOGGER.info("Results:");
        printHeader(sortedVectorNames, evaluationConfig);
        for (LibraryInstance listedInstance : sortedLibraryInstances) {
            List<String> parts = getLibraryInformation(listedInstance);
            for (String listedVector : sortedVectorNames) {
                RAdditionalOutput additionalData = libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector);
                
                if(additionalData == null) {
                    parts.add("-");
                    continue;
                }
                
                if(additionalData.isOldFormat()) {
                    parts.add(printPower(additionalData));
                } else {
                    parts.add(printQuantilesWithDifferences(additionalData).replace(",", ":"));
                }
            }
            printToOutput(evaluationConfig, parts);
        }
    }

    private static List<String> getLibraryInformation(LibraryInstance listedInstance) {
        List<String> parts = new LinkedList<>();
        parts.add(listedInstance.getImplementationType().name());
        parts.add("v" + listedInstance.getVersion());
        return parts;
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

    private static void printF1AData(List<LibraryInstance> sortedLibraryInstances, List<String> sortedVectorNames, Map<LibraryInstance, RDataFileGroup<RAdditionalOutput>> libraryRDataMap, TimingDockerEvaluatorCommandConfig evaluationConfig) {
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

    private static Map<LibraryInstance, RDataFileGroup<RAdditionalOutput>> buildLibraryAdditionalDataMap(List<File> additionalRDataFiles) {
        Map<LibraryInstance, RDataFileGroup<RAdditionalOutput>> libraryRAdditionalDataMap = new HashMap<>();
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
    
    private static Map<LibraryInstance, RDataFileGroup<RQuantileDetails>> buildLibraryQuantileDetailsMap(List<File> additionalRDataFiles) {
        Map<LibraryInstance, RDataFileGroup<RQuantileDetails>> libraryRAdditionalDataMap = new HashMap<>();
        int i = 0;
        for (File additionalDataFile : additionalRDataFiles) {
            if(i % 100 == 0) {
                LOGGER.info("Processed {}/{} files", i, additionalRDataFiles.size());
            }
            LibraryInstance libInstance = LibraryInstance.fromDockerName(additionalDataFile.getParentFile().getParentFile().getName());
            if (!libraryRAdditionalDataMap.containsKey(libInstance)) {
                libraryRAdditionalDataMap.put(libInstance, new RDataFileGroup(libInstance));
            }
            RQuantileDetails additionalOutput = RQuantileDetails.fromFile(additionalDataFile);
            libraryRAdditionalDataMap.get(libInstance).insertFile(additionalOutput);
            i++;
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
    
    protected static List<File> findRDataFiles(String path) throws RuntimeException {
        List<File> rDataAdditionalFiles = new LinkedList<>();
        try {
            rDataAdditionalFiles.addAll(Files.find(Paths.get(path), 999, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().endsWith(".RDATA")).map(Path::toFile).collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new RuntimeException("Provided path not found: " + path);
        }
        return rDataAdditionalFiles;
    }

    @Deprecated
    private static String printPower(RAdditionalOutput givenOutput) {
        if (givenOutput == null) {
            return "-";
        }
        return Integer.toString(givenOutput.getHighestPower());
    }
    
    private static String printQuantilesWithDifferences(RAdditionalOutput givenOutput) {
        if(givenOutput == null) {
            return "No results";
        } else if(givenOutput.getQuantilesWithDifference() == null) {
            return "NULL";
        } else if(givenOutput.getQuantilesWithDifference().length == 0) {
            return "No differences";
        } else {
            return Arrays.toString(givenOutput.getQuantilesWithDifference());
        }
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
