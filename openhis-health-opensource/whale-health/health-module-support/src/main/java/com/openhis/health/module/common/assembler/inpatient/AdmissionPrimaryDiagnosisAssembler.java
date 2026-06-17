/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.assembler.inpatient;

import com.openhis.health.bridge.shared.diagnosis.DiagnosisSnapshotDisplayResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 从住院读模型 {@code diagnosisJson} 快照解析诊断展示（登记弹窗、护士站列表/详情）。
 */
@Component
public class AdmissionPrimaryDiagnosisAssembler {

    private final DiagnosisSnapshotDisplayResolver diagnosisSnapshotDisplayResolver;

    public AdmissionPrimaryDiagnosisAssembler(DiagnosisSnapshotDisplayResolver diagnosisSnapshotDisplayResolver) {
        this.diagnosisSnapshotDisplayResolver = diagnosisSnapshotDisplayResolver;
    }

    public PrimaryDiagnosisDisplay resolvePrimaryDiagnosis(String diagnosisJson) {
        DiagnosisSnapshotDisplayResolver.PrimaryDiagnosisDisplay resolved =
                diagnosisSnapshotDisplayResolver.resolvePrimaryDiagnosis(diagnosisJson);
        PrimaryDiagnosisDisplay display = new PrimaryDiagnosisDisplay();
        display.setPrimaryConditionDefinitionId(resolved.getPrimaryConditionDefinitionId());
        display.setPrimaryConditionDefinitionName(resolved.getPrimaryConditionDefinitionName());
        return display;
    }

    public List<String> resolveDiagnosisDisplayLines(String diagnosisJson) {
        return diagnosisSnapshotDisplayResolver.resolveDiagnosisDisplayLines(diagnosisJson);
    }

    public String resolveDiagnosisSummary(String diagnosisJson) {
        return diagnosisSnapshotDisplayResolver.resolveDiagnosisSummary(diagnosisJson);
    }

    public String joinDiagnosisDisplayLines(List<String> displayLines) {
        return diagnosisSnapshotDisplayResolver.joinDiagnosisDisplayLines(displayLines);
    }

    public static final class PrimaryDiagnosisDisplay {

        private Long primaryConditionDefinitionId;
        private String primaryConditionDefinitionName;

        public Long getPrimaryConditionDefinitionId() {
            return primaryConditionDefinitionId;
        }

        public void setPrimaryConditionDefinitionId(Long primaryConditionDefinitionId) {
            this.primaryConditionDefinitionId = primaryConditionDefinitionId;
        }

        public String getPrimaryConditionDefinitionName() {
            return primaryConditionDefinitionName;
        }

        public void setPrimaryConditionDefinitionName(String primaryConditionDefinitionName) {
            this.primaryConditionDefinitionName = primaryConditionDefinitionName;
        }
    }
}
