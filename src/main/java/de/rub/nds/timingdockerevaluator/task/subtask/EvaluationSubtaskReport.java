package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class EvaluationSubtaskReport {

    
    private  String taskName;
    private  String targetName;
    private  List<String> withDifference = new LinkedList<>();
    private  List<String> executedIdentifiers = new LinkedList<>();
    private int genericFailureCount;
    private int failedEarlyCount;
    private int undetectableCount;
    private Map<String, Integer> undetectablePerVector = new HashMap<>();
    private CipherSuite cipherSuite;
    private ProtocolVersion protocolVersion;
    private boolean failed = false;
    private boolean undetectable = false;
    private long startTimestamp = System.currentTimeMillis();
    private long endTimestamp;
    private long duration;
    
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
        if(failed) {
            taskEnded();
        }
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
    
     public int getGenericFailureCount() {
        return genericFailureCount;
    }

    public void setGenericFailureCount(int genericFailureCount) {
        this.genericFailureCount = genericFailureCount;
    }

    public int getFailedEarlyCount() {
        return failedEarlyCount;
    }

    public void setFailedEarlyCount(int failedEarlyCount) {
        this.failedEarlyCount = failedEarlyCount;
    }
    
    public int getUndetectableCount() {
        return undetectableCount;
    }

    public void setUndetectableCount(int undetectableCount) {
        this.undetectableCount = undetectableCount;
    }

    public void failedEarly() {
        failedEarlyCount++;
    }
    
    public void genericFailure() {
        genericFailureCount++;
    }
    
    public void undetectableOracle(String vectorIdentifier) {
        undetectablePerVector.computeIfAbsent(vectorIdentifier, identifier -> 0);
        undetectablePerVector.put(vectorIdentifier, undetectablePerVector.get(vectorIdentifier) + 1);
        undetectableCount++;
    }
    
    public void taskEnded() {
        setEndTimestamp(System.currentTimeMillis());
        setDuration(getEndTimestamp() - getStartTimestamp());
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public Map<String, Integer> getUndetectablePerVector() {
        return undetectablePerVector;
    }

    public void setUndetectablePerVector(Map<String, Integer> undetectablePerVector) {
        this.undetectablePerVector = undetectablePerVector;
    }

    public boolean isUndetectable() {
        return undetectable;
    }

    public void setUndetectable(boolean undetectable) {
        this.undetectable = undetectable;
    }
}
