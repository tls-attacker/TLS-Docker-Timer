package de.rub.nds.timingdockerevaluator.util;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author marcel
 */
public class LibraryResult {
    
    private final LibraryInstance libraryInstance;
    private final String subtask;
    private boolean failed;
    private boolean finding;
    private int highestPower = -1;
    private int highestF1a = -1;
    
    private final List<String> findings;

    public LibraryResult(LibraryInstance libraryInstance, String subtask) {
        this.libraryInstance = libraryInstance;
        this.subtask = subtask;
        findings = new LinkedList<>();
    }
    
    public void populateFromDirectory(String path) {
        
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isFinding() {
        return finding;
    }

    public void setFinding(boolean finding) {
        this.finding = finding;
    }

    public String getSubtask() {
        return subtask;
    }

    public List<String> getFindings() {
        return findings;
    }
    
    public String getFinalResult() {
        if(failed) {
            return TestResultType.ABORTED.name();
        } else if(finding) {
            return TestResultType.VULNERABLE.name();
        } else {
            return TestResultType.SAFE.name();
        }
    }

    public int getHighestPower() {
        return highestPower;
    }

    public void setHighestPower(int highestPower) {
        this.highestPower = highestPower;
    }

    public int getHighestF1a() {
        return highestF1a;
    }

    public void setHighestF1a(int highestF1a) {
        this.highestF1a = highestF1a;
    }

    
}
