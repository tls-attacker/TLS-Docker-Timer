/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.util;

import de.rub.nds.timingdockerevaluator.task.eval.RAdditionalOutput;
import java.util.LinkedList;
import java.util.List;

public class RDataFileGroup {
    private final LibraryInstance libraryInstance;
    
    private final List<RAdditionalOutput> additionalRData = new LinkedList<>();

    public RDataFileGroup(LibraryInstance libraryInstance) {
        this.libraryInstance = libraryInstance;
    }

    public LibraryInstance getLibraryInstance() {
        return libraryInstance;
    }
    
    public void insertFile(RAdditionalOutput rOutput) {
        getAdditionalRData().add(rOutput);
    }

    public List<RAdditionalOutput> getAdditionalRData() {
        return additionalRData;
    }
    
    public RAdditionalOutput getOutputForVectorName(String vectorName) {
        for(RAdditionalOutput listed: additionalRData) {
            if(listed.getVectorName().equals(vectorName)) {
                return listed;
            }
        }
        return null;
    }
    
}
