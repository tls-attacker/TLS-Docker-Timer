package de.rub.nds.timingdockerevaluator.task.subtask;

public enum SubtaskNames {
    
    BLEICHENBACHER("Bleichenbacher"),
    PADDING_ORACLE("PaddingOracle"),
    LUCKY13("Lucky13");
    
    private final String camelCaseName;
    private SubtaskNames(String camelCaseName) {
        this.camelCaseName = camelCaseName;
    }

    public String getCamelCaseName() {
        return camelCaseName;
    }
    
    
}
