package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.exception.UndetectableOracleException;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.tlsattacker.core.config.delegate.ClientDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.GeneralDelegate;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.KeyExchangeAlgorithm;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.DefaultWorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsbreaker.lucky13.config.Lucky13CommandConfig;
import de.rub.nds.tlsbreaker.lucky13.impl.Lucky13Attacker;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Lucky13Subtask extends EvaluationSubtask {

    private boolean supportsCipher = false;
    
    
    public Lucky13Subtask(String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig, EvaluationTask parentTask) {
        super(SubtaskNames.LUCKY13.getCamelCaseName(), targetName, port, ip, evaluationConfig, parentTask);
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
        } else {
            cipherSuite = serverReport.getCipherSuites().stream().filter(cipher -> cipher.name().endsWith("_SHA")).findFirst().orElse(null);
        }
        version = determineVersion(serverReport);
    }

    @Override
    protected List<String> getSubtaskIdentifiers() {
        //ensure list is mutable
        return new LinkedList<>(Arrays.asList(new String[] {"NO_PADDING","LONG_PADDING"}));
    }

    @Override
    protected String getBaselineIdentifier() {
        return "NO_PADDING";
    }

    @Override
    protected Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException, UndetectableOracleException {
        Lucky13CommandConfig lucky13commandConfig = new Lucky13CommandConfig(new GeneralDelegate());
        lucky13commandConfig.getDelegate(ClientDelegate.class).setHost(getTargetIp() + ":" + getTargetPort());
        Lucky13Attacker attacker = new Lucky13Attacker(lucky13commandConfig, getBaseConfig(version, cipherSuite));
        
        int padLen = 0;
        if(typeIdentifier.equals("LONG_PADDING")) {
            padLen = 255;
        }
        Record record = attacker.createRecordWithPadding(padLen, cipherSuite);
        State state = attacker.buildAttackState(record);
        runExecutor(state);
        return getMeasurement(state);
    }

    
    @Override
    protected boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace) {
        //ensure that we got CCS, FIN
        List<ReceivingAction> receives = executedTrace.getReceivingActions();
        return ((ReceiveAction)receives.get(1)).executedAsPlanned();
    }
}
