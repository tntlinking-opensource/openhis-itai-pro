package com.openhis.whale.mrms.domain.port;

import com.openhis.whale.mrms.domain.port.model.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.domain.port.model.ProcedureLineSnapshot;

import java.util.List;

public interface DiagnosisProcedurePort {

    List<DiagnosisLineSnapshot> listAdmissionDiagnoses(long encounterId);

    List<DiagnosisLineSnapshot> listDischargeDiagnoses(long encounterId);

    List<ProcedureLineSnapshot> listProcedures(long encounterId);
}
