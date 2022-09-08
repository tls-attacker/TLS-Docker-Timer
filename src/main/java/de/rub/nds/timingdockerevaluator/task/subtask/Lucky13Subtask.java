package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.tlsattacker.core.config.delegate.ClientDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.GeneralDelegate;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsbreaker.lucky13.config.Lucky13CommandConfig;
import de.rub.nds.tlsbreaker.lucky13.impl.Lucky13Attacker;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.Arrays;
import java.util.List;

public class Lucky13Subtask extends EvaluationSubtask {

    private boolean supportsCipher = false;
    
    
    public Lucky13Subtask(String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig) {
        super("Lucky13", targetName, port, ip, evaluationConfig);
        cipherSuite = CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA;
    }
    
    @Override
    public boolean isApplicable() {
        return supportsCipher && version != null;
    }

    @Override
    public void adjustScope(ServerReport serverReport) {
        supportsCipher = serverReport.getCipherSuites().contains(cipherSuite);
        version = determineVersion(serverReport);
    }

    @Override
    protected List<String> getSubtaskIdentifiers() {
        return Arrays.asList(new String[] {"NO_PADDING","LONG_PADDING"});
    }

    @Override
    protected String getBaselineIdentifier() {
        return "NO_PADDING";
    }

    @Override
    protected Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException {
        Lucky13CommandConfig lucky13commandConfig = new Lucky13CommandConfig(new GeneralDelegate());
        lucky13commandConfig.getDelegate(ClientDelegate.class).setHost(getTargetIp() + ":" + getTargetPort());
        Lucky13Attacker attacker = new Lucky13Attacker(lucky13commandConfig, getBaseConfig(version, cipherSuite));
        int padLen = 0;
        if(typeIdentifier.equals("LONG_PADDING")) {
            padLen = 255;
        }
        Record record = attacker.createRecordWithPadding(padLen, cipherSuite);
        WorkflowTrace workflowTrace = attacker.executeAttackRound(record);
        if(!workflowTraceSufficientlyExecuted(workflowTrace)) {
            throw new WorkflowTraceFailedEarlyException();
        }
        //attacker.getLastExecutor().closeConnection();
        return attacker.getLastResult();
    }
    
    
    @Override
    protected boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace) {
        //ensure that we got CCS, FIN
        List<ReceivingAction> receives = executedTrace.getReceivingActions();
        return ((ReceiveAction)receives.get(1)).executedAsPlanned();
    }
}
