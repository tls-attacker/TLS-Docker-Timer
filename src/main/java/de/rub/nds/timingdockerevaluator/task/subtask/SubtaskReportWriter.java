package de.rub.nds.timingdockerevaluator.task.subtask;

import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author marcel
 */
public class SubtaskReportWriter {

    private static final Logger LOGGER = LogManager.getLogger();
    
    public static void writeReport(EvaluationSubtaskReport report) {
        RScriptManager.assureOutputFolderIsSet();
        XMLEncoder encoder = null;
        try {
            encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(RScriptManager.getOutputFolder() + "/" + report.getTargetName() + "-" + report.getTaskName())));
        encoder.writeObject(report);
        encoder.close();
        } catch (Exception ex) {
            LOGGER.error("Failed to write subtask report", ex);
        }
    }
}
