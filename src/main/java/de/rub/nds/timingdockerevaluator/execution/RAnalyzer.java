/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.execution;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.RAnalyzerTask;
import de.rub.nds.timingdockerevaluator.task.eval.RAdditionalOutput;
import de.rub.nds.timingdockerevaluator.util.LibraryInstance;
import de.rub.nds.timingdockerevaluator.util.CsvFileGroup;
import de.rub.nds.timingdockerevaluator.util.RDataFileGroup;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;

public class RAnalyzer {
    
    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger();
    public static void analyzeGivenCSVs(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        List<File> csvFiles = new LinkedList<>();
        findCSVs(csvFiles, evaluationConfig);
        LOGGER.info("Found {} CSVs", csvFiles.size());
        Map<LibraryInstance, CsvFileGroup> libraryResultsMap = buildLibraryResultsMap(csvFiles);
        LOGGER.info("Found {} library instances", libraryResultsMap.keySet().size());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(evaluationConfig.getThreads());
        List<RAnalyzerTask> libraryTasks = new LinkedList<>();
        computeROutputs(libraryResultsMap, evaluationConfig, libraryTasks, executor);
        List<File> additionalRDataFiles = findRDataAdditionalFiles(evaluationConfig);
        Map<LibraryInstance, RDataFileGroup> libraryRDataMap = buildLibraryAdditionalDataMap(additionalRDataFiles);
        Set<String> vectorNames = new HashSet<>();
        for(LibraryInstance listedInstance : libraryRDataMap.keySet()) {
            vectorNames.addAll(libraryRDataMap.get(listedInstance).getAdditionalRData().stream().map(RAdditionalOutput::getVectorName).collect(Collectors.toList()));
        } 
        printResultData(vectorNames, libraryRDataMap);
        printF1AData(vectorNames, libraryRDataMap);
        printDecisionCounters(vectorNames, libraryRDataMap);
        
        
    }

    private static void printDecisionCounters(Set<String> vectorNames, Map<LibraryInstance, RDataFileGroup> libraryRDataMap) {
        LOGGER.info("Biggest Differences for Decision in Quantiles:");
        for(String listedVector: vectorNames) {
            LOGGER.info("({})", listedVector);
            Map<Integer, Integer> counterMap = new HashMap<>();
            for(LibraryInstance listedInstance : libraryRDataMap.keySet()) {
                appendDecisionCounter(counterMap, libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector));
            }
            int overall = counterMap.values().stream().reduce(0, Integer::sum);
            for(Integer entry: counterMap.keySet()) {
                LOGGER.info("{} : {}", entry, (double)((double)(counterMap.get(entry)) / (double)(overall)));
            }
        }
    }

    private static void printResultData(Set<String> vectorNames, Map<LibraryInstance, RDataFileGroup> libraryRDataMap) {
        LOGGER.info("Results:");
        LOGGER.info("Library ; {}", vectorNames.stream().collect(Collectors.joining(" ; ")));
        for(LibraryInstance listedInstance : libraryRDataMap.keySet()) {
            List<String> parts = new LinkedList<>();
            parts.add(listedInstance.getDockerName());
            for(String listedVector: vectorNames) {
                parts.add(printPower(libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector)));
            }
            LOGGER.info("{}", parts.stream().collect(Collectors.joining(" ; ")));
        }
    }

    private static void printF1AData(Set<String> vectorNames, Map<LibraryInstance, RDataFileGroup> libraryRDataMap) {
        LOGGER.info("F1A: ");
        LOGGER.info("Library ; {}", vectorNames.stream().collect(Collectors.joining(" ; ")));
        for(LibraryInstance listedInstance : libraryRDataMap.keySet()) {
            List<String> parts = new LinkedList<>();
            parts.add(listedInstance.getDockerName());
            for(String listedVector: vectorNames) {
                parts.add(printF1A(libraryRDataMap.get(listedInstance).getOutputForVectorName(listedVector)));
            }
            LOGGER.info("{}", parts.stream().collect(Collectors.joining(" ; ")));
        }
    }

    private static Map<LibraryInstance, RDataFileGroup> buildLibraryAdditionalDataMap(List<File> additionalRDataFiles) {
        Map<LibraryInstance, RDataFileGroup> libraryRAdditionalDataMap = new HashMap<>();
        for(File additionalDataFile: additionalRDataFiles) {
            LibraryInstance libInstance = LibraryInstance.fromDockerName(additionalDataFile.getParentFile().getParentFile().getName());
            if(!libraryRAdditionalDataMap.containsKey(libInstance)) {
                libraryRAdditionalDataMap.put(libInstance, new RDataFileGroup(libInstance));
            }
            RAdditionalOutput additionalOutput = RAdditionalOutput.fromFile(additionalDataFile);
            libraryRAdditionalDataMap.get(libInstance).insertFile(additionalOutput);
        }
        return libraryRAdditionalDataMap;
    }

    private static void computeROutputs(Map<LibraryInstance, CsvFileGroup> libraryResultsMap, TimingDockerEvaluatorCommandConfig evaluationConfig, List<RAnalyzerTask> libraryTasks, ThreadPoolExecutor executor) {
        LOGGER.info("Deploying tasks...");
        for(LibraryInstance givenLibrary: libraryResultsMap.keySet()) {
            RAnalyzerTask libraryTask = new RAnalyzerTask(libraryResultsMap.get(givenLibrary), evaluationConfig);
            libraryTasks.add(libraryTask);
            executor.execute(() -> {
                libraryTask.execute(); 
                LOGGER.info("Finished {}", libraryTask.getResultFileGroup().getLibraryInstance().getDockerName());
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(20, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
        }
        LOGGER.info("Determined all R outputs");
    }
    
    private static Map<LibraryInstance, CsvFileGroup> buildLibraryResultsMap(List<File> csvFiles) {
        Map<LibraryInstance, CsvFileGroup> libraryResultsMap = new HashMap<>();
        for(File csv : csvFiles) {
            LibraryInstance csvsLibraryInstance = LibraryInstance.fromDockerName(csv.getParentFile().getParentFile().getName());
            if(!libraryResultsMap.containsKey(csvsLibraryInstance)) {
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
    
    protected static List<File> findRDataAdditionalFiles(TimingDockerEvaluatorCommandConfig evaluationConfig) throws RuntimeException {
        List<File> rDataAdditionalFiles = new LinkedList<>();
        try {
            rDataAdditionalFiles.addAll(Files.find(Paths.get(evaluationConfig.getCsvInput()), 999, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().endsWith(".RDATA.add")).map(Path::toFile).collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new RuntimeException("Provided path not found: " + evaluationConfig.getCsvInput());
        }
        return rDataAdditionalFiles;
    }
    
    private static String printPower(RAdditionalOutput givenOutput) {
        if(givenOutput == null) {
            return "-";
        }
        return Integer.toString(givenOutput.getHighestPower());
    }
    
    private static String printF1A(RAdditionalOutput givenOutput) {
        if(givenOutput == null) {
            return "-";
        }
        return Integer.toString(givenOutput.getHighestF1a());
    }
    
    private static void appendDecisionCounter(Map<Integer, Integer> decisionCount, RAdditionalOutput additionalOutput) {
        if(additionalOutput != null) {
            if(!decisionCount.containsKey(additionalOutput.getBigestDecisionDifferenceIndex())) {
                decisionCount.put(additionalOutput.getBigestDecisionDifferenceIndex(), 0);
            }
            decisionCount.put(additionalOutput.getBigestDecisionDifferenceIndex(), decisionCount.get(additionalOutput.getBigestDecisionDifferenceIndex()) + 1);
        }
    }
}
