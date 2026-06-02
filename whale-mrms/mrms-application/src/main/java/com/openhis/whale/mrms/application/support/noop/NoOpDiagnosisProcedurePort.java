package com.openhis.whale.mrms.application.support.noop;

import com.openhis.whale.mrms.domain.port.DiagnosisProcedurePort;
import com.openhis.whale.mrms.domain.port.model.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.domain.port.model.ProcedureLineSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnMissingBean(DiagnosisProcedurePort.class)
public class NoOpDiagnosisProcedurePort implements DiagnosisProcedurePort {

    @Override
    public List<DiagnosisLineSnapshot> listAdmissionDiagnoses(long encounterId) {
        return Collections.emptyList();
    }

    @Override
    public List<DiagnosisLineSnapshot> listDischargeDiagnoses(long encounterId) {
        return Collections.emptyList();
    }

    @Override
    public List<ProcedureLineSnapshot> listProcedures(long encounterId) {
        return Collections.emptyList();
    }
}
