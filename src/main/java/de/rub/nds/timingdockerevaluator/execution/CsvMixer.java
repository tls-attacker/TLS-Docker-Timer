/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.execution;

import de.rub.nds.timingdockerevaluator.task.eval.RAdditionalOutput;
import de.rub.nds.timingdockerevaluator.util.LibraryInstance;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author marcel
 */
public class CsvMixer {

    private static final String PARALLEL_DIRECTORY = "/home/marcel/Documents/RUB/Paper/TimingStatistik/221102/parallel/";
    private static final String SEQUENTIAL_DIRECTORY = "/home/marcel/Documents/RUB/Paper/TimingStatistik/221102/sequential/";
    private static final String OUTPUT_DIRECTORY = "/home/marcel/Documents/RUB/Paper/TimingStatistik/221102/results/";

    private static final Map<LibraryInstance, Map<Integer, List<File>>> parallelMeasurements = new HashMap<>();
    private static final Map<LibraryInstance, Map<Integer, List<File>>> sequentialMeasurements = new HashMap<>();

    //private static final Map<LibraryInstance, Map<String, File>>
    public static void main(String args[]) {
        buildMainMaps();
        //seq1 vs seq2 [baseline | modified]
        mixMeasurements(sequentialMeasurements, sequentialMeasurements, 0, 1, true, MixType.SEQ);
        mixMeasurements(sequentialMeasurements, sequentialMeasurements, 0, 1, false, MixType.SEQ);
        //parallel1 vs parallel2 [baseline | modified]
        mixMeasurements(parallelMeasurements, parallelMeasurements, 0, 1, true, MixType.PARALLEL);
        mixMeasurements(parallelMeasurements, parallelMeasurements, 0, 1, false, MixType.PARALLEL);
        //seq1 vs parallel1 [baseline | modified]
        mixMeasurements(sequentialMeasurements, parallelMeasurements, 0, 0, true, MixType.CROSS);
        mixMeasurements(sequentialMeasurements, parallelMeasurements, 0, 0, false, MixType.CROSS);
    }

    private static void mixMeasurements(Map<LibraryInstance, Map<Integer, List<File>>> map1, Map<LibraryInstance, Map<Integer, List<File>>> map2, int iteration1, int iteration2, boolean baseline, MixType mixType) {
        for (LibraryInstance lib : map1.keySet()) {
            if (map1.get(lib) == null) {
                throw new RuntimeException("Unknown lib " + lib.getDockerName() + " in map1");
            } else if (map1.get(lib).get(iteration1) == null) {
                continue;
            }
            for (File run0 : map1.get(lib).get(iteration1)) {
                File run1 = getEquivalentResult(run0, map2.get(lib).get(iteration2));
                if (run1 != null) {
                    List<String> run0Matching = getMeasurementsOfCsv(run0, baseline);
                    List<String> run1Matching = getMeasurementsOfCsv(run1, baseline);
                    writeResultFile(lib, run0, run0Matching, run1Matching, mixType, baseline);
                }
            }
        }
    }

    private static void writeResultFile(LibraryInstance lib, File run0, List<String> run0Matching, List<String> run1Matching, MixType mixType, boolean baseline) {
        File outFile = new File(OUTPUT_DIRECTORY + mixType.name() + ((baseline) ? "-Baseline" : "-Modified") + "/" + lib.getDockerName() + "/" + run0.getParentFile().getName() + "/" + mixType.name() + "_" + run0.getName());
        outFile.getParentFile().mkdirs();

        BufferedWriter bufferedWriter = null;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(outFile));
            bufferedWriter.write("V1,V2\n");
            for (String measurement : run0Matching) {
                bufferedWriter.write(measurement.replace("MODIFIED", "BASELINE") + "\n");
            }
            for (String measurement : run1Matching) {
                bufferedWriter.write(measurement.replace("BASELINE", "MODIFIED") + "\n");
            }
            System.out.println("Wrote Result!");
        } catch (IOException ex) {
            throw new RuntimeException("Attempted to access non-existing result file");
        } finally {
            try {
                bufferedWriter.close();
            } catch (IOException ex) {

            }
        }
    }

    private static File getEquivalentResult(File givenCsv, List<File> comparisonList) {
        if (comparisonList != null) {

            for (File otherCsv : comparisonList) {
                if (otherCsv.getName().equals(givenCsv.getName())) {
                    return otherCsv;
                }
            }
        }

        return null;
    }

    private static void buildMainMaps() {
        List<File> parallel0 = collectCsvs(PARALLEL_DIRECTORY, 0);
        List<File> parallel1 = collectCsvs(PARALLEL_DIRECTORY, 1);
        List<File> seq0 = collectCsvs(SEQUENTIAL_DIRECTORY, 0);
        List<File> seq1 = collectCsvs(SEQUENTIAL_DIRECTORY, 1);

        Map<LibraryInstance, List<File>> parallel0Map = getInstanceFileMap(parallel0);
        Map<LibraryInstance, List<File>> parallel1Map = getInstanceFileMap(parallel1);
        Map<LibraryInstance, List<File>> seq0Map = getInstanceFileMap(seq0);
        Map<LibraryInstance, List<File>> seq1Map = getInstanceFileMap(seq1);

        for (LibraryInstance lib : seq1Map.keySet()) {
            Map<Integer, List<File>> innerParallelMap = new HashMap<>();
            Map<Integer, List<File>> innerSeqMap = new HashMap<>();

            innerParallelMap.put(0, parallel0Map.get(lib));
            innerParallelMap.put(1, parallel1Map.get(lib));
            innerSeqMap.put(0, seq0Map.get(lib));
            innerSeqMap.put(1, seq1Map.get(lib));

            parallelMeasurements.put(lib, innerParallelMap);
            sequentialMeasurements.put(lib, innerSeqMap);
        }
    }

    private static Map<LibraryInstance, List<File>> getInstanceFileMap(List<File> allFiles) {
        Map<LibraryInstance, List<File>> resultMap = new HashMap<>();
        for (File csvFile : allFiles) {
            LibraryInstance libInstance = LibraryInstance.fromDockerName(csvFile.getParentFile().getParentFile().getName());
            if (!resultMap.containsKey(libInstance)) {
                resultMap.put(libInstance, new LinkedList<>());
            }
            resultMap.get(libInstance).add(csvFile);
        }
        return resultMap;
    }

    private static List<File> collectCsvs(String path, int iteration) {
        List<File> csvFiles = new LinkedList<>();
        String iterationString = "Iteration-" + iteration;
        try {
            csvFiles.addAll(Files.find(Paths.get(path), 999, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().endsWith(".csv") && matchesIteration(p, iterationString)).map(Path::toFile).collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new RuntimeException("Provided path not found: " + path);
        }
        return csvFiles;
    }

    private static boolean matchesIteration(Path p, String iterationString) {
        return p.getParent().getParent().getParent().toFile().getName().contains(iterationString);
    }

    private static List<String> getMeasurementsOfCsv(File csvFile, boolean baseline) {
        List<String> matchingMeasurements = new LinkedList<>();
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(csvFile));
            String content = bufferedReader.readLine();
            while (content != null) {
                if (content.startsWith("BASELINE") && baseline) {
                    matchingMeasurements.add(content);
                } else if (content.startsWith("MODIFIED") && !baseline) {
                    matchingMeasurements.add(content);
                }
                content = bufferedReader.readLine();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Attempted to access non-existing result file");
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException ex) {

            }
        }
        return matchingMeasurements;
    }

    private enum MixType {
        SEQ,
        PARALLEL,
        CROSS
    }
}
