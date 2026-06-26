/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.reader;

import com.openhis.whale.mrms.contracts.snapshot.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.ProcedureLineSnapshot;

import java.util.List;

/**
 * 诊断与手术读 Port。
 */
public interface HealthDiagnosisProcedureReader {

    /**
     * 出院诊断列表。
     *
     * @param encounterId 就诊主键
     * @return 诊断行
     */
    List<DiagnosisLineSnapshot> listDischargeDiagnoses(Long encounterId);

    /**
     * 入院诊断列表。
     *
     * @param encounterId 就诊主键
     * @return 诊断行
     */
    List<DiagnosisLineSnapshot> listAdmissionDiagnoses(Long encounterId);

    /**
     * 手术及操作列表。
     *
     * @param encounterId 就诊主键
     * @return 手术行
     */
    List<ProcedureLineSnapshot> listProcedures(Long encounterId);
}
