package de.rub.nds.timingdockerevaluator.util;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimingBenchmark {
    
    private static long lastTimestamp = 0;

    public static TimingDockerEvaluatorCommandConfig getEvaluationConfig() {
        return evaluationConfig;
    }

    public static void setEvaluationConfig(TimingDockerEvaluatorCommandConfig aEvaluationConfig) {
        evaluationConfig = aEvaluationConfig;
    }
    private static final Logger LOGGER = LogManager.getLogger();
    
    private static TimingDockerEvaluatorCommandConfig evaluationConfig;
    
    public static void print(String step) {
        if(getEvaluationConfig().isBenchmark()) {
            if(lastTimestamp > 0) {
                LOGGER.info("{} (+ {} ms)", step, System.currentTimeMillis() - lastTimestamp);
            } else {
                LOGGER.info("{} (Init)", step);
            }
            lastTimestamp = System.currentTimeMillis();
        }
        
    }
}
