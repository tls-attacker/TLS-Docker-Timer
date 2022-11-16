package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.eval.RScriptManager;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author marcel
 */
public class SubtaskReportWriter {

    private static final Logger LOGGER = LogManager.getLogger();
    
    public static void writeReport(EvaluationSubtaskReport report, int run, boolean multipleRuns) {
        RScriptManager.assureOutputFolderIsSet();
        XMLEncoder encoder = null;
        File outputFile = new File(RScriptManager.getOutputFolder() + ((multipleRuns)? "/Iteration-" + run + "/": "") + "/" + report.getTargetName() + "-" + report.getTaskName());
        outputFile.getParentFile().mkdirs();
        try {
            encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(outputFile)));
        encoder.writeObject(report);
        encoder.close();
        } catch (Exception ex) {
            LOGGER.error("Failed to write subtask report", ex);
        }
    }
    
    public static EvaluationSubtaskReport readReport(File reportFile) {
        FileInputStream fileInStream = null;
        try {
            fileInStream = new FileInputStream(reportFile);
            XMLDecoder decoder = new XMLDecoder(fileInStream);
            return (EvaluationSubtaskReport)decoder.readObject();
        } catch (FileNotFoundException ex) {
            java.util.logging.Logger.getLogger(SubtaskReportWriter.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fileInStream.close();
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(SubtaskReportWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
}
