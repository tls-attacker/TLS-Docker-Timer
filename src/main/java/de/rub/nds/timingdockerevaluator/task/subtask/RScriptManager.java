package de.rub.nds.timingdockerevaluator.task.subtask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class RScriptManager {
    
    private static String outputFolder;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH-mm-ss");
    
    private final String baselineIdentifier;
    private final Map<String, List<Long>> measurements;
    private final Map<String, String> inputFileIdentifiers = new HashMap<>();
    
    public RScriptManager(String baselineIdentifier, Map<String, List<Long>> measurements) {
        this.baselineIdentifier = baselineIdentifier;
        this.measurements = measurements;
    }
    
    public void prepareFiles(String subtaskName, String targetName) {
        assureOutputFolderIsSet();
        inputFileIdentifiers.clear();
        for(String identifier: measurements.keySet()) {
            if(!baselineIdentifier.equals(identifier)) {
                String filePath = getOutputFolder() + "/" + targetName + "/" + subtaskName + "/" + baselineIdentifier + "vs" + identifier + ".csv";
                inputFileIdentifiers.put(filePath, identifier);
                File outFile = new File(filePath);
                outFile.getParentFile().mkdirs();
                BufferedWriter bufferedWriter;
                try {
                    bufferedWriter = new BufferedWriter(new FileWriter(outFile, false));
                    bufferedWriter.write("V1,V2");
                    bufferedWriter.newLine();
                    for(Long baselineMeasured: measurements.get(baselineIdentifier)) {
                        bufferedWriter.write("BASELINE, " + baselineMeasured);
                        bufferedWriter.newLine();
                    }
                    for(Long measured: measurements.get(identifier)) {
                        bufferedWriter.write("MODIFIED, " + measured);
                        bufferedWriter.newLine();
                    }
                    bufferedWriter.close();
                } catch(Exception ex) {
                    LOGGER.error("Failed to write files", ex);
                }
            }
        }
    }

    public static void assureOutputFolderIsSet() {
        if(getOutputFolder() == null) {
            LocalDateTime now = LocalDateTime.now();
            setOutputFolder("output-" + DATE_TIME_FORMAT.format(now));
        }
    }
    
    public Map<String, Integer> testWithR(int n) {
        Map<String, Integer> exitCodeMap = new HashMap<>();
        for(String file: inputFileIdentifiers.keySet()) {
            try {
                String[] commandArray = new String[]{"Rscript", "evaluateMeasurements.R", file, file + TIME_FORMAT.format(LocalDateTime.now()) + ".RDATA", "BASELINE", "MODIFIED", Integer.toString(n)};
                Process rProcess = Runtime.getRuntime().exec(commandArray);
                int exitCode = rProcess.waitFor();
                exitCodeMap.put(inputFileIdentifiers.get(file), exitCode);
            } catch (IOException | InterruptedException ex) {
                LOGGER.error("Failed to run RScript", ex);
            }
        }
        return exitCodeMap;
    }
     
    public Map<String, Integer> createDummyResult() {
        Map<String, Integer> exitCodeMap = new HashMap<>();
        for(String file: inputFileIdentifiers.keySet()) {
            exitCodeMap.put(inputFileIdentifiers.get(file), 14);
        }
        return exitCodeMap;
    }
    
    public static boolean rScriptGiven() {
        File rScriptFile = new File("evaluateMeasurements.R");
        return rScriptFile.exists();
    } 

    public static String getOutputFolder() {
        return outputFolder;
    }

    public static void setOutputFolder(String aOutputFolder) {
        outputFolder = aOutputFolder;
    }
}
