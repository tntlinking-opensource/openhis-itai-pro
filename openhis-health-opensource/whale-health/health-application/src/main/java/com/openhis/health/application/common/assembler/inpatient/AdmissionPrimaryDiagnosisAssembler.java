/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.common.assembler.inpatient;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 从住院读模型 {@code diagnosisJson} 快照解析诊断展示（登记弹窗、护士站列表/详情）。
 *
 * <p>读模型列仍为 JSON 快照；应用层出站统一转为 {@link List}，禁止向 API 透传原始 JSON 字符串。
 * 解析口径与 {@link DoctorStationDiagnosisMapper#parseDiagnosisJson} 一致。</p>
 */
@Component
public class AdmissionPrimaryDiagnosisAssembler {

    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;

    public AdmissionPrimaryDiagnosisAssembler(DoctorStationDiagnosisMapper doctorStationDiagnosisMapper) {
        this.doctorStationDiagnosisMapper = doctorStationDiagnosisMapper;
    }

    /**
     * 解析主诊断定义 ID 与名称；无主诊断时字段为 null。
     *
     * @param diagnosisJson 读模型诊断快照 JSON
     * @return 主诊断展示载荷
     */
    public PrimaryDiagnosisDisplay resolvePrimaryDiagnosis(String diagnosisJson) {
        PrimaryDiagnosisDisplay display = new PrimaryDiagnosisDisplay();
        List<EncounterDiagnosisRowDto> rows = doctorStationDiagnosisMapper.parseDiagnosisJson(diagnosisJson);
        if (rows == null || rows.isEmpty()) {
            return display;
        }
        EncounterDiagnosisRowDto primaryRow = null;
        for (EncounterDiagnosisRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getMainDiagnosisFlag() != null
                && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryRow = row;
                break;
            }
        }
        if (primaryRow == null) {
            primaryRow = rows.get(0);
        }
        if (primaryRow == null) {
            return display;
        }
        display.setPrimaryConditionDefinitionId(primaryRow.getConditionDefinitionId());
        if (StringUtils.isNotBlank(primaryRow.getConditionDefinitionName())) {
            display.setPrimaryConditionDefinitionName(primaryRow.getConditionDefinitionName().trim());
        }
        return display;
    }

    /**
     * 将读模型诊断快照解析为展示行列表（每条一行文案，供护士站 {@code diagnosisList} 等）。
     *
     * @param diagnosisJson 读模型 {@code diagnosis_json} 列
     * @return 非 null 列表；无诊断时为空列表
     */
    public List<String> resolveDiagnosisDisplayLines(String diagnosisJson) {
        List<EncounterDiagnosisRowDto> rows = doctorStationDiagnosisMapper.parseDiagnosisJson(diagnosisJson);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：过滤空行后按主诊优先、排序号升序排列，保证列表与摘要顺序一致。
        List<EncounterDiagnosisRowDto> ordered = new ArrayList<>();
        for (EncounterDiagnosisRowDto row : rows) {
            if (row != null) {
                ordered.add(row);
            }
        }
        if (ordered.isEmpty()) {
            return Collections.emptyList();
        }
        ordered.sort(this.buildDiagnosisRowDisplayComparator());
        List<String> lines = new ArrayList<>(ordered.size());
        for (EncounterDiagnosisRowDto row : ordered) {
            String line = this.formatDiagnosisDisplayLine(row);
            if (StringUtils.isNotBlank(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 多条诊断合并为一行摘要（护士站列表卡片 {@code diagnosisSummary}）。
     *
     * @param diagnosisJson 读模型诊断快照 JSON
     * @return 摘要；无有效诊断名时返回 null
     */
    public String resolveDiagnosisSummary(String diagnosisJson) {
        return this.joinDiagnosisDisplayLines(this.resolveDiagnosisDisplayLines(diagnosisJson));
    }

    /**
     * 将已解析的展示行合并为摘要（避免同一快照重复 parse）。
     *
     * @param displayLines 展示行列表
     * @return 摘要；空列表时返回 null
     */
    public String joinDiagnosisDisplayLines(List<String> displayLines) {
        if (displayLines == null || displayLines.isEmpty()) {
            return null;
        }
        return String.join(DoctorStationDiagnosisConstants.DIAGNOSIS_DISPLAY_SUMMARY_SEPARATOR, displayLines);
    }

    /**
     * 诊断展示排序：主诊优先，其次按 {@code diagnosisSortNo} 升序。
     *
     * @return 比较器
     */
    private Comparator<EncounterDiagnosisRowDto> buildDiagnosisRowDisplayComparator() {
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
     * 单条诊断展示文案：仅输出名称类字段，不拼接目录编码/业务号；主诊带 {@link DoctorStationDiagnosisConstants#MAIN_DIAGNOSIS_DISPLAY_TAG} 前缀。
     *
     * @param row 快照行
     * @return 展示行或 null
     */
    private String formatDiagnosisDisplayLine(EncounterDiagnosisRowDto row) {
        if (row == null) {
            return null;
        }
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
        StringBuilder line = new StringBuilder();
        if (row.getMainDiagnosisFlag() != null
            && row.getMainDiagnosisFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
            line.append('[')
                .append(DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_DISPLAY_TAG)
                .append(']')
                .append(name);
        } else {
            line.append(name);
        }
        return line.toString();
    }

    /**
     * 主诊断展示字段（登记上下文 IF-IP-REG-02）。
     */
    public static final class PrimaryDiagnosisDisplay {

        /** 主诊断定义主键。 */
        private Long primaryConditionDefinitionId;

        /** 主诊断定义名称。 */
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
