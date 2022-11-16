/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.task.subtask;

/**
 *
 * @author marcel
 */
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
