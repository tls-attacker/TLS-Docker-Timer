/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.rub.nds.timingdockerevaluator.util;

import de.rub.nds.timingdockerevaluator.task.eval.RAdditionalOutput;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;

public class RDataFileGroup {
    
    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger();
    
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
        RAdditionalOutput identifiedOutput = null;
        for(RAdditionalOutput listed: additionalRData) {
            if(listed.getVectorName().equals(vectorName)) {
                if(identifiedOutput == null) {
                    identifiedOutput = listed;
                } else {
                    LOGGER.warn("Found multiple results for vector " + vectorName + " of library " + libraryInstance.getDockerName());
                }
            }
        }
        return identifiedOutput;
    }
    
}
