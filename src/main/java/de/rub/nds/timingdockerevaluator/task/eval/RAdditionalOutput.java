package de.rub.nds.timingdockerevaluator.task.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author marcel
 */
public class RAdditionalOutput implements RPostAnalyzedInfo {
    
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final String vectorName;
    
    private final boolean oldFormat;
    private int highestPower;
    private int highestF1a;
    private int bigestDecisionDifferenceIndex;
    
    private int[] quantilesWithDifference;

    public RAdditionalOutput(String vectorName, int highestF1a, int highestPower, int bigestDecisionDifferenceIndex) {
        this.vectorName = vectorName;
        this.highestPower = highestPower;
        this.highestF1a = highestF1a;
        this.bigestDecisionDifferenceIndex = bigestDecisionDifferenceIndex;
        this.oldFormat = true;
    }
    
    public RAdditionalOutput(String vectorName, int[] quantilesWithDifference) {
        this.vectorName = vectorName;
        this.oldFormat = false;
        this.quantilesWithDifference = quantilesWithDifference;
    }
    
    public static RAdditionalOutput fromFile(File additionalROutput) {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(additionalROutput));
            String content = bufferedReader.readLine();
            // Additional, maxF1a, maxPower, decisionIndex
            String[] parts = content.split(",");
            if(parts[0].equals("AdditionalV2")) {
                int[] quantilesWithDifferences = new int[parts.length - 1];
                for(int i = 1; i < parts.length; i++) {
                    quantilesWithDifferences[i - 1] = Integer.valueOf(parts[i]);
                }
                return new RAdditionalOutput(additionalROutput.getName().replace(".csv-postEval-.RDATA.add", ""), quantilesWithDifferences);
            } else if(parts[0].equals("Additional")){
                LOGGER.warn("Found deprecated additional data created with old R-Script!");
                return new RAdditionalOutput(additionalROutput.getName().replace(".csv-postEval-.RDATA.add", ""), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } else {
                throw new IllegalArgumentException("Found invalid additional data");
            }
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
    
   public int[] getQuantilesWithDifference() {
        return quantilesWithDifference;
    }

    public void setQuantilesWithDifference(int[] quantilesWithDifference) {
        this.quantilesWithDifference = quantilesWithDifference;
    }

    public boolean isOldFormat() {
        return oldFormat;
    }
    
}
