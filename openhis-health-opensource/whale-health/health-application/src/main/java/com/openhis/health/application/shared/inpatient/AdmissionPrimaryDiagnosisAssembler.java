/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.shared.diagnosis.DiagnosisJsonSnapshotParser;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.utils.TcmDiagnosisPresentationUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 从住院读模型 {@code diagnosisJson} 快照解析诊断展示（登记弹窗、护士站列表/详情）。
 */
@Component
@RequiredArgsConstructor
public class AdmissionPrimaryDiagnosisAssembler {

    /** 诊断 JSON 快照解析器（住院读模型 {@code diagnosisJson} → 结构化行）。 */
    private final DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser;

    /**
     * 解析主诊断展示（诊断目录 id + 名称）。
     *
     * @param diagnosisJson 住院读模型诊断快照 JSON
     * @return 主诊断展示对象
     */
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
        display.setPrimaryConditionDefinitionId(this.resolvePrimaryDefinitionId(rows, primaryRow));
        String displayName = this.resolvePrimaryDisplayName(rows, primaryRow);
        if (StringUtils.isNotEmpty(displayName)) {
            display.setPrimaryConditionDefinitionName(displayName.trim());
        }
        return display;
    }

    /**
     * 解析诊断多行展示列表。
     *
     * @param diagnosisJson 住院读模型诊断快照 JSON
     * @return 诊断展示行列表
     */
    public List<String> resolveDiagnosisDisplayLines(String diagnosisJson) {
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
            diagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> ordered = new ArrayList<>(rows);
        ordered.sort(this.buildDiagnosisRowDisplayComparator());
        List<String> lines = new ArrayList<>(ordered.size());
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : ordered) {
            String line = this.formatDiagnosisDisplayLine(row);
            if (StringUtils.isNotEmpty(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 解析诊断摘要单行文案。
     *
     * @param diagnosisJson 住院读模型诊断快照 JSON
     * @return 摘要展示文本
     */
    public String resolveDiagnosisSummary(String diagnosisJson) {
        return this.joinDiagnosisDisplayLines(this.resolveDiagnosisDisplayLines(diagnosisJson));
    }

    /**
     * 将诊断展示行拼接为单行摘要。
     *
     * @param displayLines 诊断展示行
     * @return 拼接后的摘要文本
     */
    public String joinDiagnosisDisplayLines(List<String> displayLines) {
        if (displayLines == null || displayLines.isEmpty()) {
            return null;
        }
        return String.join(DoctorStationDiagnosisConstants.DIAGNOSIS_DISPLAY_SUMMARY_SEPARATOR, displayLines);
    }

    /**
     * 解析主诊断展示名称（西医取目录名；中医按病证组拼接）。
     *
     * @param rows       快照解析行列表
     * @param primaryRow 主诊断行
     * @return 展示名称；无法解析时 null
     */
    private String resolvePrimaryDisplayName(
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows,
        DiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow) {
        if (primaryRow == null) {
            return null;
        }
        if (this.isTcmDiagnosisRow(primaryRow)) {
            String groupKey = trimToNull(primaryRow.getTcmSyndromeGroup());
            if (groupKey != null) {
                DiagnosisJsonSnapshotParser.ParsedDiagnosisRow diseaseRow = null;
                DiagnosisJsonSnapshotParser.ParsedDiagnosisRow syndromeRow = null;
                for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
                    if (!groupKey.equals(trimToNull(row.getTcmSyndromeGroup()))) {
                        continue;
                    }
                    if (DoctorStationDiagnosisConstants.SOURCE_TCM_DX.equals(trimToNull(row.getSourceEnum()))) {
                        diseaseRow = row;
                    } else if (DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME.equals(
                        trimToNull(row.getSourceEnum()))) {
                        syndromeRow = row;
                    }
                }
                if (diseaseRow == null && this.isTcmDiseaseSource(primaryRow)) {
                    diseaseRow = primaryRow;
                }
                if (syndromeRow == null && this.isTcmSyndromeSource(primaryRow)) {
                    syndromeRow = primaryRow;
                }
                if (diseaseRow == null) {
                    diseaseRow = primaryRow;
                }
                String diseaseName = null;
                if (diseaseRow != null) {
                    diseaseName = diseaseRow.getConditionDefinitionName();
                }
                String syndromeName = null;
                if (syndromeRow != null) {
                    syndromeName = syndromeRow.getConditionDefinitionName();
                }
                return TcmDiagnosisPresentationUtil.formatGroupDisplayName(diseaseName, syndromeName);
            }
        }
        if (StringUtils.isNotEmpty(primaryRow.getConditionDefinitionName())) {
            return primaryRow.getConditionDefinitionName().trim();
        }
        return null;
    }

    /**
     * 解析主诊断目录定义主键（中医优先取疾病行定义 id）。
     *
     * @param rows       快照解析行列表
     * @param primaryRow 主诊断行
     * @return 主诊断目录定义 id；无法解析时 null
     */
    private Long resolvePrimaryDefinitionId(
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows,
        DiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow) {
        if (primaryRow == null) {
            return null;
        }
        if (this.isTcmDiagnosisRow(primaryRow)) {
            String groupKey = trimToNull(primaryRow.getTcmSyndromeGroup());
            if (groupKey != null) {
                for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
                    if (groupKey.equals(trimToNull(row.getTcmSyndromeGroup()))
                        && DoctorStationDiagnosisConstants.SOURCE_TCM_DX.equals(trimToNull(row.getSourceEnum()))) {
                        return row.getConditionDefinitionId();
                    }
                }
            }
            if (this.isTcmDiseaseSource(primaryRow)) {
                return primaryRow.getConditionDefinitionId();
            }
        }
        return primaryRow.getConditionDefinitionId();
    }

    /**
     * 判定快照行是否为中医诊断（kind 或 source 分流）。
     *
     * @param row 快照解析行
     * @return true 表示中医诊断行
     */
    private boolean isTcmDiagnosisRow(DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        if (row == null) {
            return false;
        }
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(trimToNull(row.getDiagnosisKind()))) {
            return true;
        }
        String source = trimToNull(row.getSourceEnum());
        if (DoctorStationDiagnosisConstants.SOURCE_TCM_DX.equals(source)) {
            return true;
        }
        return DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME.equals(source);
    }

    /**
     * 判定快照行来源是否为中医疾病（{@code SOURCE_TCM_DX}）。
     *
     * @param row 快照解析行
     * @return true 表示中医疾病行
     */
    private boolean isTcmDiseaseSource(DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        return DoctorStationDiagnosisConstants.SOURCE_TCM_DX.equals(trimToNull(row.getSourceEnum()));
    }

    /**
     * 判定快照行来源是否为中医证候（{@code SOURCE_TCM_SYNDROME}）。
     *
     * @param row 快照解析行
     * @return true 表示中医证候行
     */
    private boolean isTcmSyndromeSource(DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        return DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME.equals(trimToNull(row.getSourceEnum()));
    }

    /**
     * 构建诊断展示行排序比较器（主诊断优先，其次 sortNo）。
     *
     * @return 快照行比较器
     */
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

    /**
     * 格式化单条诊断展示行（主诊断前缀 {@code [主]} 标签）。
     *
     * @param row 快照解析行
     * @return 展示行文本；无有效名称时 null
     */
    private String formatDiagnosisDisplayLine(DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        String name = null;
        if (StringUtils.isNotEmpty(row.getConditionDefinitionName())) {
            name = row.getConditionDefinitionName().trim();
        }
        if (StringUtils.isEmpty(name) && StringUtils.isNotEmpty(row.getDiagnosisDesc())) {
            name = row.getDiagnosisDesc().trim();
        }
        if (StringUtils.isEmpty(name)) {
            return null;
        }
        if (row.getMainDiagnosisFlag() != null
            && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
            return '[' + DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_DISPLAY_TAG + ']' + name;
        }
        return name;
    }

    /**
     * 主诊断展示（登记/护士站列表用）。
     */
    public static final class PrimaryDiagnosisDisplay {

        /** 主诊断目录定义主键。 */
        private Long primaryConditionDefinitionId;
        /** 主诊断展示名称。 */
        private String primaryConditionDefinitionName;

        /** @return 主诊断目录定义主键。 */
        public Long getPrimaryConditionDefinitionId() {
            return primaryConditionDefinitionId;
        }

        /** @param primaryConditionDefinitionId 主诊断目录定义主键。 */
        public void setPrimaryConditionDefinitionId(Long primaryConditionDefinitionId) {
            this.primaryConditionDefinitionId = primaryConditionDefinitionId;
        }

        /** @return 主诊断展示名称。 */
        public String getPrimaryConditionDefinitionName() {
            return primaryConditionDefinitionName;
        }

        /** @param primaryConditionDefinitionName 主诊断展示名称。 */
        public void setPrimaryConditionDefinitionName(String primaryConditionDefinitionName) {
            this.primaryConditionDefinitionName = primaryConditionDefinitionName;
        }
    }
}
