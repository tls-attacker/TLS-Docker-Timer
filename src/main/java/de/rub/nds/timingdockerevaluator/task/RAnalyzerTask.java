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

    public RAnalyzerTask(TimingDockerEvaluatorCommandConfig evaluationConfig, File singleCsvFile, LibraryInstance assignedLibraryInstance) {
        this.evaluationConfig = evaluationConfig;
        this.singleCsvFile = singleCsvFile;
        this.assignedLibraryInstance = assignedLibraryInstance;
    }
    
    
    
    
    public void execute() {
        LOGGER.info("Starting eval for {} of {}", singleCsvFile.getName(), assignedLibraryInstance.getDockerName());
        evaluateWithR(singleCsvFile);
        LOGGER.info("Finished R-Eval for {} of {}", singleCsvFile.getName(), assignedLibraryInstance.getDockerName());
    }

    public void evaluateWithR(File csvFile) {
        int exitCode = callR(csvFile);
        LOGGER.info("Result {} of {} == {}", singleCsvFile.getName(), assignedLibraryInstance.getDockerName(), exitCode);
    }

    public int callR(File csvFile) {
        try {
            return RScriptManager.testDetailedWithR(csvFile.getAbsolutePath(), "-postEval-", evaluationConfig.getTotalMeasurements());
        } catch (InterruptedException | IOException ex) {
            LOGGER.error("Failed to run R script for {}", csvFile.getAbsolutePath(), ex);
            throw new RuntimeException();
        }
    }
    
    
}
