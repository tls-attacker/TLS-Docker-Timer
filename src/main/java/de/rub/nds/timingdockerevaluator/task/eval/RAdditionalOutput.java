package de.rub.nds.timingdockerevaluator.task.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * @author marcel
 */
public class RAdditionalOutput {
    
    private final String vectorName;
    private final int highestPower;
    private final int highestF1a;
    private final int bigestDecisionDifferenceIndex;

    public RAdditionalOutput(String vectorName, int highestF1a, int highestPower, int bigestDecisionDifferenceIndex) {
        this.vectorName = vectorName;
        this.highestPower = highestPower;
        this.highestF1a = highestF1a;
        this.bigestDecisionDifferenceIndex = bigestDecisionDifferenceIndex;
    }
    
    public static RAdditionalOutput fromFile(File additionalROutput) {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(additionalROutput));
            String content = bufferedReader.readLine();
            // Additional, maxF1a, maxPower, decisionIndex
            String[] parts = content.split(",");
            return new RAdditionalOutput(additionalROutput.getName().replace(".RDATA.add", ""), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IOException ex) {
            throw new RuntimeException("Attempted to access non-existing result file");
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException ex) {
                
            }
        }
    }

    public int getHighestPower() {
        return highestPower;
    }

    public int getHighestF1a() {
        return highestF1a;
    }

    public int getBigestDecisionDifferenceIndex() {
        return bigestDecisionDifferenceIndex;
    }

    public String getVectorName() {
        return vectorName;
    }
    
}
