package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import java.util.LinkedList;
import java.util.List;

public class EvaluationSubtaskReport {
    private  String taskName;
    private  String targetName;
    private  List<String> withDifference = new LinkedList<>();
    private  List<String> executedIdentifiers = new LinkedList<>();
    private CipherSuite cipherSuite;
    private ProtocolVersion protocolVersion;
    private boolean failed = false;

    public EvaluationSubtaskReport() {
    }
    
    
    
    public EvaluationSubtaskReport(String taskName, String targetName) {
        this.taskName = taskName;
        this.targetName = targetName;
    }
    
    
    public void appendFinding(String identifier) {
        getWithDifference().add(identifier);
    }
    
    public void setIdentifiers(List<String> identifiers) {
        setExecutedIdentifiers(identifiers);
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTargetName() {
        return targetName;
    }

    public List<String> getWithDifference() {
        return withDifference;
    }

    public List<String> getExecutedIdentifiers() {
        return executedIdentifiers;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isFailed() {
        return failed;
    }

    public CipherSuite getCipherSuite() {
        return cipherSuite;
    }

    public void setCipherSuite(CipherSuite cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public void setWithDifference(List<String> withDifference) {
        this.withDifference = withDifference;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public void setExecutedIdentifiers(List<String> executedIdentifiers) {
        this.executedIdentifiers = new LinkedList<>(executedIdentifiers);
    }

}
