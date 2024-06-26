package de.rub.nds.timingdockerevaluator.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Image;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.execution.ExecutionWatcher;
import de.rub.nds.timingdockerevaluator.task.exception.ContainerFailedException;
import de.rub.nds.timingdockerevaluator.task.exception.FailedToHandshakeException;
import de.rub.nds.timingdockerevaluator.task.exception.NoSubtaskApplicableException;
import de.rub.nds.timingdockerevaluator.task.subtask.BleichenbacherSubtask;
import de.rub.nds.timingdockerevaluator.task.subtask.EvaluationSubtask;
import de.rub.nds.timingdockerevaluator.task.subtask.EvaluationSubtaskReport;
import de.rub.nds.timingdockerevaluator.task.subtask.Lucky13Subtask;
import de.rub.nds.timingdockerevaluator.task.subtask.PaddingOracleSubtask;
import de.rub.nds.timingdockerevaluator.task.subtask.SubtaskReportWriter;
import de.rub.nds.timingdockerevaluator.util.DockerTargetManagement;
import de.rub.nds.timingdockerevaluator.util.HttpUtil;
import de.rub.nds.timingdockerevaluator.util.TimingBenchmark;
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.constants.TlsImageLabels;
import de.rub.nds.tls.subject.docker.DockerClientManager;
import de.rub.nds.tls.subject.docker.DockerTlsServerInstance;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.config.delegate.ClientDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.GeneralDelegate;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.exceptions.TransportHandlerConnectException;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsattacker.transport.TransportHandlerFactory;
import de.rub.nds.tlsattacker.transport.tcp.ClientTcpTransportHandler;
import de.rub.nds.tlsscanner.core.constants.TlsProbeType;
import de.rub.nds.tlsscanner.serverscanner.config.ServerScannerConfig;
import de.rub.nds.tlsscanner.serverscanner.connectivity.ConnectivityChecker;
import de.rub.nds.tlsscanner.serverscanner.execution.TlsServerScanner;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import de.rub.nds.tlsscanner.serverscanner.selector.DefaultConfigProfile;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EvaluationTask extends TimingDockerTask {

    private final static int PORT_SWITCH_ACTIVATION_ATTEMPTS = 3;

    private static final Logger LOGGER = LogManager.getLogger();
    public static final String CONTAINER_NAME_PREFIX = "timingEval-";
    private static final DockerClient DOCKER = DockerClientManager.getDockerClient();

    private int targetPort;
    private String targetIp;

    private final String targetName;
    private TlsImplementationType implementation;
    private String version;
    private ServerReport serverReport;
    private DockerTlsServerInstance dockerInstance;

    private boolean portSwitchEnabled = false;

    List<EvaluationSubtask> subtasks = new LinkedList<>();

    public EvaluationTask(Image image, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        super(evaluationConfig);
        this.implementation = TlsImplementationType.fromString(image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName()));
        this.version = image.getLabels().get(TlsImageLabels.VERSION.getLabelName());
        this.targetName = implementation.toString() + "-" + version;
    }

    public EvaluationTask(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        super(evaluationConfig);
        this.targetName = (evaluationConfig.getSpecificName() != null) ? evaluationConfig.getSpecificName() : "RemoteTarget";
    }

    public void execute() {
        LOGGER.info("Starting tests for {}", targetName);
        long startTimestamp = System.currentTimeMillis();
        try {
            if (getEvaluationConfig().isManagedTarget()) {
                dockerInstance = prepareNewDockerContainer();
                waitForContainer();
                retrieveContainerIp(dockerInstance);
                handlePortSwitching();
                if (getEvaluationConfig().isUseHostNetwork()) {
                    int oldPort = targetPort;
                    dockerInstance.updateInstancePort();
                    targetPort = dockerInstance.getHostInfo().getPort();
                    LOGGER.info("Switched port for {} from {} to {}", targetName, oldPort, targetPort);
                }
                testTarget();
            } else {
                targetIp = getEvaluationConfig().getSpecificIp();
                targetPort = getEvaluationConfig().getSpecificPort();
                testTarget();
            }
        } catch (ContainerFailedException ex) {
            LOGGER.error("Container was unavailable for {}", targetName, ex);
            ExecutionWatcher.getReference().failedContainer(targetName);
        } catch (FailedToHandshakeException ex) {
            LOGGER.error("Scanner couldn't handshake with {}", targetName, ex);
            ExecutionWatcher.getReference().failedToHandshake(targetName);
        } catch (TransportHandlerConnectException ex) {
            LOGGER.error("Scanner couldn't reach {}", targetName, ex);
            ExecutionWatcher.getReference().failedToConnect(targetName);
        } catch (NoSubtaskApplicableException ex) {
            LOGGER.error("No subtask applicable for {}", targetName, ex);
            ExecutionWatcher.getReference().failedToFindSubtask(targetName);
        } catch (Exception ex) {
            LOGGER.error("Evaluation failed unexpected for {}", targetName, ex);
            ExecutionWatcher.getReference().failedUnexpected(targetName);
        } finally {
            if (getEvaluationConfig().isManagedTarget() && !getEvaluationConfig().isKeepContainer()) {
                stopContainter(dockerInstance);
            }
        }
        LOGGER.info("Finished measurements for {} in {} minutes", targetName, (System.currentTimeMillis() - startTimestamp) / (60 * 1000));
        ExecutionWatcher.getReference().finishedTask();
    }

    private void handlePortSwitching() {
        if (getEvaluationConfig().getTargetManagement() == DockerTargetManagement.PORT_SWITCHING) {
            LOGGER.info("Enabling port switching for target {}", targetName);
            for (int attempts = 0; attempts < 3 && !isPortSwitchEnabled(); attempts++) {
                portSwitchEnabled = HttpUtil.enablePortSwitiching(targetIp);
                if (!isPortSwitchEnabled()) {
                    pauseFor(500);
                }
            }
            if (!isPortSwitchEnabled()) {
                LOGGER.error("Failed to enable port switching within {} attempts, future failures to obtain port from Docker will use initial port ({})", PORT_SWITCH_ACTIVATION_ATTEMPTS, targetPort);
            } else {
                LOGGER.info("Port switching enabled by go server. Pausing 2 seconds to take effect.");
                pauseFor(2000);
                testPortSwitchWorks();
            }
        }
    }

    private void testPortSwitchWorks() {
        Config connectivityCheckConfig = Config.createConfig();
        connectivityCheckConfig.getDefaultClientConnection().setHostname(targetIp);
        connectivityCheckConfig.getDefaultClientConnection().setPort(HttpUtil.getCurrentPort(targetIp, targetPort));
        ConnectivityChecker checker = new ConnectivityChecker(connectivityCheckConfig.getDefaultClientConnection());
        if (!checker.isConnectable()) {
            connectivityCheckConfig.getDefaultClientConnection().setPort(targetPort);
            ConnectivityChecker initalPortChecker = new ConnectivityChecker(connectivityCheckConfig.getDefaultClientConnection());
            if (initalPortChecker.isConnectable()) {
                LOGGER.warn("Target {} does not seem to respect given port. Disabling port switching.", targetName);
                portSwitchEnabled = false;
            } else {
                LOGGER.warn("Failed to reach target {} using requested and default port.", targetName);
            }
        } else {
            LOGGER.info("Port switiching tested, pausing 2s to allow for server restart before initiating TLS-Scanner.");
            // if the server crashed here, give it some time to restart before
            // the server scanner performs its own connectivity check
            pauseFor(2000);
        }

    }

    public void pauseFor(long msToWait) {
        try {
            Thread.sleep(msToWait);
        } catch (Exception ignored) {
        }
    }

    private DockerTlsServerInstance prepareNewDockerContainer() {
        TimingBenchmark.print("Preparing new container");
        DockerTlsServerInstance newDockerInstance = createDockerInstance(implementation, version, getEvaluationConfig().isUseHostNetwork());
        targetPort = newDockerInstance.getHostInfo().getPort();
        newDockerInstance.start();
        TimingBenchmark.print("Started new container");
        return newDockerInstance;
    }

    private void testTarget() throws FailedToHandshakeException, NoSubtaskApplicableException {
        if(getEvaluationConfig().isEchoTest()) {
            serverReport = new ServerReport(targetIp, targetPort);
            HashSet<CipherSuite> cipherSuiteSet = new HashSet<>();
            cipherSuiteSet.add(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA);
            serverReport.setCipherSuites(cipherSuiteSet);
            List<ProtocolVersion> versions = new LinkedList<>();
            versions.add(ProtocolVersion.TLS12);
            serverReport.setVersions(versions);
            serverReport.setSpeaksProtocol(true);
            serverReport.setIsHandshaking(true);
            serverReport.setConfigProfileIdentifier(DefaultConfigProfile.UNFILTERED.name());
        } else {
            runServerScan();
        }
        subtasks = buildTaskList();
        executeSubtasks();
    }

    @Override
    public void stopContainter(DockerTlsServerInstance dockerInstance) {
        try {
            TimingBenchmark.print("Stopping Container");
            dockerInstance.stop();
            TimingBenchmark.print("Stopped Container");
        } catch (NotModifiedException exception) {
            LOGGER.warn("Failed to stop container for {} - was already stopped or never started!", targetName);
        }
    }

    public void retrieveContainerIp(DockerTlsServerInstance dockerInstance) throws ContainerFailedException {
        InspectContainerResponse containerInspectResponse = DOCKER.inspectContainerCmd(dockerInstance.getId()).exec();
        if (getEvaluationConfig().isUseHostNetwork()) {
            targetIp = "localhost";
        } else {
            targetIp = containerInspectResponse.getNetworkSettings().getNetworks().get("bridge").getIpAddress();
        }
        if (targetIp.isEmpty()) {
            throw new ContainerFailedException();
        }
    }

    public void executeSubtasks() {
        for (EvaluationSubtask subtask : subtasks) {
            if(getEvaluationConfig().isOnlyTestVectors()) {
                subtask.testVectors();
            } else {
                EvaluationSubtaskReport report = subtask.evaluate();
                SubtaskReportWriter.writeReport(report);
                if (report.isFailed()) {
                    ExecutionWatcher.getReference().abortedSubtask(report.getTaskName(), report.getTargetName());
                }
            }  
        }
    }

    public void runServerScan() {
        LOGGER.info("Starting TLS-Scanner for {}", targetName);
        ClientDelegate clientDelegate = new ClientDelegate();
        int dynamicPort = targetPort;
        if (isPortSwitchEnabled()) {
            dynamicPort = HttpUtil.getCurrentPort(targetIp, targetPort);
        }
        clientDelegate.setHost(targetIp + ":" + dynamicPort);
        ServerScannerConfig scannerConfig = new ServerScannerConfig(new GeneralDelegate(), clientDelegate);
        scannerConfig.setTimeout(getEvaluationConfig().getTimeout());
        scannerConfig.setProbes(TlsProbeType.PROTOCOL_VERSION, TlsProbeType.CIPHER_SUITE, TlsProbeType.CCA_SUPPORT, TlsProbeType.CCA_REQUIRED);
        scannerConfig.setOverallThreads(1);
        scannerConfig.setParallelProbes(1);
        scannerConfig.setConfigSearchCooldown(true);

        ParallelExecutor parallelExecutor = new ParallelExecutor(1, 2);
        if (getEvaluationConfig().additionalContainerActionsRequired()) {
            parallelExecutor.setDefaultBeforeTransportPreInitCallback(getRestartCallable());
        }

        TlsServerScanner scanner = new TlsServerScanner(scannerConfig, parallelExecutor);
        serverReport = scanner.scan();
        LOGGER.info("Supported cipher suites: {}", serverReport.getCipherSuites().stream().map(CipherSuite::toString).collect(Collectors.joining(", ")));
        parallelExecutor.shutdown();
    }

    public Function<State, Integer> getRestartCallable() {
        return (State state) -> {
            switch (getEvaluationConfig().getTargetManagement()) {
                case RESTART_CONTAINTER:
                    restartContainer();
                    break;
                case RESTART_SERVER:
                    restartServer();
                    break;
                case PORT_SWITCHING:
                    if (isPortSwitchEnabled()) {
                        getSwitchedPort(state);
                    }
                    break;
            }
            return 0;
        };
    }

    private void getSwitchedPort(State state) {
        int port = HttpUtil.getCurrentPort(targetIp, targetPort);
        state.getConfig().getDefaultClientConnection().setPort(port);
        state.getTlsContext().getConnection().setPort(port);
        state.getTlsContext().setTransportHandler(TransportHandlerFactory.createTransportHandler(state.getTlsContext().getConnection()));
        ((ClientTcpTransportHandler) state.getTlsContext().getTransportHandler()).setInitializationFailedCallback(() -> {
            return HttpUtil.getCurrentPort(targetIp, targetPort);
        });
    }

    public void restartServer() {
        TimingBenchmark.print("Killing server");
        HttpUtil.killServer(targetIp);
        TimingBenchmark.print("Server killed");
    }

    public void restartContainer() {
        TimingBenchmark.print("Restarting container");
        DOCKER.restartContainerCmd(dockerInstance.getId()).withtTimeout(0).exec();
        try {
            retrieveContainerIp(dockerInstance);
        } catch (ContainerFailedException containerException) {
            throw new RuntimeException("Failed to fetch port on container restart");
        }
        TimingBenchmark.print("Restarted");
    }
    
    public boolean isContainerAlive() {
        return DOCKER.inspectContainerCmd(dockerInstance.getId()).exec().getState().getRunning();
    }
    

    public List<EvaluationSubtask> buildTaskList() throws FailedToHandshakeException, NoSubtaskApplicableException {
        List<EvaluationSubtask> tasksToAdd = new LinkedList<>();
        if (serverReport.getSpeaksProtocol() != null && serverReport.getSpeaksProtocol() == true) {
            EvaluationSubtask[] implementedSubtasks = {
                new BleichenbacherSubtask(targetName, targetPort, targetIp, getEvaluationConfig(), this),
                new PaddingOracleSubtask(targetName, targetPort, targetIp, getEvaluationConfig(), this),
                new Lucky13Subtask(targetName, targetPort, targetIp, getEvaluationConfig(), this)
            };

            for (EvaluationSubtask plannedSubtask : implementedSubtasks) {
                if (getEvaluationConfig().getSpecificSubtask() == null || plannedSubtask.getSubtaskName().equalsIgnoreCase(getEvaluationConfig().getSpecificSubtask())) {
                    plannedSubtask.adjustScope(serverReport);
                    if (plannedSubtask.isApplicable()) {
                        tasksToAdd.add(plannedSubtask);
                    } else {
                        LOGGER.warn("Subtask {} is not applicable for {}", plannedSubtask.getSubtaskName(), targetName);
                        ExecutionWatcher.getReference().unapplicableSubtask(plannedSubtask.getSubtaskName(), targetName);
                    }
                }
            }

            if (tasksToAdd.isEmpty()) {
                throw new NoSubtaskApplicableException();
            }
        } else {
            throw new FailedToHandshakeException();
        }
        return tasksToAdd;
    }

    public boolean isPortSwitchEnabled() {
        return portSwitchEnabled;
    }

}
