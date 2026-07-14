/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.bridge.metadata.context;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.whale.emr.bridge.metadata.context.diagnosis.EmrDiagnosisSnapshotDisplayResolver;
import com.openhis.whale.emr.bridge.metadata.context.diagnosis.EmrEncounterDiagnosisSnapshotAssembler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 就诊诊断元数据贡献器（方案 C）：从就诊诊断快照解析 {@code diagnosis.*} 扁平键。
 */
@Component
public class EmrDiagnosisMetadataContributor {

    private final EmrEncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    private final EmrDiagnosisSnapshotDisplayResolver diagnosisSnapshotDisplayResolver;

    public EmrDiagnosisMetadataContributor(
            EmrEncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler,
            EmrDiagnosisSnapshotDisplayResolver diagnosisSnapshotDisplayResolver) {
        this.encounterDiagnosisSnapshotAssembler = encounterDiagnosisSnapshotAssembler;
        this.diagnosisSnapshotDisplayResolver = diagnosisSnapshotDisplayResolver;
    }

    public void contribute(Map<String, String> target, Encounter encounter) {
        if (target == null || encounter == null || encounter.getId() == null) {
            return;
        }
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(encounter.getId()).diagnosisJson();
        if (StringUtils.isBlank(diagnosisJson)) {
            return;
        }
        String primaryDisplay = resolvePrimaryDisplay(diagnosisJson);
        put(target, "diagnosis.primaryDisplay", primaryDisplay);
        EmrDiagnosisSnapshotDisplayResolver.PrimaryDiagnosisDisplay primary =
                diagnosisSnapshotDisplayResolver.resolvePrimaryDiagnosis(diagnosisJson);
        if (primary.getPrimaryConditionDefinitionId() != null) {
            put(target, "diagnosis.primaryConditionDefinitionId",
                    String.valueOf(primary.getPrimaryConditionDefinitionId()));
        }
        put(target, "diagnosis.summary", diagnosisSnapshotDisplayResolver.resolveDiagnosisSummary(diagnosisJson));
    }

    private String resolvePrimaryDisplay(String diagnosisJson) {
        EmrDiagnosisSnapshotDisplayResolver.PrimaryDiagnosisDisplay primary =
                diagnosisSnapshotDisplayResolver.resolvePrimaryDiagnosis(diagnosisJson);
        if (StringUtils.isNotBlank(primary.getPrimaryConditionDefinitionName())) {
            return primary.getPrimaryConditionDefinitionName().trim();
        }
        List<String> lines = diagnosisSnapshotDisplayResolver.resolveDiagnosisDisplayLines(diagnosisJson);
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return stripPrimaryDisplayTag(lines.get(0));
    }

    private String stripPrimaryDisplayTag(String line) {
        if (StringUtils.isBlank(line)) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int end = trimmed.indexOf(']');
            if (end > 0 && end < trimmed.length() - 1) {
                return trimmed.substring(end + 1).trim();
            }
        }
        return trimmed;
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }
}
