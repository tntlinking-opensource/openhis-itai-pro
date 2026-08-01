/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.bridge.metadata.context.diagnosis;

import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.utils.TcmDiagnosisPresentationUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 从 {@code diagnosisJson} 快照解析主诊断与摘要展示，与护士站/登记口径一致。
 */
@Component
@RequiredArgsConstructor
public class EmrDiagnosisSnapshotDisplayResolver {

    private final EmrDiagnosisJsonSnapshotParser EmrDiagnosisJsonSnapshotParser;

    /**
     * 从诊断快照 JSON 解析主诊断目录 ID 与展示名。
     *
     * @param diagnosisJson 就诊诊断快照数组 JSON；空或非法时返回空展示
     * @return 主诊断展示；无行时 id/name 均为 null
     */
    public PrimaryDiagnosisDisplay resolvePrimaryDiagnosis(String diagnosisJson) {
        PrimaryDiagnosisDisplay display = new PrimaryDiagnosisDisplay();
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
                EmrDiagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return display;
        }
        // Pre：优先取 mainDiagnosisFlag=1 的行；无标记时回退首行
        EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow = null;
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
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
     * 解析主诊断展示名：中医一病一症合并为「病名（证候）」；西医取目录名。
     *
     * @param rows       快照全量行
     * @param primaryRow 主诊断行
     * @return 展示名；无法解析时返回 {@code null}
     */
    private String resolvePrimaryDisplayName(
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows,
        EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow) {
        if (primaryRow == null) {
            return null;
        }
        if (this.isTcmDiagnosisRow(primaryRow)) {
            String groupKey = trimToNull(primaryRow.getTcmSyndromeGroup());
            if (groupKey != null) {
                // 中医：同组内分别取病名行与证候行合并展示
                EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow diseaseRow = null;
                EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow syndromeRow = null;
                for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
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
     * 主诊断目录 ID：中医优先取病名行定义 ID。
     */
    private Long resolvePrimaryDefinitionId(
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows,
        EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow) {
        if (primaryRow == null) {
            return null;
        }
        if (this.isTcmDiagnosisRow(primaryRow)) {
            String groupKey = trimToNull(primaryRow.getTcmSyndromeGroup());
            if (groupKey != null) {
                for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
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

    private boolean isTcmDiagnosisRow(EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
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
        if (DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME.equals(source)) {
            return true;
        }
        return false;
    }

    private boolean isTcmDiseaseSource(EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        return DoctorStationDiagnosisConstants.SOURCE_TCM_DX.equals(trimToNull(row.getSourceEnum()));
    }

    private boolean isTcmSyndromeSource(EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        return DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME.equals(trimToNull(row.getSourceEnum()));
    }

    /**
     * 解析诊断快照为有序展示行（主诊断前置，带「主」标签）。
     *
     * @param diagnosisJson 就诊诊断快照 JSON
     * @return 非空展示行列表；无有效行时返回空列表
     */
    public List<String> resolveDiagnosisDisplayLines(String diagnosisJson) {
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
                EmrDiagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        // Asm：主诊断优先、再按 sortNo 排序后逐行格式化
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> ordered = new ArrayList<>(rows);
        ordered.sort(buildDiagnosisRowDisplayComparator());
        List<String> lines = new ArrayList<>(ordered.size());
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : ordered) {
            String line = formatDiagnosisDisplayLine(row);
            if (StringUtils.isNotEmpty(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 西医诊断展示（仅 WESTERN 行，分号拼接；无「[主]」前缀）。
     *
     * @param diagnosisJson 就诊诊断快照 JSON
     * @return 展示文案；无西医行时返回 {@code null}
     */
    public String resolveWesternDiagnosisDisplay(String diagnosisJson) {
        return joinDiagnosisDisplayLines(resolveWesternDiagnosisDisplayLines(diagnosisJson));
    }

    /**
     * 中医诊断展示（病证组合并为「病名（证候）」，分号拼接；无「[主]」前缀）。
     *
     * @param diagnosisJson 就诊诊断快照 JSON
     * @return 展示文案；无中医行时返回 {@code null}
     */
    public String resolveTcmDiagnosisDisplay(String diagnosisJson) {
        return joinDiagnosisDisplayLines(resolveTcmDiagnosisDisplayLines(diagnosisJson));
    }

    /**
     * 西医诊断有序展示行（不含中医；名称侧无「[主]」前缀）。
     */
    public List<String> resolveWesternDiagnosisDisplayLines(String diagnosisJson) {
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
                EmrDiagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> western = new ArrayList<>();
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
            if (!this.isTcmDiagnosisRow(row)) {
                western.add(row);
            }
        }
        if (western.isEmpty()) {
            return Collections.emptyList();
        }
        western.sort(buildDiagnosisRowDisplayComparator());
        List<String> lines = new ArrayList<>(western.size());
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : western) {
            String name = plainDiagnosisName(row);
            if (StringUtils.isNotEmpty(name)) {
                lines.add(name);
            }
        }
        return lines;
    }

    /**
     * 中医诊断有序展示行（同组病证合并；名称侧无「[主]」前缀）。
     */
    public List<String> resolveTcmDiagnosisDisplayLines(String diagnosisJson) {
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
                EmrDiagnosisJsonSnapshotParser.parse(diagnosisJson);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> tcmRows = new ArrayList<>();
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
            if (this.isTcmDiagnosisRow(row)) {
                tcmRows.add(row);
            }
        }
        if (tcmRows.isEmpty()) {
            return Collections.emptyList();
        }
        tcmRows.sort(buildDiagnosisRowDisplayComparator());

        List<String> lines = new ArrayList<>();
        LinkedHashSet<String> emittedGroups = new LinkedHashSet<>();
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : tcmRows) {
            String groupKey = trimToNull(row.getTcmSyndromeGroup());
            if (groupKey != null) {
                if (!emittedGroups.add(groupKey)) {
                    continue;
                }
                String merged = formatTcmGroupDisplayName(tcmRows, groupKey, row);
                if (StringUtils.isNotEmpty(merged)) {
                    lines.add(merged);
                }
                continue;
            }
            String name = plainDiagnosisName(row);
            if (StringUtils.isNotEmpty(name)) {
                lines.add(name);
            }
        }
        return lines;
    }

    private String formatTcmGroupDisplayName(
            List<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> tcmRows,
            String groupKey,
            EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow fallbackRow) {
        EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow diseaseRow = null;
        EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow syndromeRow = null;
        for (EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : tcmRows) {
            if (!groupKey.equals(trimToNull(row.getTcmSyndromeGroup()))) {
                continue;
            }
            if (this.isTcmDiseaseSource(row)) {
                diseaseRow = row;
            } else if (this.isTcmSyndromeSource(row)) {
                syndromeRow = row;
            }
        }
        if (diseaseRow == null && this.isTcmDiseaseSource(fallbackRow)) {
            diseaseRow = fallbackRow;
        }
        if (syndromeRow == null && this.isTcmSyndromeSource(fallbackRow)) {
            syndromeRow = fallbackRow;
        }
        if (diseaseRow == null) {
            diseaseRow = fallbackRow;
        }
        String diseaseName = diseaseRow != null ? diseaseRow.getConditionDefinitionName() : null;
        String syndromeName = syndromeRow != null ? syndromeRow.getConditionDefinitionName() : null;
        return TcmDiagnosisPresentationUtil.formatGroupDisplayName(diseaseName, syndromeName);
    }

    private static String plainDiagnosisName(EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
        if (row == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(row.getConditionDefinitionName())) {
            return row.getConditionDefinitionName().trim();
        }
        if (StringUtils.isNotEmpty(row.getDiagnosisDesc())) {
            return row.getDiagnosisDesc().trim();
        }
        return null;
    }

    /**
     * 解析诊断快照并拼接为摘要字符串（护士站/队列卡片用）。
     *
     * @param diagnosisJson 就诊诊断快照 JSON
     * @return 分隔符拼接的摘要；无行时返回 null
     */
    public String resolveDiagnosisSummary(String diagnosisJson) {
        return joinDiagnosisDisplayLines(resolveDiagnosisDisplayLines(diagnosisJson));
    }

    /**
     * 将展示行列表拼接为摘要（使用医生站统一分隔符）。
     *
     * @param displayLines 已格式化的诊断展示行
     * @return 拼接结果；空列表时返回 null
     */
    public String joinDiagnosisDisplayLines(List<String> displayLines) {
        if (displayLines == null || displayLines.isEmpty()) {
            return null;
        }
        return String.join(DoctorStationDiagnosisConstants.DIAGNOSIS_DISPLAY_SUMMARY_SEPARATOR, displayLines);
    }

    private Comparator<EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow> buildDiagnosisRowDisplayComparator() {
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

    private String formatDiagnosisDisplayLine(EmrDiagnosisJsonSnapshotParser.ParsedDiagnosisRow row) {
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

    /** 主诊断解析结果（目录 ID + 展示名）。 */
    public static final class PrimaryDiagnosisDisplay {

        /** 主诊断目录定义主键。 */
        private Long primaryConditionDefinitionId;

        /** 主诊断展示名（中医已合并病证）。 */
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
