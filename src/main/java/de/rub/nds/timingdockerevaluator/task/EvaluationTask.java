package de.rub.nds.timingdockerevaluator.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Image;
import de.rub.nds.modifiablevariable.util.ArrayConverter;
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
import de.rub.nds.tls.subject.TlsImplementationType;
import de.rub.nds.tls.subject.constants.TlsImageLabels;
import de.rub.nds.tls.subject.docker.DockerClientManager;
import de.rub.nds.tls.subject.docker.DockerTlsManagerFactory;
import de.rub.nds.tls.subject.docker.DockerTlsServerInstance;
import de.rub.nds.tlsattacker.core.config.delegate.ClientDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.GeneralDelegate;
import de.rub.nds.tlsattacker.core.exceptions.TransportHandlerConnectException;
import de.rub.nds.tlsscanner.core.constants.TlsProbeType;
import de.rub.nds.tlsscanner.serverscanner.config.ServerScannerConfig;
import de.rub.nds.tlsscanner.serverscanner.execution.TlsServerScanner;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EvaluationTask {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final String CONTAINER_NAME_PREFIX = "timingEval-";
    private static final DockerClient DOCKER = DockerClientManager.getDockerClient();

    private int targetPort;
    private String targetIp;

    private final String targetName;
    private TlsImplementationType implementation;
    private String version;
    private ServerReport serverReport;
    private final TimingDockerEvaluatorCommandConfig evaluationConfig;
    

    List<EvaluationSubtask> subtasks = new LinkedList<>();

    public EvaluationTask(Image image, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        this.implementation = TlsImplementationType.fromString(image.getLabels().get(TlsImageLabels.IMPLEMENTATION.getLabelName()));
        this.version = image.getLabels().get(TlsImageLabels.VERSION.getLabelName());
        this.targetName = implementation.toString() + "-" + version;
        this.evaluationConfig = evaluationConfig;
    }
    
    public EvaluationTask(TimingDockerEvaluatorCommandConfig evaluationConfig) {
        this.evaluationConfig = evaluationConfig;
        this.targetName = (evaluationConfig.getSpecificName()!= null) ? evaluationConfig.getSpecificName(): "RemoteTarget";
    }
    
    

    public void execute() {
        LOGGER.info("Starting tests for {}", targetName);
        try {
            if(evaluationConfig.isManagedTarget()) {
                DockerTlsServerInstance dockerInstance = createDockerInstance();
                dockerInstance.start();
                waitForContainer();
                retrieveContainerIp(dockerInstance);
                if(evaluationConfig.isUseHostNetwork()) {
                    int oldPort = targetPort;
                    dockerInstance.updateInstancePort();
                    targetPort = dockerInstance.getHostInfo().getPort();
                    LOGGER.info("Switched port for {} from {} to {}", targetName, oldPort, targetPort);
                }
                testTarget();
                stopContainter(dockerInstance);
            } else {
                targetIp = evaluationConfig.getSpecificIp();
                targetPort = evaluationConfig.getSpecificPort();
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
        }

        ExecutionWatcher.getReference().finishedTask();
    }

    private void testTarget() throws FailedToHandshakeException, NoSubtaskApplicableException {
        runServerScan();
        buildTaskList();
        executeSubtasks();
    }

    public void stopContainter(DockerTlsServerInstance dockerInstance) {
        try {
            dockerInstance.stop();
        } catch (NotModifiedException exception) {
            LOGGER.warn("Failed to stop container for {} - was already stopped or never started!", targetName);
        }
    }

    public void waitForContainer() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }
    }

    public void retrieveContainerIp(DockerTlsServerInstance dockerInstance) throws ContainerFailedException {
        InspectContainerResponse containerInspectResponse = DOCKER.inspectContainerCmd(dockerInstance.getId()).exec();
        if(evaluationConfig.isUseHostNetwork()) {
            targetIp = "localhost";
        } else {
            targetIp = containerInspectResponse.getNetworkSettings().getNetworks().get("bridge").getIpAddress();
        }
        if (targetIp.isEmpty()) {
            throw new ContainerFailedException();
        }
    }

    private DockerTlsServerInstance createDockerInstance() {
        try {
            String containerName = CONTAINER_NAME_PREFIX + (implementation + version).replace(":", "-");
            DockerTlsManagerFactory.TlsServerInstanceBuilder targetInstanceBuilder = DockerTlsManagerFactory.getTlsServerBuilder(implementation, version);
            targetInstanceBuilder = targetInstanceBuilder.containerName(containerName).port(4433).hostname("0.0.0.0").ip("0.0.0.0").parallelize(true);
            if(evaluationConfig.isUseHostNetwork()) {
                targetInstanceBuilder = targetInstanceBuilder.hostConfigHook(hostConfig -> {
                        hostConfig.withPortBindings(new LinkedList<>()).withNetworkMode("host");
                        return hostConfig;});
            }     
            DockerTlsServerInstance targetInstance = targetInstanceBuilder.build();
            targetPort = targetInstance.getHostInfo().getPort();
            return targetInstance;
        } catch (DockerException | InterruptedException ex) {
            LOGGER.error("Failed to create instance ", ex);
            throw new RuntimeException();
        }
    }

    public void executeSubtasks() {
        for (EvaluationSubtask subtask : subtasks) {
            subtask.adjustScope(serverReport);
            EvaluationSubtaskReport report = subtask.evaluate();
            SubtaskReportWriter.writeReport(report);
        }
    }

    public void runServerScan() {
        ClientDelegate clientDelegate = new ClientDelegate();
        clientDelegate.setHost(targetIp + ":" + targetPort);
        ServerScannerConfig scannerConfig = new ServerScannerConfig(new GeneralDelegate(), clientDelegate);
        scannerConfig.setTimeout(evaluationConfig.getTimeout());
        scannerConfig.setProbes(TlsProbeType.PROTOCOL_VERSION, TlsProbeType.CIPHER_SUITE);
        scannerConfig.setOverallThreads(1);
        scannerConfig.setParallelProbes(1);
        scannerConfig.setConfigSearchCooldown(true);

        TlsServerScanner scanner = new TlsServerScanner(scannerConfig);
        serverReport = scanner.scan();
    }

    public void buildTaskList() throws FailedToHandshakeException, NoSubtaskApplicableException {
        if (serverReport.getSpeaksProtocol() != null && serverReport.getSpeaksProtocol() == true) {
            EvaluationSubtask[] implementedSubtasks = {
                new BleichenbacherSubtask(targetName, targetPort, targetIp, evaluationConfig),
                new PaddingOracleSubtask(targetName, targetPort, targetIp, evaluationConfig),
                new Lucky13Subtask(targetName, targetPort, targetIp, evaluationConfig)
            };

            for (EvaluationSubtask plannedSubtask : implementedSubtasks) {
                if (evaluationConfig.getSpecificSubtask() == null || plannedSubtask.getSubtaskName().equalsIgnoreCase(evaluationConfig.getSpecificSubtask())) {
                    plannedSubtask.adjustScope(serverReport);
                    if (plannedSubtask.isApplicable()) {
                        subtasks.add(plannedSubtask);
                    } else {
                        LOGGER.warn("Subtask {} is not applicable for {}", plannedSubtask.getSubtaskName(), targetName);
                        ExecutionWatcher.getReference().unapplicableSubtask(plannedSubtask.getSubtaskName(), targetName);
                    }
                }
            }

            if (subtasks.isEmpty()) {
                throw new NoSubtaskApplicableException();
            }
        } else {
            throw new FailedToHandshakeException();
        }
    }

}
