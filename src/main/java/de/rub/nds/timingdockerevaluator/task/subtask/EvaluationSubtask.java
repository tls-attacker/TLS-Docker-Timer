package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.modifiablevariable.util.Modifiable;
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
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.exceptions.TransportHandlerConnectException;
import de.rub.nds.tlsattacker.core.exceptions.WorkflowExecutionException;
import de.rub.nds.tlsattacker.core.protocol.ProtocolMessage;
import de.rub.nds.tlsattacker.core.protocol.message.AlertMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateRequestMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateVerifyMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.DefaultWorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTraceUtil;
import de.rub.nds.tlsattacker.core.workflow.action.GenericReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendAction;
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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class EvaluationSubtask {
    
    private static String lastMemoryFootprint = "";

    private final static Random notReallyRandom = new Random(System.currentTimeMillis());
    protected static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_FAILURES_IN_A_ROW = 30;
    private static final int UNDETECTABLE_LIMIT = 150;
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
    
    public void bloat() {
        List<String> subtaskIdentifiers = prepareSubtask();
        Random soRandom = new Random();
        for(String identifier : subtaskIdentifiers) {
            for(int i = 0; i < evaluationConfig.getTotalMeasurements(); i++) {
                addMeasurement(identifier, soRandom.nextLong());
            }
        }
    }

    public EvaluationSubtaskReport evaluate() {
        LOGGER.info("Starting evaluation of {} - Target: {}", getSubtaskName(), getTargetName());
        List<String> subtaskIdentifiers = prepareSubtask();
        LOGGER.info("Subtask {} for {} has {} identifiers", getSubtaskName(), getTargetName(), subtaskIdentifiers.size());
        String baselineIdentifier = getBaselineIdentifier();
        boolean keepMeasuring = true;
        do {
            int[] executionPlan = getExecutionPlan(subtaskIdentifiers.size(), evaluationConfig.getMeasurementsPerStep());
            int failedInARow = 0;
            int unreachableInARow = 0;
            for (int i = 0; i < executionPlan.length;) {
                int nextIndentifier = executionPlan[i];
                printProgress(i, subtaskIdentifiers.size());
                try {
                    TimingBenchmark.print("Starting next measurement");
                    //System.gc();
                    Long newMeasurement = measure(subtaskIdentifiers.get(nextIndentifier));
                    TimingBenchmark.print("Obtained measurement");
                    addMeasurement(subtaskIdentifiers.get(nextIndentifier), newMeasurement);
                    i++;
                    measurementsDone++;
                    failedInARow = 0;
                    unreachableInARow = 0;
                } catch (WorkflowTraceFailedEarlyException ex) {
                    failedInARow++;
                    report.failedEarly();
                    LOGGER.error("WorkflowTrace failed early for {} - Target: {} will retry", getSubtaskName(), getTargetName());
                } catch (UndetectableOracleException ex) {
                    failedInARow++;
                    report.undetectableOracle(subtaskIdentifiers.get(nextIndentifier));
                    LOGGER.warn("Target {} send no alert and did not close for vector {} of {}", getTargetName(), subtaskIdentifiers.get(nextIndentifier), getSubtaskName());
                } catch (TransportHandlerConnectException connectException) {
                    unreachableInARow++;
                    failedInARow++;
                    LOGGER.error("Target {} was unreachable", getTargetName());
                    if (unreachableInARow == MAX_UNREACHABLE_IN_A_ROW_BEFORE_RESTART && (evaluationConfig.getTargetManagement() == DockerTargetManagement.RESTART_CONTAINTER || evaluationConfig.getTargetManagement() == DockerTargetManagement.RESTART_SERVER)) {
                        LOGGER.warn("Failed to reach {} {} times - switching to restarting mode", getTargetName(), MAX_UNREACHABLE_IN_A_ROW_BEFORE_RESTART);
                        switchedToRestarting = true;
                    }
                } catch (Exception ex) {
                    failedInARow++;
                    report.genericFailure();
                    LOGGER.error("Failed to measure {} - Target: {} will retry", getSubtaskName(), getTargetName(), ex);
                }

                if (failedInARow == MAX_FAILURES_IN_A_ROW && !evaluationConfig.isNeverStop()) {
                    LOGGER.error("Measuring aborted due to frequent failures - Subtask {} - Target: {}", getSubtaskName(), getTargetName());
                    report.setFailed(true);
                    return report;
                } else if (report.getUndetectableCount() > UNDETECTABLE_LIMIT && !evaluationConfig.isNeverStop()) {
                    LOGGER.error("Measuring aborted since socket was {} times not closed and no alert was sent - Subtask {} - Target: {}", UNDETECTABLE_LIMIT, getSubtaskName(), getTargetName());
                    report.setFailed(true);
                    report.setUndetectable(true);
                    return report;
                } else if(failedInARow > MAX_FAILURES_IN_A_ROW / 2) {
                    LOGGER.warn("So far, there have been {} consecutive failures.", failedInARow);
                    if(evaluationConfig.isManagedTarget()) {
                        LOGGER.warn("Attempting to restart container.");
                        parentTask.restartContainer();
                    }
                }
            }
            LOGGER.info("Subtask {} completed {} measurements for {}", getSubtaskName(), measurementsDone, getTargetName());
            RScriptManager scriptManager = new RScriptManager(baselineIdentifier, runningMeasurements, isCompareAllVectorCombinations(), parentTask);
            if(evaluationConfig.isWriteInEachStep()) {
                LOGGER.info("Writing sub results for subtask {}", getSubtaskName());
                scriptManager.prepareExtendingFiles(getSubtaskName(), getTargetName());
                resetMeasurements();
            } else {
               scriptManager.prepareFiles(getSubtaskName(), getTargetName()); 
            }

            if (!evaluationConfig.isSkipR()) {
                List<VectorEvaluationTask> executedEvalTasks = scriptManager.testWithR(measurementsDone);
                processAnalysisResults(subtaskIdentifiers, executedEvalTasks);
            }
            if (subtaskIdentifiers.size() <= 1 || measurementsDone >= evaluationConfig.getTotalMeasurements() * subtaskIdentifiers.size()) {
                keepMeasuring = false;
            }
        } while (keepMeasuring);
        report.taskEnded();
        // results have been written, remove them from RAM
        resetMeasurements();
        return report;
    }

    private void printProgress(int i, int subtaskIdentifierCount) {
        if(i % 1000 == 0 && measurementsDone > 0) {
            long timeSpent = System.currentTimeMillis() - report.getStartTimestamp();
            double timePerMeasurement = (double)(timeSpent / measurementsDone);
            double remainingTime = timePerMeasurement * (evaluationConfig.getTotalMeasurements() * subtaskIdentifierCount - measurementsDone);
            LOGGER.info("Progess: {}/{} for {} in subtask {} (Expected to finish in {})", measurementsDone, evaluationConfig.getTotalMeasurements() * subtaskIdentifierCount, getTargetName(), getSubtaskName(), getReadableTime(remainingTime));
        }
    }

    private List<String> prepareSubtask() {
        List<String> subtaskIdentifiers = getSubtaskIdentifiers();
        report.setIdentifiers(subtaskIdentifiers);
        report.setCipherSuite(cipherSuite);
        report.setProtocolVersion(version);
        return subtaskIdentifiers;
    }
    
    private String getReadableTime(double nanos){
        long tempSec    = (long) nanos/1000;
        long sec        = tempSec % 60;
        long min        = (tempSec /60) % 60;
        long hour       = (tempSec /(60*60)) % 24;
        long day        = (tempSec / (24*60*60)) % 24;
    
        return String.format("%dd %dh %dm %ds", day,hour,min,sec);
    }

    public void testVectors() {
        List<String> subtaskIdentifiers = prepareSubtask();
        while (true) {
            LOGGER.info("Select vector index:");
            for (int i = 0; i < subtaskIdentifiers.size(); i++) {
                LOGGER.info("[{}] {}", i, subtaskIdentifiers.get(i));
            }
            LOGGER.info("[-1] Exit tests for {}", getSubtaskName());
            Scanner sc = new Scanner(System.in);
            int selected = sc.nextInt();
            if (selected == -1) {
                return;
            }
            try {
                Long newMeasurement = measure(subtaskIdentifiers.get(selected));
                LOGGER.info("********************************************");
                LOGGER.info("Measured vector {} ({})", subtaskIdentifiers.get(selected), newMeasurement);
                LOGGER.info("********************************************");
            } catch (Exception ex) {
                LOGGER.error("Measuring failed", ex);
            }
        }
    }

    protected void addMeasurement(String identifier, Long measured) {
        if (!runningMeasurements.containsKey(identifier)) {
            runningMeasurements.put(identifier, new LinkedList<>());
        }
        runningMeasurements.get(identifier).add(measured);
        if(evaluationConfig.isPrintRam()) {
            addMemoryInfo();
        }
    }
    
    protected static void addMemoryInfo() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        String memoryFootprint = String.format("Heap Info -- Initial: %s   Used: %s   Committed: %s   Max: %s", formatSize(heapMemoryUsage.getInit()), formatSize(heapMemoryUsage.getUsed()), formatSize(heapMemoryUsage.getCommitted()), formatSize(heapMemoryUsage.getMax()));
        if(!memoryFootprint.equals(lastMemoryFootprint)) {
            lastMemoryFootprint = memoryFootprint;
            LOGGER.info(memoryFootprint);
        }
    }
    
    private static String formatSize(long bytes) {
        long kilo = 1024;
        long mega = kilo * kilo;
        long giga = mega * kilo;

        if (bytes >= giga) {
            return String.format("%.2f GB", (double) bytes / giga);
        } else if (bytes >= mega) {
            return String.format("%.2f MB", (double) bytes / mega);
        } else if (bytes >= kilo) {
            return String.format("%.2f KB", (double) bytes / kilo);
        } else {
            return bytes + " B";
        }
    }
    
    protected void resetMeasurements() {
        runningMeasurements.values().forEach(Collection::clear);
    }

    private void processAnalysisResults(List<String> subtaskIdentifiers, List<VectorEvaluationTask> executedEvalTasks) {
        for (VectorEvaluationTask task : executedEvalTasks) {
            switch (task.getExitCode()) {
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
        for(int i = 0; i < identifierCount; i++) {
            Arrays.fill(executionPlan, i * nextMeasurementsPerType, (i + 1) * (nextMeasurementsPerType), i);
        }
        shuffleArray(executionPlan);
        shuffleArray(executionPlan);
        shuffleArray(executionPlan);
        return executionPlan;
    }
    
    private static void shuffleArray(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = notReallyRandom.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
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
        if (cipherSuite.name().contains("ECDH")) {
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
        if (!serverReport.getConfigProfileIdentifier().equals(DefaultConfigProfile.UNFILTERED.getIdentifier())) {
            for (ConfigFilterProfile filterProfile : DefaultConfigProfile.getTls12ConfigProfiles()) {
                if (filterProfile.getIdentifier().equals(serverReport.getConfigProfileIdentifier())) {
                    ConfigFilter.applyFilterProfile(config, filterProfile.getConfigFilterTypes());
                    break;
                }
            }
        }

        config.setDefaultClientSupportedCipherSuites(cipherSuite);
        config.setDefaultRunningMode(RunningModeType.CLIENT);
        config.getDefaultClientConnection().setHostname(targetIp);
        int dynamicPort = targetPort;
        if (parentTask.isPortSwitchEnabled()) {
            dynamicPort = HttpUtil.getCurrentPort(targetIp, targetPort);
        }
        config.getDefaultClientConnection().setPort(dynamicPort);
        config.getDefaultClientConnection().setProxyControlHostname(evaluationConfig.getProxyIp());
        config.getDefaultClientConnection().setProxyControlPort(evaluationConfig.getProxyControlPort());
        config.getDefaultClientConnection().setProxyDataHostname(evaluationConfig.getProxyIp());
        config.getDefaultClientConnection().setProxyDataPort(evaluationConfig.getProxyDataPort());
        config.getDefaultClientConnection().setTimeout(evaluationConfig.getTimeout());
        config.getDefaultClientConnection().setFirstTimeout(evaluationConfig.getTimeout());
        config.getDefaultClientConnection().setConnectionTimeout(evaluationConfig.getTimeout() + 1000);
        if (evaluationConfig.isUseProxy()) {
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
        TlsAction lastAction = workflowTrace.getTlsActions().get(workflowTrace.getTlsActions().size() - 1);
        if (lastAction instanceof GenericReceiveAction) {
            workflowTrace.getTlsActions().remove(lastAction);
            workflowTrace.addTlsAction(new ReceiveAction(new AlertMessage()));
        } else if (!(lastAction instanceof ReceiveAction)) {
            LOGGER.warn("Last Action for {} is not a ReceiveAction and not a GenericReceive");
        }
    }

    protected void handleClientAuthentication(WorkflowTrace workflowTrace, Config config) {
        SendAction clientSecondFlight = (SendAction) WorkflowTraceUtil.getFirstSendingActionForMessage(HandshakeMessageType.CLIENT_KEY_EXCHANGE, workflowTrace);
        if (serverReport.getCcaSupported()) {
            if (workflowTrace.getFirstReceivingAction() instanceof ReceiveAction) {
                List<ProtocolMessage> expectedMessages = ((ReceiveAction) workflowTrace.getFirstReceivingAction()).getExpectedMessages();
                expectedMessages.add(expectedMessages.size() - 1, new CertificateRequestMessage());
            }

            if (serverReport.getCcaRequired()) {
                clientSecondFlight.getSendMessages().add(0, new CertificateMessage(config));
                clientSecondFlight.getSendMessages().add(2, new CertificateVerifyMessage(config));
            } else {
                CertificateMessage emptyCert = new CertificateMessage(config);
                emptyCert.setCertificatesListBytes(Modifiable.explicit(new byte[0]));
                clientSecondFlight.getSendMessages().add(0, emptyCert);
            }

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

        if (!workflowTraceSufficientlyExecuted) {
            throw new WorkflowTraceFailedEarlyException();
        } else if (!oracleDetectable) {
            throw new UndetectableOracleException();
        }
    }

    protected void prepareExecutor(WorkflowExecutor executor) {
        if (evaluationConfig.additionalContainerActionsRequired()) {
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
    
    protected CipherSuite parseEnforcedCipherSuite() {
        if(evaluationConfig.getEnforcedCipher() != null) {
            return CipherSuite.valueOf(evaluationConfig.getEnforcedCipher());
        }
        return null;
    }
}
