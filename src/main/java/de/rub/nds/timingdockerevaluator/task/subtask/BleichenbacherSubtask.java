package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.KeyExchangeAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.util.CertificateFetcher;
import de.rub.nds.tlsattacker.core.workflow.DefaultWorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveTillAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsattacker.transport.tcp.proxy.TimingProxyClientTcpTransportHandler;
import de.rub.nds.tlsattacker.transport.tcp.timing.TimingClientTcpTransportHandler;
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
    
    public BleichenbacherSubtask(String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        super("Bleichenbacher", targetName, port, ip, evaluationConfig);
    }

    @Override
    public void adjustScope(ServerReport serverReport) {
        
        if(serverReport.getCipherSuites().contains(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA)) {
            cipherSuite = CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA;
        } else {
            cipherSuite = serverReport.getCipherSuites().stream().filter(CipherSuite::isRealCipherSuite).filter(cipher -> {
            return AlgorithmResolver.getKeyExchangeAlgorithm(cipher) == KeyExchangeAlgorithm.RSA;
            }).findFirst().orElse(null);
        }
        
        
        version = determineVersion(serverReport);
        if (cipherSuite != null && version != null) {
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
    protected Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException {
        Pkcs1Vector selectedVector = null;
        for(Pkcs1Vector vector: vectors) {
            if(vector.getName().replace(" ", "_").equals(typeIdentifier)) {
                selectedVector = vector;
            }
        }
        if(selectedVector == null) {
            throw new IllegalArgumentException("Was unable to resolve identifier " + typeIdentifier);
        }
        
        Config config = getBaseConfig(version, cipherSuite);
        final byte[] newRandom = new byte[32];
        random.nextBytes(newRandom);
        config.setDefaultClientRandom(newRandom);
        final WorkflowTrace workflowTrace = BleichenbacherWorkflowGenerator.generateWorkflow(config, BleichenbacherWorkflowType.CKE_CCS_FIN, selectedVector.getEncryptedValue());
        final State state = new State(config, workflowTrace);
        final WorkflowExecutor executor = (WorkflowExecutor) new DefaultWorkflowExecutor(state);
        executor.executeWorkflow();
        if(!workflowTraceSufficientlyExecuted(workflowTrace)) {
            throw new WorkflowTraceFailedEarlyException();
        }
        return getMeasurement(state);
    }

    

    @Override
    public boolean isApplicable() {
        return cipherSuite != null && version != null && publicKey != null;
    }

    @Override
    protected boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace) {
        //ensure that we got SH + CERT + SKE + SHD
        return ((ReceiveTillAction)executedTrace.getFirstReceivingAction()).executedAsPlanned();
    }

}
