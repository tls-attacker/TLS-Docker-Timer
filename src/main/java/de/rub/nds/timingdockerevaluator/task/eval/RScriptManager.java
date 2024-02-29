package de.rub.nds.timingdockerevaluator.task.eval;

import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class RScriptManager {
    
    public static String R_SCRIPT_FILENAME = "RTLF.R";
    public static final String R_SCRIPT_QUANTILE_EXTRACTOR = "extractMaxQuantileDiffCyclesToNano.R";  //"extractQuantileDetails.R"
    public static final String R_QUANTILE_DETAILS_FILE = "rQuantileDetails.tmp";
    
    private static String outputFolder;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH-mm-ss");
    
    private final String baselineIdentifier;
    private final Map<String, List<Long>> measurements;
    private final List<VectorEvaluationTask> vectorEvaluationTasks = new LinkedList<>();
    private final boolean compareAllCombinations;
    private EvaluationTask parentTask;
    
    public RScriptManager(String baselineIdentifier, Map<String, List<Long>> measurements, boolean compareAllCombinations, EvaluationTask parentTask) {
        this.baselineIdentifier = baselineIdentifier;
        this.measurements = measurements;
        this.compareAllCombinations = compareAllCombinations;
        this.parentTask = parentTask;
    }
    
    public void prepareFiles(String subtaskName, String targetName) {
        assureOutputFolderIsSet();
        vectorEvaluationTasks.clear();
        for(String identifier: measurements.keySet()) {
            if(compareAllCombinations) {
               prepareFilesToCompareAllCombinations(subtaskName, targetName, identifier);
            } else {
               prepareFilesToCompareToBaseline(subtaskName, targetName, identifier); 
            }
            
        }
    }
    
    public void prepareExtendingFiles(String subtaskName, String targetName) {
        assureOutputFolderIsSet();
        vectorEvaluationTasks.clear();
        for(String identifier: measurements.keySet()) {
            if(compareAllCombinations) {
               extendFilesToCompareAllCombinations(subtaskName, targetName, identifier);
            } else {
               extendFilesToCompareToBaseline(subtaskName, targetName, identifier); 
            }
            
        }
    }
    
    private void prepareFilesToCompareAllCombinations(String subtaskName, String targetName, String identifier) {
        for(String secondIdentifier: measurements.keySet()) {
            String filePath = getBaseResultPath(targetName, subtaskName) + identifier + "vs" + secondIdentifier + ".csv";
            VectorEvaluationTask newTask = new VectorEvaluationTask(identifier, secondIdentifier, filePath);
            if(!secondIdentifier.equals(identifier) && !vectorEvaluationTasks.stream().anyMatch(newTask::isSamePlan)) {
                vectorEvaluationTasks.add(newTask);
                writeComparisonFile(newTask.getFilePath(), measurements.get(newTask.getIdentifier1()), measurements.get(newTask.getIdentifier2()));
            }
        }
    }
    
    private void prepareFilesToCompareToBaseline(String subtaskName, String targetName, String identifier) {
        if(!baselineIdentifier.equals(identifier)) {
                String filePath = getBaseResultPath(targetName, subtaskName) + baselineIdentifier + "vs" + identifier + ".csv";
                vectorEvaluationTasks.add(new VectorEvaluationTask(baselineIdentifier, identifier, filePath));
                writeComparisonFile(filePath, measurements.get(baselineIdentifier), measurements.get(identifier));
        }
    }

    private String getBaseResultPath(String targetName, String subtaskName) {
        return getOutputFolder() + "/" + targetName + "/" + subtaskName + "/";
    }

    private void writeComparisonFile(String filePath, List<Long> measurementsVector1, List<Long> measurementsVector2) {
        File outFile = new File(filePath);
        outFile.getParentFile().mkdirs();
        BufferedWriter bufferedWriter;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(outFile, false));
            bufferedWriter.write("V1,V2");
            bufferedWriter.newLine();
            for(Long baselineMeasured: measurementsVector1) {
                bufferedWriter.write("BASELINE, " + baselineMeasured);
                bufferedWriter.newLine();
            }
            for(Long measured: measurementsVector2) {
                bufferedWriter.write("MODIFIED, " + measured);
                bufferedWriter.newLine();
            }
            bufferedWriter.close();
        } catch(Exception ex) {
            LOGGER.error("Failed to write files", ex);
        }
    }
    
    private void extendFilesToCompareAllCombinations(String subtaskName, String targetName, String identifier) {
        for(String secondIdentifier: measurements.keySet()) {
            String filePath = getBaseResultPath(targetName, subtaskName) + identifier + "vs" + secondIdentifier + ".csv";
            VectorEvaluationTask newTask = new VectorEvaluationTask(identifier, secondIdentifier, filePath);
            if(!secondIdentifier.equals(identifier) && !vectorEvaluationTasks.stream().anyMatch(newTask::isSamePlan)) {
                vectorEvaluationTasks.add(newTask);
                extendComparisonFile(newTask.getFilePath(), measurements.get(newTask.getIdentifier1()), measurements.get(newTask.getIdentifier2()));
            }
        }
    }
    
    private void extendFilesToCompareToBaseline(String subtaskName, String targetName, String identifier) {
        if(!baselineIdentifier.equals(identifier)) {
                String filePath = getBaseResultPath(targetName, subtaskName) + baselineIdentifier + "vs" + identifier + ".csv";
                vectorEvaluationTasks.add(new VectorEvaluationTask(baselineIdentifier, identifier, filePath));
                extendComparisonFile(filePath, measurements.get(baselineIdentifier), measurements.get(identifier));
        }
    }
    
    
    private void extendComparisonFile(String filePath, List<Long> measurementsVector1, List<Long> measurementsVector2) {
        File outFile = new File(filePath);
        boolean writeHeader = false;
        if(!outFile.exists()) {
            writeHeader = true;
        }
        outFile.getParentFile().mkdirs();
        BufferedWriter bufferedWriter;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(outFile, true));
            if(writeHeader) {
                bufferedWriter.write("V1,V2");
                bufferedWriter.newLine();
            }
            for(Long baselineMeasured: measurementsVector1) {
                bufferedWriter.write("BASELINE, " + baselineMeasured);
                bufferedWriter.newLine();
            }
            for(Long measured: measurementsVector2) {
                bufferedWriter.write("MODIFIED, " + measured);
                bufferedWriter.newLine();
            }
            bufferedWriter.close();
        } catch(Exception ex) {
            LOGGER.error("Failed to write files", ex);
        }
    }
    
    

    public static void assureOutputFolderIsSet() {
        if(getOutputFolder() == null) {
            LocalDateTime now = LocalDateTime.now();
            setOutputFolder("output-" + DATE_TIME_FORMAT.format(now));
        }
    }
    
    public List<VectorEvaluationTask> testWithR(int n) {
        List<VectorEvaluationTask> executedTasks = new LinkedList<>(vectorEvaluationTasks);
        for(VectorEvaluationTask taskToExecute:  executedTasks) {
            try {
                String file = taskToExecute.getFilePath();
                int exitCode = testSingleFileWithR(file, n);
                taskToExecute.setExitCode(exitCode);
            } catch (IOException | InterruptedException ex) {
                LOGGER.error("Failed to run RScript", ex);
            }
        }
        return executedTasks;
    }

    public static int testSingleFileWithR(String file, int n) throws IOException, InterruptedException {
        return testDetailedWithR(file, TIME_FORMAT.format(LocalDateTime.now()), n);
    }
    
    public static int testDetailedWithR(String file, String rDataSpecifier, int n) throws IOException, InterruptedException {
        String rDataPath = file + rDataSpecifier + ".RDATA";
        String[] commandArray = new String[]{"Rscript", R_SCRIPT_FILENAME, file, rDataPath, Integer.toString(n)};
        Process rProcess = Runtime.getRuntime().exec(commandArray);
        int exitCode = rProcess.waitFor();
        if(exitCode == 1) {
            LOGGER.warn("R-Script failed using command {}", Arrays.asList(commandArray).stream().collect(Collectors.joining(" ")));
        }
        return exitCode;
    }
    
    public static int extractQuantileDetails(String file) {
        try {
            String[] commandArray = new String[]{"Rscript", R_SCRIPT_QUANTILE_EXTRACTOR, file, R_QUANTILE_DETAILS_FILE};
            Process rProcess = Runtime.getRuntime().exec(commandArray);
            int code = rProcess.waitFor();
            if(code > 0) {
                LOGGER.error(new BufferedReader(new InputStreamReader(rProcess.getInputStream())).readLine());
            }
            return code;
        } catch (IOException | InterruptedException ex) {
            LOGGER.error("Failed to run RScript", ex);
        }
        return 1;
    }
    
    
    public static boolean rScriptGiven() {
        File rScriptFile = new File(R_SCRIPT_FILENAME);
        return rScriptFile.exists();
    } 

    public static String getOutputFolder() {
        return outputFolder;
    }

    public static void setOutputFolder(String aOutputFolder) {
        outputFolder = aOutputFolder;
    }
}
