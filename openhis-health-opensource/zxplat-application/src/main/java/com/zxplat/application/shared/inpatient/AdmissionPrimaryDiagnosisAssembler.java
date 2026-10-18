/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inpatient;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.shared.diagnosis.DiagnosisJsonSnapshotParser;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionYbMappingQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.utils.TcmDiagnosisPresentationUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 从住院读模型 {@code diagnosisJson} 快照解析诊断展示（登记弹窗、护士站列表/详情、医生站队列摘要）。
 *
 * <p>登记弹窗西医主诊名称见 {@link #resolvePrimaryDiagnosisForRegistration}（国临行回落医保名）；
 * 其它场景用 {@link #resolvePrimaryDiagnosis} 直接取目录名。</p>
 */
@Component
@RequiredArgsConstructor
public class AdmissionPrimaryDiagnosisAssembler {

    /** 诊断 JSON 快照解析器（住院读模型 {@code diagnosisJson} → 结构化行）。 */
    private final DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser;
    /** 诊断目录读侧：国临主诊反查医保名称（登记弹窗展示用）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * 解析主诊断展示（诊断目录 id + 目录名称）。
     *
     * @param diagnosisJson 住院读模型诊断快照 JSON
     * @return 主诊断展示对象
     */
    public PrimaryDiagnosisDisplay resolvePrimaryDiagnosis(String diagnosisJson) {
        PrimaryDiagnosisDisplay display = new PrimaryDiagnosisDisplay();
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
            diagnosisJsonSnapshotParser.parse(diagnosisJson);
        // Gate：无快照行则返回空展示
        if (rows.isEmpty()) {
            return display;
        }
        DiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow = null;
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
            // 业务分支：主诊标记命中则定为主诊行
            if (row.getMainDiagnosisFlag() != null
                && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryRow = row;
                break;
            }
        }
        // 业务分支：无主诊标记时回落首行
        if (primaryRow == null) {
            primaryRow = rows.get(0);
        }
        display.setPrimaryConditionDefinitionId(this.resolvePrimaryDefinitionId(rows, primaryRow));
        String displayName = this.resolvePrimaryDisplayName(rows, primaryRow);
        // 业务分支：有可展示名称才写入
        if (StringUtils.isNotEmpty(displayName)) {
            display.setPrimaryConditionDefinitionName(displayName.trim());
        }
        return display;
    }

    /**
     * 解析登记弹窗主诊断展示：西医国临行优先回落医保名称（申请/登记阶段尚未进入医生站国临展示）。
     *
     * <p>目录 id 仍为快照真源；仅名称在有国临→医保对码时改用医保名。中医不变。</p>
     *
     * @param diagnosisJson 住院读模型诊断快照 JSON
     * @return 主诊断展示对象
     */
    public PrimaryDiagnosisDisplay resolvePrimaryDiagnosisForRegistration(String diagnosisJson) {
        // Pre：先按目录名解析主诊 id/默认名称
        PrimaryDiagnosisDisplay display = this.resolvePrimaryDiagnosis(diagnosisJson);
        List<DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rows =
            diagnosisJsonSnapshotParser.parse(diagnosisJson);
        // Gate：无行则沿用空展示
        if (rows.isEmpty()) {
            return display;
        }
        DiagnosisJsonSnapshotParser.ParsedDiagnosisRow primaryRow = null;
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rows) {
            // 业务分支：主诊标记命中
            if (row.getMainDiagnosisFlag() != null
                && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryRow = row;
                break;
            }
        }
        // 业务分支：无主诊标记回落首行
        if (primaryRow == null) {
            primaryRow = rows.get(0);
        }
        // 业务分支：中医主诊不走国临→医保名称回落
        if (this.isTcmDiagnosisRow(primaryRow)) {
            return display;
        }
        // 业务分支：非国临目录（已是医保等）直接用目录名
        if (!DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(trimToNull(primaryRow.getSourceEnum()))) {
            return display;
        }
        String clinicCode = trimToNull(primaryRow.getConditionCode());
        // Gate：国临码缺失则无法反查医保名
        if (clinicCode == null) {
            return display;
        }
        // Pre：按国临码反查医保名称（兼容历史已展开为国临的住院线）
        Map<String, ConditionYbMappingQueryModel> ybByClinic =
            conditionDefinitionQueryService.mapYbMappingByClinicCodes(List.of(clinicCode));
        ConditionYbMappingQueryModel mapped = ybByClinic.get(clinicCode);
        // 业务分支：无对码名称则保留国临目录名
        if (mapped == null || StringUtils.isEmpty(mapped.getYbName())) {
            return display;
        }
        // Asm：登记弹窗展示医保名
        display.setPrimaryConditionDefinitionName(mapped.getYbName().trim());
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
