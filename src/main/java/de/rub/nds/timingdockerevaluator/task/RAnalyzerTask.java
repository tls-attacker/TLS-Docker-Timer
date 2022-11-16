/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.task;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.eval.RScriptManager;
import de.rub.nds.timingdockerevaluator.util.CsvFileGroup;
import java.io.File;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RAnalyzerTask {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final CsvFileGroup resultFileGroup;
    private final TimingDockerEvaluatorCommandConfig evaluationConfig;

    public RAnalyzerTask(CsvFileGroup resultFileGroup, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        this.resultFileGroup = resultFileGroup;
        this.evaluationConfig = evaluationConfig;
    }
    
    
    
    public void execute() {
        LOGGER.info("Starting BB eval for {}", getResultFileGroup().getLibraryInstance().getDockerName());
        for(File bbFile : getResultFileGroup().getBleichenbacherFiles()) {
            evaluateWithR(bbFile);
        }
        LOGGER.info("Starting PO eval for {}", getResultFileGroup().getLibraryInstance().getDockerName());
        for(File poFile : getResultFileGroup().getPaddingOracleFiles()) {
            evaluateWithR(poFile);
        }
        LOGGER.info("Starting LUCKY13 eval for {}", getResultFileGroup().getLibraryInstance().getDockerName());
        for(File lucky13File : getResultFileGroup().getLucky13Files()) {
            evaluateWithR(lucky13File);
        }

        LOGGER.info("Finished R-Eval for {}", getResultFileGroup().getLibraryInstance().getDockerName());
    }

    public void evaluateWithR(File csvFile) {
        int exitCode = callR(csvFile);
        LOGGER.info(getResultFileGroup().getLibraryInstance().getDockerName() + "+" + csvFile.getName() + "==" + exitCode);
    }

    public int callR(File bbFile) {
        try {
            return RScriptManager.testDetailedWithR(bbFile.getAbsolutePath(), "-postEval-", evaluationConfig.getTotalMeasurements());
        } catch (InterruptedException | IOException ex) {
            LOGGER.error("Failed to run R script for {}", bbFile.getAbsolutePath(), ex);
            throw new RuntimeException();
        }
    }

    public CsvFileGroup getResultFileGroup() {
        return resultFileGroup;
    }
    
    
}
