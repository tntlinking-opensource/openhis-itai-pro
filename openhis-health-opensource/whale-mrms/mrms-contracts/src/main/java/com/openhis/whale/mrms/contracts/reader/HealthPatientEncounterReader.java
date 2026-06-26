/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.reader;

import com.openhis.whale.mrms.contracts.snapshot.EncounterSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.PatientSnapshot;

/**
 * 患者与就诊读 Port（首页自动取值）。
 */
public interface HealthPatientEncounterReader {

    /**
     * 按患者主键加载快照。
     *
     * @param patientId 患者主键
     * @return 快照；不存在时 null
     */
    PatientSnapshot loadPatient(Long patientId);

    /**
     * 按就诊主键加载快照。
     *
     * @param encounterId 就诊主键
     * @return 快照；不存在时 null
     */
    EncounterSnapshot loadEncounter(Long encounterId);
}
