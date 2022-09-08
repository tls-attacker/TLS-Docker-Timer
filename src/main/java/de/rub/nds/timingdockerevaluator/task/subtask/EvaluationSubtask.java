package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.execution.Main;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.transport.TransportHandlerType;
import de.rub.nds.tlsattacker.transport.tcp.proxy.TimingProxyClientTcpTransportHandler;
import de.rub.nds.tlsattacker.transport.tcp.timing.TimingClientTcpTransportHandler;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



public abstract class EvaluationSubtask {
    
    private final static Random notReallyRandom = new Random(System.currentTimeMillis());
    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean USE_TCP_PROXY = false;
    
    private Map<String, List<Long>> runningMeasurements = new HashMap<>();
    private Map<String, List<Long>> finishedMeasurements = new HashMap<>();
    protected int measurementsDone;
    protected int nextMaximum;
    private final String subtaskName;
    private final String targetName;
    private final EvaluationSubtaskReport report;
    private final TimingDockerEvaluatorCommandConfig evaluationConfig;
    
    private final int targetPort;
    private final String targetIp; 
    
    protected int subtaskWorkflowFails;
    
    protected ProtocolVersion version;
    protected CipherSuite cipherSuite;
    
    public EvaluationSubtask(String taskName, String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        this.subtaskName = taskName;
        this.targetName = targetName;
        this.report = new EvaluationSubtaskReport(taskName, targetName);
        this.targetIp = ip;
        this.targetPort = port;
        this.evaluationConfig = evaluationConfig;
        measurementsDone = 0;
        nextMaximum = evaluationConfig.getMeasurementsPerStep();
    }
    
    public abstract boolean isApplicable();
    
    public abstract void adjustScope(ServerReport serverReport);
    
    public EvaluationSubtaskReport evaluate() {
        LOGGER.info("Starting evaluation of {} - Target: {}", getSubtaskName(), getTargetName());
        List<String> subtaskIdentifiers = getSubtaskIdentifiers();
        report.setIdentifiers(subtaskIdentifiers);
        report.setCipherSuite(cipherSuite);
        report.setProtocolVersion(version);
        LOGGER.info("Subtask {} for {} has {} identifiers", getSubtaskName(), getTargetName(), subtaskIdentifiers.size());
        String baselineIdentifier = getBaselineIdentifier();
        boolean keepMeasuring = true;
        do {
            int[] executionPlan = getExecutionPlan(subtaskIdentifiers.size(), evaluationConfig.getMeasurementsPerStep());
            int failedInARow = 0;
            for(int i = 0; i < executionPlan.length; i++) {
                try {
                    Long newMeasurement = measure(subtaskIdentifiers.get(executionPlan[i]));
                    addMeasurement(subtaskIdentifiers.get(executionPlan[i]), newMeasurement);
                    failedInARow = 0;
                } catch (WorkflowTraceFailedEarlyException ex) {
                    failedInARow++;
                    LOGGER.error("WorkflowTrace failed early for {} - Target: {} will retry", getSubtaskName(), getTargetName());
                } catch (Exception ex) {
                    failedInARow++;
                    LOGGER.error("Failed to measure {} - Target: {} will retry", getSubtaskName(), getTargetName(),ex);
                }
                
                if(failedInARow == 100) {
                        LOGGER.error("Measuring failed 100 times in a row - aborting {} - Target: {}", getSubtaskName(), getTargetName());
                        report.setFailed(true);
                        return report;
                }
            }
            measurementsDone += evaluationConfig.getMeasurementsPerStep();
            LOGGER.info("Subtask {} completed {} measurements for {}", getSubtaskName(), measurementsDone, getTargetName());
            RScriptManager scriptManager = new RScriptManager(baselineIdentifier, runningMeasurements);
            scriptManager.prepareFiles(getSubtaskName(), getTargetName());
            Map<String, Integer> rOutputMap;
            
            if(!evaluationConfig.isSkipR()) {
                rOutputMap = scriptManager.testWithR(measurementsDone); 
                processAnalysisResults(subtaskIdentifiers, rOutputMap);
            }       
            if(subtaskIdentifiers.size() <= 1 || measurementsDone >= evaluationConfig.getTotalMeasurements()) {
                keepMeasuring = false;
            }
        } while(keepMeasuring);
        return report;
    }
    
    protected void addMeasurement(String identifier, Long measured) {
        if(!runningMeasurements.containsKey(identifier)) {
            runningMeasurements.put(identifier, new LinkedList<>());
        }
        runningMeasurements.get(identifier).add(measured);
    }
    
