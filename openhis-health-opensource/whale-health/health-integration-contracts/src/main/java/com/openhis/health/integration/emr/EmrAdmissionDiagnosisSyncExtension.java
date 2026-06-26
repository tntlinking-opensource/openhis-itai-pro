/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.emr;

/**
 * 住院医生站保存/删除诊断后，回写 EMR 入院记录结构化诊断（供 MRMS 入院病情自动带入）。
 */
public interface EmrAdmissionDiagnosisSyncExtension {

    default boolean isEnabled() {
        return false;
    }

    /**
     * 按当前就诊诊断列表刷新 {@code admissionDiagnosisStructured} 数据元；无入院记录文书时静默跳过。
     *
     * @param encounterId 住院就诊主键
     * @param patientId   患者主键
     */
    default void syncAfterInpatientDiagnosisChanged(Long encounterId, Long patientId) {
        // no-op when EMR module not assembled
    }
}
