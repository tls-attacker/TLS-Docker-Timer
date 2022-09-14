package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.exception.UndetectableOracleException;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.CipherType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.exceptions.WorkflowExecutionException;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.DefaultWorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsscanner.serverscanner.probe.padding.constants.PaddingRecordGeneratorType;
import de.rub.nds.tlsscanner.serverscanner.probe.padding.trace.ClassicPaddingTraceGenerator;
import de.rub.nds.tlsscanner.serverscanner.probe.padding.vector.PaddingVector;
import de.rub.nds.tlsscanner.serverscanner.probe.padding.vector.VeryShortPaddingGenerator;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class PaddingOracleSubtask extends EvaluationSubtask {

    private final Random random = new Random(System.currentTimeMillis());
    List<PaddingVector> vectors;

    public PaddingOracleSubtask(String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig, EvaluationTask parentTask) {
        super("PaddingOracle", targetName, port, ip, evaluationConfig, parentTask);
    }

    @Override
    public boolean isApplicable() {
        return cipherSuite != null && version != null;
    }

    @Override
    public void adjustScope(ServerReport serverReport) {
        super.adjustScope(serverReport);
        if(serverReport.getCipherSuites().contains(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA)) {
            cipherSuite = CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA;
        } else  {
            cipherSuite = serverReport.getCipherSuites().stream().filter(CipherSuite::isRealCipherSuite).filter(cipher -> {
            return AlgorithmResolver.getCipherType(cipher) == CipherType.BLOCK;
            }).findFirst().orElse(null);
        }
        version = determineVersion(serverReport);
        if (version != null && cipherSuite != null) {
            vectors = (List<PaddingVector>) new VeryShortPaddingGenerator().getVectors(cipherSuite, version);
        }
    }

    @Override
    protected List<String> getSubtaskIdentifiers() {
        return vectors.stream().map(PaddingVector::getName).map(name -> name.replace(" ", "_")).collect(Collectors.toList());
    }

    @Override
    protected String getBaselineIdentifier() {
        return "Plain_FF";
    }

    @Override
    protected Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException, UndetectableOracleException {
        PaddingVector testedVector = null;
        for (PaddingVector vector : vectors) {
            if (vector.getName().replace(" ", "_").equals(typeIdentifier)) {
                testedVector = vector;
                break;
            }
        }
        if (testedVector == null) {
            throw new RuntimeException("No test vector found for identifier " + typeIdentifier);
        }
        final byte[] newRandom = new byte[32];
        Config config = getBaseConfig(version, cipherSuite);
        random.nextBytes(newRandom);
        config.setDefaultClientRandom(newRandom);
        final WorkflowTrace workflowTrace = new ClassicPaddingTraceGenerator(PaddingRecordGeneratorType.VERY_SHORT).getPaddingOracleWorkflowTrace(config, testedVector);
        setSpecificReceiveAction(workflowTrace);
        final State state = new State(config, workflowTrace);
        runExecutor(state);
        return getMeasurement(state);
    }

    @Override
    protected boolean isCompareAllVectorCombinations() {
        return true;
    }
    
    @Override
    protected boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace) {
        //ensure that we got CCS, FIN
        List<ReceivingAction> receives = executedTrace.getReceivingActions();
        return ((ReceiveAction)receives.get(1)).executedAsPlanned();
    }

}