    private void processAnalysisResults(List<String> subtaskIdentifiers, Map<String, Integer> rOutputMap) {
        for(String identifier: rOutputMap.keySet()) {
            switch(rOutputMap.get(identifier)) {
                case 12:
                    LOGGER.info("Found significant difference for {} - Target: {}", identifier, getTargetName());
                    subtaskIdentifiers.remove(identifier);
                    finishedMeasurements.put(identifier, runningMeasurements.get(identifier));
                    report.appendFinding(identifier);
                    runningMeasurements.remove(identifier);
                    break;
                case 13:
                    LOGGER.info("Continuing - F1A for {} - Target: {}", identifier, getTargetName());
                    break;
                case 14:
                    LOGGER.info("Continuing - No difference for {} - Target: {}", identifier, getTargetName());
                    break;
                default:
                    LOGGER.error("R Script failed - status code {} - Target: {}", rOutputMap.get(identifier), getTargetName());
            }
        }
    }
    
    protected abstract List<String> getSubtaskIdentifiers();
    
    protected abstract String getBaselineIdentifier();
    
    protected abstract Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException;
    
    public Map<String, List<Long>> getRunningMeasurements() {
        return runningMeasurements;
    }
    
    private int[] getExecutionPlan(int identifierCount, int nextMeasurementsPerType) {
        int[] executionPlan = new int[identifierCount * nextMeasurementsPerType];
        for(int i = 0; i < executionPlan.length; i++) {
            executionPlan[i] = notReallyRandom.nextInt(identifierCount);
        }
        return executionPlan;
    }

    public String getSubtaskName() {
        return subtaskName;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public String getTargetIp() {
        return targetIp;
    }
    
    protected Config getBaseConfig(ProtocolVersion version, CipherSuite cipherSuite) {
        Config config = Config.createConfig();
        config.setDefaultSelectedCipherSuite(cipherSuite);
        if(cipherSuite.name().contains("ECDH")) {
            config.setAddEllipticCurveExtension(true);
        } else {
            config.setAddEllipticCurveExtension(false);
        }
        
        //ensure nice NamedGroup list
        config.setDefaultClientNamedGroups(Arrays.asList(NamedGroup.values()).stream()
                .filter(NamedGroup::isCurve)
                .filter(NamedGroup.getImplemented()::contains)
                .filter(group -> (group.isStandardCurve() && group.name().contains("SECP")) || group.isTls13())
                .collect(Collectors.toList()));
        
        //ensure nice SigHashAlgo list
        config.setDefaultClientSupportedSignatureAndHashAlgorithms(Arrays.asList(SignatureAndHashAlgorithm.values()).stream()
                .filter(SignatureAndHashAlgorithm.getImplemented()::contains)
                .filter(algo -> (!algo.name().contains("ANON") && !algo.name().contains("_NONE")))
                .collect(Collectors.toList()));
        
        config.setDefaultClientSupportedCipherSuites(cipherSuite);
        config.setDefaultRunningMode(RunningModeType.CLIENT);
        config.getDefaultClientConnection().setHostname(targetIp);
        config.getDefaultClientConnection().setPort(targetPort);
        config.getDefaultClientConnection().setProxyControlHostname("localhost");
        config.getDefaultClientConnection().setProxyControlPort(evaluationConfig.getProxyControlPort());
        config.getDefaultClientConnection().setProxyDataHostname("localhost");
        config.getDefaultClientConnection().setProxyDataPort(evaluationConfig.getProxyDataPort());
        config.getDefaultClientConnection().setTimeout(evaluationConfig.getTimeout());
        config.getDefaultClientConnection().setFirstTimeout(evaluationConfig.getTimeout());
        if(evaluationConfig.isUseProxy()) {
            config.getDefaultClientConnection().setTransportHandlerType(TransportHandlerType.TCP_PROXY_TIMING);
        } else {
            config.getDefaultClientConnection().setTransportHandlerType(TransportHandlerType.TCP_TIMING);
        }
        
        config.setHighestProtocolVersion(version);
        return config;
    }
    
    protected Long getMeasurement(final State state) {
        Long lastMeasurement;
        if (evaluationConfig.isUseProxy()) {
            lastMeasurement = ((TimingProxyClientTcpTransportHandler) state.getTlsContext().getTransportHandler()).getLastMeasurement();
        } else {
            lastMeasurement = ((TimingClientTcpTransportHandler) state.getTlsContext().getTransportHandler()).getLastMeasurement();
        }
        return lastMeasurement;
    }
    
    public ProtocolVersion determineVersion(ServerReport serverReport) {
        if (!serverReport.getVersions().isEmpty()) {
            if (serverReport.getVersions().contains(ProtocolVersion.TLS12)) {
                return ProtocolVersion.TLS12;
            } else if (serverReport.getVersions().contains(ProtocolVersion.TLS11)) {
                return ProtocolVersion.TLS11;
            } else if (serverReport.getVersions().contains(ProtocolVersion.TLS10)) {
                return ProtocolVersion.TLS10;
            }
        }
        return null;
    }
    
    protected abstract boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace);
}
