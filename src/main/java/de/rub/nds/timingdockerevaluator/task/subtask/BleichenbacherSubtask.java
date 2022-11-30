package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.exception.UndetectableOracleException;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.timingdockerevaluator.util.DockerTargetManagement;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.core.constants.KeyExchangeAlgorithm;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.util.CertificateFetcher;
import de.rub.nds.tlsattacker.core.workflow.DefaultWorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTraceUtil;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveTillAction;
import de.rub.nds.tlsscanner.serverscanner.probe.bleichenbacher.constans.BleichenbacherScanType;
import de.rub.nds.tlsscanner.serverscanner.probe.bleichenbacher.constans.BleichenbacherWorkflowType;
import de.rub.nds.tlsscanner.serverscanner.probe.bleichenbacher.trace.BleichenbacherWorkflowGenerator;
import de.rub.nds.tlsscanner.serverscanner.probe.bleichenbacher.vector.Pkcs1Vector;
import de.rub.nds.tlsscanner.serverscanner.probe.bleichenbacher.vector.Pkcs1VectorGenerator;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BleichenbacherSubtask extends EvaluationSubtask {

    private final Random random = new Random(System.currentTimeMillis());
    private static final Logger LOGGER = LogManager.getLogger();
    
    
    private PublicKey publicKey;
    private List<Pkcs1Vector> vectors;
    
    public BleichenbacherSubtask(String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig, EvaluationTask parentTask) {
        super(SubtaskNames.BLEICHENBACHER.getCamelCaseName(), targetName, port, ip, evaluationConfig, parentTask);
    }

    @Override
    public void adjustScope(ServerReport serverReport) {
        super.adjustScope(serverReport);
        if(serverReport.getCipherSuites().contains(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA)) {
            cipherSuite = CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA;
        } else {
            cipherSuite = serverReport.getCipherSuites().stream().filter(CipherSuite::isRealCipherSuite).filter(cipher -> {
            return AlgorithmResolver.getKeyExchangeAlgorithm(cipher) == KeyExchangeAlgorithm.RSA;
            }).findFirst().orElse(null);
        }
        
        
        version = determineVersion(serverReport);
        if (cipherSuite != null && version != null) {
            if(evaluationConfig.getTargetManagement() == DockerTargetManagement.RESTART_CONTAINTER) {
               parentTask.restartContainer(); 
            } else if(evaluationConfig.getTargetManagement() == DockerTargetManagement.RESTART_CONTAINTER) {
                parentTask.restartServer();
            }
            
            publicKey = CertificateFetcher.fetchServerPublicKey(getBaseConfig(version, cipherSuite));
            if (publicKey == null) {
                LOGGER.error("Failed to fetch any PublicKey for {}", getTargetName());
                return;
            }
            if (!(publicKey instanceof RSAPublicKey)) {
                LOGGER.error("Failed to fetch RSA PublicKey for {}", getTargetName());
                return;
            }
            vectors = ((List<Pkcs1Vector>) Pkcs1VectorGenerator.generatePkcs1Vectors((RSAPublicKey) publicKey, BleichenbacherScanType.FAST, version)).stream().filter(vector -> vector.getName().contains("Correctly formatted PKCS#1 PMS message") || vector.getName().contains("Wrong first byte (0x00 set to 0x17)")).collect(Collectors.toList());
        }
    }

    

    @Override
    protected List<String> getSubtaskIdentifiers() {
        return vectors.stream().map(Pkcs1Vector::getName).map(name -> name.replace(" ", "_")).collect(Collectors.toList());
    }

    @Override
    protected String getBaselineIdentifier() {
        return "Correctly_formatted_PKCS#1_PMS_message";
    }

    @Override
    protected Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException, UndetectableOracleException {
        Pkcs1Vector selectedVector = resolveVector(typeIdentifier);
        if(selectedVector == null) {
            throw new IllegalArgumentException("Was unable to resolve identifier " + typeIdentifier);
        }
        
        Config config = getBaseConfig(version, cipherSuite);
        final byte[] newRandom = new byte[32];
        random.nextBytes(newRandom);
        config.setDefaultClientRandom(newRandom);
        final WorkflowTrace workflowTrace = BleichenbacherWorkflowGenerator.generateWorkflow(config, BleichenbacherWorkflowType.CKE_CCS_FIN, selectedVector.getEncryptedValue());
        setSpecificReceiveAction(workflowTrace);
        final State state = new State(config, workflowTrace);
        runExecutor(state);
        return getMeasurement(state);
    }

    protected Pkcs1Vector resolveVector(String typeIdentifier) {
        Pkcs1Vector selectedVector = null;
        for(Pkcs1Vector vector: vectors) {
            if(vector.getName().replace(" ", "_").equals(typeIdentifier)) {
                selectedVector = vector;
            }
        }
        return selectedVector;
    }

    

    @Override
    public boolean isApplicable() {
        return cipherSuite != null && version != null && publicKey != null;
    }

    @Override
    protected boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace) {
        if(WorkflowTraceUtil.didReceiveMessage(HandshakeMessageType.CERTIFICATE_REQUEST, executedTrace)) {
            return false;
        }
        //ensure that we got SH + CERT + SKE + SHD
        return ((ReceiveTillAction)executedTrace.getFirstReceivingAction()).executedAsPlanned();
    }

}
