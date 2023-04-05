package de.rub.nds.timingdockerevaluator.task;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.eval.RScriptManager;
import de.rub.nds.timingdockerevaluator.util.LibraryInstance;
import java.io.File;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RAnalyzerTask {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final TimingDockerEvaluatorCommandConfig evaluationConfig;
    
    private final File singleCsvFile;
    private final LibraryInstance assignedLibraryInstance;
    private final static String EVAL_SUFFIX = "-postEval-";

    public RAnalyzerTask(TimingDockerEvaluatorCommandConfig evaluationConfig, File singleCsvFile, LibraryInstance assignedLibraryInstance) {
        this.evaluationConfig = evaluationConfig;
        this.singleCsvFile = singleCsvFile;
        this.assignedLibraryInstance = assignedLibraryInstance;
    }  
    
    public RAnalyzerTask(TimingDockerEvaluatorCommandConfig evaluationConfig, File singleCsvFile) {
        this.evaluationConfig = evaluationConfig;
        this.singleCsvFile = singleCsvFile;
        this.assignedLibraryInstance = null;
    }
    
    public void execute() {
        if(alreadyAnalyzed()) {
            LOGGER.warn("Found pre-existing result for {} of {} - skipping", singleCsvFile.getName(), resolveParentName());
        } else {
            LOGGER.info("Starting eval for {} of {}", singleCsvFile.getName(), resolveParentName());
            evaluateWithR(singleCsvFile);
            LOGGER.info("Finished R-Eval for {} of {}", singleCsvFile.getName(), resolveParentName());
        }
    }

    private String resolveParentName() {
        return (assignedLibraryInstance != null) ? assignedLibraryInstance.getDockerName() : "generic CSV file";
    }

    public void evaluateWithR(File csvFile) {
        int exitCode = callR(csvFile);
        LOGGER.info("Result {} of {} == {}", singleCsvFile.getName(), resolveParentName(), exitCode);
    }

    public int callR(File csvFile) {
        try {
            return RScriptManager.testDetailedWithR(csvFile.getAbsolutePath(), EVAL_SUFFIX, evaluationConfig.getTotalMeasurements());
        } catch (InterruptedException | IOException ex) {
            LOGGER.error("Failed to run R script for {}", csvFile.getAbsolutePath(), ex);
            throw new RuntimeException();
        }
    }
    
    private boolean alreadyAnalyzed() {
        File analyzedFile = new File(singleCsvFile.getAbsolutePath() + "-postEval-.RDATA");
        return analyzedFile.exists();
    }
    
    
}
