/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.bridge.shared.diagnosis;

import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 从 {@code diagnosisJson} 快照解析主诊断与摘要展示，与护士站/登记口径一致。
 */
@Component
public class DiagnosisSnapshotDisplayResolver {

    private final DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser;

    public DiagnosisSnapshotDisplayResolver(DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser) {
        this.diagnosisJsonSnapshotParser = diagnosisJsonSnapshotParser;
    }

    public PrimaryDiagnosisDisplay resolvePrimaryDiagnosis(String diagnosisJson) {
        PrimaryDiagnosisDisplay display = new PrimaryDiagnosisDisplay();
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
                diagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return display;
        }
        DiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow = null;
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
            if (row.getMainDiagnosisFlag() != null
                    && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryRow = row;
                break;
            }
        }
        if (primaryRow == null) {
            primaryRow = rows.get(0);
        }
        display.setPrimaryConditionDefinitionId(primaryRow.getConditionDefinitionId());
        if (StringUtils.isNotBlank(primaryRow.getConditionDefinitionName())) {
            display.setPrimaryConditionDefinitionName(primaryRow.getConditionDefinitionName().trim());
        }
        return display;
    }

    public List<String> resolveDiagnosisDisplayLines(String diagnosisJson) {
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
                diagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> ordered = new ArrayList<>(rows);
        ordered.sort(buildDiagnosisRowDisplayComparator());
        List<String> lines = new ArrayList<>(ordered.size());
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : ordered) {
            String line = formatDiagnosisDisplayLine(row);
            if (StringUtils.isNotBlank(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    public String resolveDiagnosisSummary(String diagnosisJson) {
        return joinDiagnosisDisplayLines(resolveDiagnosisDisplayLines(diagnosisJson));
    }

    public String joinDiagnosisDisplayLines(List<String> displayLines) {
        if (displayLines == null || displayLines.isEmpty()) {
            return null;
        }
        return String.join(DoctorStationDiagnosisConstants.DIAGNOSIS_DISPLAY_SUMMARY_SEPARATOR, displayLines);
    }

    private Comparator<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> buildDiagnosisRowDisplayComparator() {
        return (left, right) -> {
            int mainLeft = 1;
            int mainRight = 1;
            if (left.getMainDiagnosisFlag() != null
                    && left.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                mainLeft = 0;
            }
            if (right.getMainDiagnosisFlag() != null
                    && right.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                mainRight = 0;
            }
            if (mainLeft != mainRight) {
                return Integer.compare(mainLeft, mainRight);
            }
            Integer sortLeft = left.getDiagnosisSortNo();
            Integer sortRight = right.getDiagnosisSortNo();
            if (sortLeft != null && sortRight != null && !sortLeft.equals(sortRight)) {
                return Integer.compare(sortLeft, sortRight);
            }
            if (sortLeft != null && sortRight == null) {
                return -1;
            }
            if (sortLeft == null && sortRight != null) {
                return 1;
            }
            return 0;
        };
    }

    private String formatDiagnosisDisplayLine(DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        String name = null;
        if (StringUtils.isNotBlank(row.getConditionDefinitionName())) {
            name = row.getConditionDefinitionName().trim();
        }
        if (StringUtils.isBlank(name) && StringUtils.isNotBlank(row.getDiagnosisDesc())) {
            name = row.getDiagnosisDesc().trim();
        }
        if (StringUtils.isBlank(name)) {
            return null;
        }
        if (row.getMainDiagnosisFlag() != null
                && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
            return '[' + DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_DISPLAY_TAG + ']' + name;
        }
        return name;
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
