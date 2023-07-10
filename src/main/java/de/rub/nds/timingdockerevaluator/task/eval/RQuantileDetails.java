package de.rub.nds.timingdockerevaluator.task.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class RQuantileDetails implements RPostAnalyzedInfo {
    private final String vectorName;
    private final String details;

    public RQuantileDetails(String vectorName, String details) {
        this.vectorName = vectorName;
        this.details = details;
    }

    @Override
    public String getVectorName() {
        return vectorName;
    }

    public String getDetails() {
        return details;
    }
    
    public static RQuantileDetails fromFile(File path) {
        int rCode = RScriptManager.extractQuantileDetails(path.getAbsolutePath());
        if(rCode != 0) {
            throw new RuntimeException("Failed to run R script for extraction");
        }
        
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(RScriptManager.R_QUANTILE_DETAILS_FILE));
            String content = bufferedReader.readLine();
            return new RQuantileDetails(path.getName().replace(".csv-postEval-.RDATA",""), content);
        } catch (IOException ex) {
            throw new RuntimeException("Attempted to access non-existing result file");
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException ex) {
                
            }
        }
    }
    
}
