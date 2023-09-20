package de.rub.nds.timingdockerevaluator.task.subtask;

import de.rub.nds.modifiablevariable.bytearray.ModifiableByteArray;
import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.modifiablevariable.util.Modifiable;
import de.rub.nds.timingdockerevaluator.config.TimingDockerEvaluatorCommandConfig;
import de.rub.nds.timingdockerevaluator.task.EvaluationTask;
import de.rub.nds.timingdockerevaluator.task.exception.UndetectableOracleException;
import de.rub.nds.timingdockerevaluator.task.exception.WorkflowTraceFailedEarlyException;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.CipherType;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.protocol.message.ApplicationMessage;
import de.rub.nds.tlsattacker.core.protocol.message.RSAClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.record.RecordCryptoComputations;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.GenericReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendAction;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class PaddingOracleSubtask extends EvaluationSubtask {

    private final Random random = new Random(System.currentTimeMillis());
    List<String> vectors;

    public PaddingOracleSubtask(String targetName, int port, String ip, TimingDockerEvaluatorCommandConfig evaluationConfig, EvaluationTask parentTask) {
        super(SubtaskNames.PADDING_ORACLE.getCamelCaseName(), targetName, port, ip, evaluationConfig, parentTask);
    }

    @Override
    public boolean isApplicable() {
        return cipherSuite != null && version != null;
    }

    @Override
    public void adjustScope(ServerReport serverReport) {
        super.adjustScope(serverReport);
        if (serverReport.getCipherSuites().contains(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA)) {
            cipherSuite = CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA;
        } else {
            cipherSuite = serverReport.getCipherSuites().stream().filter(CipherSuite::isRealCipherSuite).filter(cipher -> {
                return AlgorithmResolver.getCipherType(cipher) == CipherType.BLOCK;
            }).findFirst().orElse(null);
        }
        CipherSuite enforcedSuite = parseEnforcedCipherSuite();
        if (enforcedSuite != null) {
            cipherSuite = enforcedSuite;
        }

        version = determineVersion(serverReport);
        if(cipherSuite != CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA && !(cipherSuite.name().contains("SHA256") && cipherSuite.name().contains("AES"))) {
            LOGGER.error("Code flow only accepts TLS_RSA_WITH_AES_128_CBC_SHA or any AES CBC SHA256");
            cipherSuite = null;
        }
        
        vectors = new LinkedList<>();   
        if(cipherSuite == CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA) {
            vectors.add("ValPadInvMac-[0]-0-59");
            vectors.add("InvPadValMac-[0]-0-59");
        } else {
            vectors.add("ValPadInvMac-[0]-0-47");
            vectors.add("InvPadValMac-[0]-0-47");
        }
        vectors.add("Plain_FF");
        vectors.add("Plain_XF_(0xXF=#padding_bytes)");
    }

    @Override
    protected List<String> getSubtaskIdentifiers() {
        return new LinkedList<>(vectors);
    }

    @Override
    protected String getBaselineIdentifier() {
        return "Plain_FF";
    }

    @Override
    protected Long measure(String typeIdentifier) throws WorkflowTraceFailedEarlyException, UndetectableOracleException {
       
        Config config = getBaseConfig(version, cipherSuite);
        
        WorkflowTrace workflowTrace;
        if(cipherSuite == CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA) {
            workflowTrace = getWorkflowTraceForRecordType(typeIdentifier, config);
        } else {
            workflowTrace = getWorkflowTraceForRecordTypeSha256(typeIdentifier, config);
        }
        
        if (evaluationConfig.isEchoTest()) {
            byte[] byteArray = {
                (byte) 0x67, (byte) 0xa8, (byte) 0x1a, (byte) 0xaf,
                (byte) 0x60, (byte) 0xb4, (byte) 0x20, (byte) 0xbb,
                (byte) 0x38, (byte) 0x51, (byte) 0xd9, (byte) 0xd4,
                (byte) 0x7a, (byte) 0xcb, (byte) 0x93, (byte) 0x3d,
                (byte) 0xbe, (byte) 0x70, (byte) 0x39, (byte) 0x9b,
                (byte) 0xf6, (byte) 0xc9, (byte) 0x2d, (byte) 0xa3,
                (byte) 0x3a, (byte) 0xf0, (byte) 0x1d, (byte) 0x4f,
                (byte) 0xb7, (byte) 0x70, (byte) 0xe9, (byte) 0x8c
            };
            config.setUseFreshRandom(false);
            config.setDefaultClientRandom(byteArray);
            RSAClientKeyExchangeMessage rsaCke = workflowTrace.getFirstSendMessage(RSAClientKeyExchangeMessage.class);
            rsaCke.prepareComputations();
            rsaCke.getComputations().setPlainPaddedPremasterSecret(Modifiable.explicit(ArrayConverter.hexStringToByteArray("000260B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C0325F41D3EBAF8986DA712C82BCD4D554BF0B54023C29B624DE9EF9C2F931EFC580F9AFB081B12E107B1E805F2B4F5F0F1D00C2D0F62634670921C505867FF20F6A8335E98AF8725385586B41FEFF205B4E05A010823F78B5F8F5C02439CE8F67A781D90CBE6BF1AE7F2BC40A49709A06C0E31499BF02969CA42D203E566BCC696DE08FA0102A0FD2E2330B0964ABB7C443020DE1CAD09BFD6381FFB94DAAFBB90C4ED91A0613AD1DC4B4703AF84C1D63B00030321C6D5869D61CCB98ED13AE6C09A13FC91E14922F301CF8BCF934315A6049D2F07D983FAA91B8F4E7265ECB815A7")));
            rsaCke.getComputations().setPremasterSecret(Modifiable.explicit(ArrayConverter.hexStringToByteArray("030321C6D5869D61CCB98ED13AE6C09A13FC91E14922F301CF8BCF934315A6049D2F07D983FAA91B8F4E7265ECB815A7")));
        } else {
            config.setUseFreshRandom(true);
        }
        setSpecificReceiveAction(workflowTrace);
        handleClientAuthentication(workflowTrace, config);

        final State state = new State(config, workflowTrace);
        runExecutor(state);
        
        //System.out.println(ArrayConverter.bytesToHexString(((Record)workflowTrace.getLastSendingAction().getSendRecords().get(0)).getComputations().getPlainRecordBytes().getValue()));
        return getMeasurement(state);
    }
    
    private WorkflowTrace getWorkflowTraceForRecordType(String identifier, Config config) {
        Record preparedRecord = new Record();
        preparedRecord.setComputations(new RecordCryptoComputations());
        ModifiableByteArray paddingModArray;
        ModifiableByteArray macModArray;
        byte[] padding;
        byte padVal;
        byte macXorVal;
        if(identifier.equals("ValPadInvMac-[0]-0-59")) {
            padVal = (byte) 0x3B;
            macXorVal = (byte) 0x80;
            padding = new byte[] {
                (byte) 0x3B, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.dummy(fullPlain));
        } else if (identifier.equals("InvPadValMac-[0]-0-59")) {
            padVal = (byte) 0x3B;
            macXorVal = (byte) 0x00;
            // add padding array in all vectors because we need to modify the
            // first byte for this vector
            padding = new byte[] {
                (byte) 0xBB, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.dummy(fullPlain));
        } else if (identifier.equals("Plain_FF")) {
            padVal = (byte) 0xFF;
            macXorVal = (byte) 0x00;
            padding = new byte[] {
                (byte) 0xFF, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.explicit(fullPlain));
        } else if (identifier.equals("Plain_XF_(0xXF=#padding_bytes)")) {
            padVal = (byte) 0x4F;
            macXorVal = (byte) 0x00;
            padding = new byte[] {
                (byte) 0x4F, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.explicit(fullPlain));
        } else {
            throw new IllegalArgumentException("Unknown Record type " + identifier);
        }
        paddingModArray = Modifiable.explicit(padding);
        preparedRecord.setCleanProtocolMessageBytes(Modifiable.explicit(new byte[0]));
        preparedRecord.getComputations().setPadding(paddingModArray);
        
        macModArray = Modifiable.xor(new byte[] {macXorVal}, 0);
        preparedRecord.getComputations().setMac(macModArray);
        
        WorkflowTrace trace = createTrace(config, preparedRecord);
        return trace;
    }
    
    private WorkflowTrace getWorkflowTraceForRecordTypeSha256(String identifier, Config config) {
        // This code is duplicated to avoid changes to our tested setup
        Record preparedRecord = new Record();
        preparedRecord.setComputations(new RecordCryptoComputations());
        ModifiableByteArray paddingModArray;
        ModifiableByteArray macModArray;
        byte[] padding;
        byte padVal;
        byte macXorVal;
        if(identifier.equals("ValPadInvMac-[0]-0-47")) {
            padVal = (byte)  0x2F;
            macXorVal = (byte) 0x80;
            padding = new byte[] {
                (byte) 0x2F, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.dummy(fullPlain));
        } else if (identifier.equals("InvPadValMac-[0]-0-47")) {
            padVal = (byte) 0x2F;
            macXorVal = (byte) 0x00;
            // add padding array in all vectors because we need to modify the
            // first byte for this vector
            padding = new byte[] {
                (byte) 0xAF, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.dummy(fullPlain));
        } else if (identifier.equals("Plain_FF")) {
            padVal = (byte) 0xFF;
            macXorVal = (byte) 0x00;
            padding = new byte[] {
                (byte) 0xFF, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.explicit(fullPlain));
        } else if (identifier.equals("Plain_XF_(0xXF=#padding_bytes)")) {
            padVal = (byte) 0x4F;
            macXorVal = (byte) 0x00;
            padding = new byte[] {
                (byte) 0x4F, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            byte[] fullPlain = new byte[] {
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal, 
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal,
                padVal, padVal, padVal, padVal, padVal, padVal, padVal, padVal};
            preparedRecord.getComputations().setPlainRecordBytes(Modifiable.explicit(fullPlain));
        } else {
            throw new IllegalArgumentException("Unknown Record type " + identifier);
        }
        paddingModArray = Modifiable.explicit(padding);
        preparedRecord.setCleanProtocolMessageBytes(Modifiable.explicit(new byte[0]));
        preparedRecord.getComputations().setPadding(paddingModArray);
        
        macModArray = Modifiable.xor(new byte[] {macXorVal}, 0);
        preparedRecord.getComputations().setMac(macModArray);
        
        WorkflowTrace trace = createTrace(config, preparedRecord);
        return trace;
    }
    
    private WorkflowTrace createTrace(Config config, Record preparedRecord) {
        RunningModeType runningMode = config.getDefaultRunningMode();
        WorkflowTrace trace =
                new WorkflowConfigurationFactory(config).createWorkflowTrace(WorkflowTraceType.HANDSHAKE, runningMode);
        ApplicationMessage applicationMessage = new ApplicationMessage(config);
        SendAction sendAction = new SendAction(applicationMessage);
        sendAction.setRecords(new LinkedList<>());
        sendAction.getRecords().add(preparedRecord);
        trace.addTlsAction(sendAction);
        trace.addTlsAction(new GenericReceiveAction());
        return trace;
    }
    
    
    @Override
    protected boolean isCompareAllVectorCombinations() {
        return true;
    }

    @Override
    protected boolean workflowTraceSufficientlyExecuted(WorkflowTrace executedTrace) {
        //ensure that we got CCS, FIN
        List<ReceivingAction> receives = executedTrace.getReceivingActions();
        return ((ReceiveAction) receives.get(1)).executedAsPlanned();
    }

}
