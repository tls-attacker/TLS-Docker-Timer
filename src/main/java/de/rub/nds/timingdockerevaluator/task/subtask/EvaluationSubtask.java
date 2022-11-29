package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.task.eval.RScriptManager;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.eval.VectorEvaluationTask;
import de.rub.nds.timingdockerevaluator.task.exception.UndetectableOracleException;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.timingdockerevaluator.util.DockerTargetManagement;
import de.rub.nds.timingdockerevaluator.util.HttpUtil;
import de.rub.nds.timingdockerevaluator.util.TimingBenchmark;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.exceptions.TransportHandlerConnectException;
import de.rub.nds.tlsattacker.core.exceptions.WorkflowExecutionException;
import de.rub.nds.tlsattacker.core.protocol.message.AlertMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.DefaultWorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTraceUtil;
import de.rub.nds.tlsattacker.core.workflow.action.GenericReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.TlsAction;
import de.rub.nds.tlsattacker.transport.TransportHandlerType;
import de.rub.nds.tlsattacker.transport.socket.SocketState;
import de.rub.nds.tlsattacker.transport.tcp.ClientTcpTransportHandler;
import de.rub.nds.tlsattacker.transport.tcp.proxy.TimingProxyClientTcpTransportHandler;
import de.rub.nds.tlsattacker.transport.tcp.timing.TimingClientTcpTransportHandler;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import de.rub.nds.tlsscanner.serverscanner.selector.ConfigFilter;
import de.rub.nds.tlsscanner.serverscanner.selector.ConfigFilterProfile;
import de.rub.nds.tlsscanner.serverscanner.selector.DefaultConfigProfile;
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
    private static final int MAX_FAILURES_IN_A_ROW = 15;
    private static final int UNDETECTABLE_LIMIT = 300;
    private static final int MAX_UNREACHABLE_IN_A_ROW_BEFORE_RESTART = 5;
    
    
    private final Map<String, List<Long>> runningMeasurements = new HashMap<>();
    private final Map<String, List<Long>> finishedMeasurements = new HashMap<>();
    protected int measurementsDone;
    protected int nextMaximum;
    private final String subtaskName;
    private final String targetName;
    private final EvaluationSubtaskReport report;
    protected final TimingDockerEvaluatorCommandConfig evaluationConfig;
    protected final EvaluationTask parentTask;
    
    private final int targetPort;
    private final String targetIp; 
    
    protected int subtaskWorkflowFails;
    
    protected ProtocolVersion version;
    protected CipherSuite cipherSuite;
    
    private ServerReport serverReport;
    private boolean switchedToRestarting = false;
    
    public EvaluationSubtask(String taskName, String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig, EvaluationTask parentTask) {
        this.subtaskName = taskName;
        this.targetName = targetName;
        this.report = new EvaluationSubtaskReport(taskName, targetName);
        this.targetIp = ip;
        this.targetPort = port;
        this.evaluationConfig = evaluationConfig;
        this.parentTask = parentTask;
        measurementsDone = 0;
        nextMaximum = evaluationConfig.getMeasurementsPerStep();
    }
    
    public abstract boolean isApplicable();
    
    public void adjustScope(ServerReport serverReport) {
        this.serverReport = serverReport;
    }
    
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
            int unreachableInARow = 0;
            for(int i = 0; i < executionPlan.length; i++) {
                try {
                    TimingBenchmark.print("Starting next measurement");
                    Long newMeasurement = measure(subtaskIdentifiers.get(executionPlan[i]));
                    TimingBenchmark.print("Obtained measurement");
                    addMeasurement(subtaskIdentifiers.get(executionPlan[i]), newMeasurement);
                    failedInARow = 0;
                    unreachableInARow = 0;
                } catch (WorkflowTraceFailedEarlyException ex) {
                    failedInARow++;
                    report.failedEarly();
                    LOGGER.error("WorkflowTrace failed early for {} - Target: {} will retry", getSubtaskName(), getTargetName());
                } catch (UndetectableOracleException ex) {
                    failedInARow++;
                    report.undetectableOracle();
                    LOGGER.error("Target {} send no alert and did not close for {}", getTargetName(), getSubtaskName());
                } catch (TransportHandlerConnectException connectException) {
                    unreachableInARow++;
                    failedInARow++;
                    LOGGER.error("Target {} was unreachable", getTargetName());
                    if(unreachableInARow == MAX_UNREACHABLE_IN_A_ROW_BEFORE_RESTART && (evaluationConfig.getTargetManagement() == DockerTargetManagement.RESTART_CONTAINTER || evaluationConfig.getTargetManagement() == DockerTargetManagement.RESTART_SERVER)) {
                        LOGGER.warn("Failed to reach {} {} times - switching to restarting mode", getTargetName(), MAX_UNREACHABLE_IN_A_ROW_BEFORE_RESTART);
                        switchedToRestarting = true;
                    }
                } catch (Exception ex) {
                    failedInARow++;
                    report.genericFailure();
                    LOGGER.error("Failed to measure {} - Target: {} will retry", getSubtaskName(), getTargetName(),ex);
                }
                
                if(failedInARow == MAX_FAILURES_IN_A_ROW || report.getUndetectableCount()> UNDETECTABLE_LIMIT) {
                        LOGGER.error("Measuring aborted due to frequent failures - Subtask {} - Target: {}", getSubtaskName(), getTargetName());
                        report.setFailed(true);
                        return report;
                }
            }
            measurementsDone += evaluationConfig.getMeasurementsPerStep();
            LOGGER.info("Subtask {} completed {} measurements for {}", getSubtaskName(), measurementsDone, getTargetName());
            RScriptManager scriptManager = new RScriptManager(baselineIdentifier, runningMeasurements, isCompareAllVectorCombinations(), parentTask);
            scriptManager.prepareFiles(getSubtaskName(), getTargetName());
            
            if(!evaluationConfig.isSkipR()) {
                List<VectorEvaluationTask> executedEvalTasks = scriptManager.testWithR(measurementsDone); 
                processAnalysisResults(subtaskIdentifiers, executedEvalTasks);
            }       
            if(subtaskIdentifiers.size() <= 1 || measurementsDone >= evaluationConfig.getTotalMeasurements()) {
                keepMeasuring = false;
            }
        } while(keepMeasuring);
        report.taskEnded();
        return report;
    }
    
    protected void addMeasurement(String identifier, Long measured) {
        if(!runningMeasurements.containsKey(identifier)) {
            runningMeasurements.put(identifier, new LinkedList<>());
        }
        runningMeasurements.get(identifier).add(measured);
    }
    
    private void processAnalysisResults(List<String> subtaskIdentifiers, List<VectorEvaluationTask> executedEvalTasks) {
        for(VectorEvaluationTask task: executedEvalTasks) {
            switch(task.getExitCode()) {
                case 12:
                    LOGGER.info("Found significant difference for {} in comparison to {} - Target: {}", task.getIdentifier1(), task.getIdentifier2(), getTargetName());
                    subtaskIdentifiers.remove(task.getIdentifier2());
                    finishedMeasurements.put(task.getIdentifier2(), runningMeasurements.get(task.getIdentifier2()));
                    report.appendFinding(task.getIdentifier1() + " vs " + task.getIdentifier2());
                    runningMeasurements.remove(task.getIdentifier2());
                    break;
                case 13:
                    LOGGER.info("Continuing - F1A for {} in comparison to {} - Target: {}", task.getIdentifier1(), task.getIdentifier2(), getTargetName());
                    break;
                case 14:
                    LOGGER.info("Continuing - No difference for {} in comparison to {} - Target: {}", task.getIdentifier1(), task.getIdentifier2(), getTargetName());
                    break;
                default:
                    LOGGER.error("R Script failed - status code {} - Target: {}", task.getExitCode(), getTargetName());
            }
        }
    }
    
    protected abstract List<String> getSubtaskIdentifiers();
    
    protected abstract String getBaselineIdentifier();
    
    protected abstract Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException, UndetectableOracleException;
    
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
        
        //ensure config filter is also applied if necessary
        if(!serverReport.getConfigProfileIdentifier().equals(DefaultConfigProfile.UNFILTERED.getIdentifier())) {
            for(ConfigFilterProfile filterProfile: DefaultConfigProfile.getTls12ConfigProfiles()) {
                if(filterProfile.getIdentifier().equals(serverReport.getConfigProfileIdentifier())) {
                    ConfigFilter.applyFilterProfile(config, filterProfile.getConfigFilterTypes());
                    break;
                }
            }
        }
        
        config.setDefaultClientSupportedCipherSuites(cipherSuite);
        config.setDefaultRunningMode(RunningModeType.CLIENT);
        config.getDefaultClientConnection().setHostname(targetIp);
        int dynamicPort = targetPort;
        if(evaluationConfig.getTargetManagement() == DockerTargetManagement.PORT_SWITCHING) {
            dynamicPort = HttpUtil.getCurrentPort(targetIp, targetPort);
        }
        config.getDefaultClientConnection().setPort(dynamicPort);
        config.getDefaultClientConnection().setProxyControlHostname("localhost");
        config.getDefaultClientConnection().setProxyControlPort(evaluationConfig.getProxyControlPort());
        config.getDefaultClientConnection().setProxyDataHostname("localhost");
        config.getDefaultClientConnection().setProxyDataPort(evaluationConfig.getProxyDataPort());
        config.getDefaultClientConnection().setTimeout(evaluationConfig.getTimeout());
        config.getDefaultClientConnection().setFirstTimeout(evaluationConfig.getTimeout());
        config.getDefaultClientConnection().setConnectionTimeout(evaluationConfig.getTimeout() + 1000);
        if(evaluationConfig.isUseProxy()) {
            config.getDefaultClientConnection().setTransportHandlerType(TransportHandlerType.TCP_PROXY_TIMING);
        } else {
            config.getDefaultClientConnection().setTransportHandlerType(TransportHandlerType.TCP_TIMING);
        }
        config.setWorkflowExecutorShouldClose(false);
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
        if (serverReport.getVersions() != null && !serverReport.getVersions().isEmpty()) {
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
    
    /*
        Note: GenericReceive would allow us to measure a possible time difference
        between an alert and a subsequent TCP close/rst. However, we are currently
        measuring the time between sending and receiving the first byte in response,
        which would hide this difference anyway. Hence, we stick to a specific
        ReceiveAction to gain a speedup.
    */
    protected void setSpecificReceiveAction(WorkflowTrace workflowTrace) {
        TlsAction lastAction = workflowTrace.getTlsActions().get(workflowTrace.getTlsActions().size() -1);
        if(lastAction instanceof GenericReceiveAction) {
            workflowTrace.getTlsActions().remove(lastAction);
            workflowTrace.addTlsAction(new ReceiveAction(new AlertMessage()));
        } else if(!(lastAction instanceof ReceiveAction)) {
            LOGGER.warn("Last Action for {} is not a ReceiveAction and not a GenericReceive");
        }
    }
    
    /*
     * Some implementations do not send an alert and do not close the connection.
     * Our tests are unable to exploit these even if an oracle may be present.
     */
    protected boolean oraclePossible(State executedState) {
        boolean gotAlert = WorkflowTraceUtil.didReceiveMessage(ProtocolMessageType.ALERT, executedState.getWorkflowTrace());
        SocketState socketState = (((ClientTcpTransportHandler) (executedState.getTlsContext().getTransportHandler())).getSocketState());
        
        boolean isClosed = socketState == SocketState.CLOSED || socketState == SocketState.IO_EXCEPTION || socketState == SocketState.TIMEOUT || socketState == SocketState.PEER_WRITE_CLOSED;
        return gotAlert || isClosed;
    }
    
    protected void postExecutionCheck(State executedState, WorkflowExecutor executor) throws UndetectableOracleException, WorkflowTraceFailedEarlyException {
        boolean workflowTraceSufficientlyExecuted = workflowTraceSufficientlyExecuted(executedState.getWorkflowTrace());
        boolean oracleDetectable = oraclePossible(executedState);
        executor.closeConnection();
        
        if(!workflowTraceSufficientlyExecuted) {
            throw new WorkflowTraceFailedEarlyException();
        } else if(!oracleDetectable) {
            throw new UndetectableOracleException();
        }
    }
    
    protected void prepareExecutor(WorkflowExecutor executor) {
        if(evaluationConfig.additionalContainerActionsRequired()) {
            executor.setBeforeTransportPreInitCallback(parentTask.getRestartCallable());
        }
    }
    
    protected void runExecutor(final State state) throws WorkflowTraceFailedEarlyException, UndetectableOracleException, WorkflowExecutionException {
        final WorkflowExecutor executor = (WorkflowExecutor) new DefaultWorkflowExecutor(state);
        prepareExecutor(executor);
        executor.executeWorkflow();
        postExecutionCheck(state, executor);
    }
    
    protected boolean isCompareAllVectorCombinations() {
        return false;
    }
    
    protected abstract boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace);
}
