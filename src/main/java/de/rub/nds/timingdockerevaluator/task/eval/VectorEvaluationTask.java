package de.rub.nds.timingdockerevaluator.task.eval;

public class VectorEvaluationTask {
    private final String identifier1;
    private final String identifier2;
    private final String filePath;
    private int exitCode;

    public VectorEvaluationTask(String identifier1, String identifier2, String filePath) {
        this.identifier1 = identifier1;
        this.identifier2 = identifier2;
        this.filePath = filePath;
    }

    public String getIdentifier1() {
        return identifier1;
    }

    public String getIdentifier2() {
        return identifier2;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public boolean isSamePlan(VectorEvaluationTask other) {
        return (this.getIdentifier1().equals(other.getIdentifier1()) && this.getIdentifier2().equals(other.getIdentifier2()))
                || (this.getIdentifier1().equals(other.getIdentifier2()) && this.getIdentifier2().equals(other.getIdentifier1()));
    }
    
}
