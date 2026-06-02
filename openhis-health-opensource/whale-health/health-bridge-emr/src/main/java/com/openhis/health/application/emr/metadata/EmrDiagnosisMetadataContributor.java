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

package com.openhis.health.application.emr.metadata;

import com.openhis.health.application.common.assembler.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.share.enums.EncounterClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 就诊诊断元数据贡献器（方案 C）：从门诊/住院就诊上下文快照解析 {@code diagnosis.*} 扁平键。
 *
 * <p>数据来源：上下文模型 {@code diagnosisJson}（领域物化 JSON 数组），经
 * {@link AdmissionPrimaryDiagnosisAssembler} 与医生站、护士站展示口径一致。
 * 对外契约仅输出结构化展示字符串，<b>禁止</b>将 {@code diagnosisJson} 原文写入上下文或 Response（B.6.2）。</p>
 *
 * <p>产出键（与元数据维护页 {@code source_path} 对齐）：</p>
 * <ul>
 *   <li>{@code diagnosis.primaryDisplay} — 主诊断一行展示，供 {@code value_source=DIAGNOSIS} 解析</li>
 *   <li>{@code diagnosis.summary} — 多条诊断 {@code ；} 拼接摘要</li>
 *   <li>{@code diagnosis.primaryConditionDefinitionId} — 主诊断目录定义主键（可选）</li>
 * </ul>
 *
 * <p>无诊断快照或上下文行缺失时静默不写键，由字段 {@code default_value} 兜底。</p>
 */
@Component
public class EmrDiagnosisMetadataContributor {

    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    public EmrDiagnosisMetadataContributor(
            EncounterQueryService encounterQueryService,
            EncounterContextFacade encounterContextFacade,
            AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler) {
        this.encounterQueryService = encounterQueryService;
        this.encounterContextFacade = encounterContextFacade;
        this.admissionPrimaryDiagnosisAssembler = admissionPrimaryDiagnosisAssembler;
    }

    /**
     * 按就诊类别选择上下文并写入 {@code diagnosis.primaryDisplay}、{@code diagnosis.summary} 等键。
     *
     * @param target 元数据扁平上下文，由 {@link HealthEmrMetadataContextAdapter} 传入
     * @param encounter 已加载的就诊实体（含 {@code classEnum} 与主键）
     */
    public void contribute(Map<String, String> target, Encounter encounter) {
        if (target == null || encounter == null || encounter.getId() == null) {
            return;
        }
        // Pre：按 AMB/EMER/IMP 路由上下文，取诊断快照 JSON（仅内存解析，不下发 API）
        String diagnosisJson = this.resolveDiagnosisJson(encounter);
        // 上下文尚未回填诊断或患者未下诊断：保持静默，页眉走 default_value
        if (StringUtils.isBlank(diagnosisJson)) {
            return;
        }
        // Asm：主诊断展示名（页眉 chip / primaryDiagnosis 字段）
        String primaryDisplay = this.resolvePrimaryDisplay(diagnosisJson);
        put(target, "diagnosis.primaryDisplay", primaryDisplay);
        AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primary =
            this.admissionPrimaryDiagnosisAssembler.resolvePrimaryDiagnosis(diagnosisJson);
        if (primary.getPrimaryConditionDefinitionId() != null) {
            put(target, "diagnosis.primaryConditionDefinitionId",
                String.valueOf(primary.getPrimaryConditionDefinitionId()));
        }
        // Asm：多条诊断摘要，与护士站列表 diagnosisSummary 口径一致
        put(target, "diagnosis.summary",
            this.admissionPrimaryDiagnosisAssembler.resolveDiagnosisSummary(diagnosisJson));
    }

    /**
     * 根据就诊类别 {@code classEnum} 从门诊或住院上下文加载 {@code diagnosisJson} 快照列。
     *
     * @param encounter 就诊实体，{@code getClassEnum()} 为 {@link EncounterClass} 的 code
     * @return 快照 JSON 字符串；无上下文行或空快照时返回 null
     */
    private String resolveDiagnosisJson(Encounter encounter) {
        Long encounterId = encounter.getId();
        String classEnum = encounter.getClassEnum();
        // 住院：仅查住院就诊上下文
        if (EncounterClass.IMP.getCode().equals(classEnum)) {
            return this.encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                .map(InpatientEncounterContextQueryModel::getDiagnosisJson)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        }
        // 门诊/急诊门诊：仅查门诊就诊上下文
        if (EncounterClass.AMB.getCode().equals(classEnum) || EncounterClass.EMER.getCode().equals(classEnum)) {
            return this.encounterContextFacade.getOutpatientContextByEncounterId(encounterId)
                .map(OutpatientEncounterContextQueryModel::getDiagnosisJson)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        }
        // 类别未知或历史数据：先门诊后住院，避免漏诊
        return this.encounterContextFacade.getOutpatientContextByEncounterId(encounterId)
            .map(OutpatientEncounterContextQueryModel::getDiagnosisJson)
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> this.encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                .map(InpatientEncounterContextQueryModel::getDiagnosisJson)
                .filter(StringUtils::isNotBlank)
                .orElse(null));
    }

    /**
     * 解析主诊断展示名：优先目录名称；无主诊名时取排序后首条展示行并去掉 {@code [主]} 前缀。
     *
     * @param diagnosisJson 诊断快照 JSON 数组
     * @return 主诊断展示文本；无法解析时返回 null
     */
    private String resolvePrimaryDisplay(String diagnosisJson) {
        AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primary =
            this.admissionPrimaryDiagnosisAssembler.resolvePrimaryDiagnosis(diagnosisJson);
        // 优先 ICD/目录主诊断名称，与护士站主诊断 chip 一致
        if (StringUtils.isNotBlank(primary.getPrimaryConditionDefinitionName())) {
            return primary.getPrimaryConditionDefinitionName().trim();
        }
        // 仅有自由文本诊断描述、无目录名时，回落到展示行首条
        List<String> lines = this.admissionPrimaryDiagnosisAssembler.resolveDiagnosisDisplayLines(diagnosisJson);
        if (lines == null || lines.isEmpty()) {
            // 快照存在但无法解析为展示行：不写 primaryDisplay，避免空串覆盖 default_value
            return null;
        }
        return this.stripPrimaryDisplayTag(lines.get(0));
    }

    /**
     * 去掉医生站展示行上的主诊标记前缀（如 {@code [主]高血压} → {@code 高血压}），供页眉单行展示。
     *
     * @param line 单条诊断展示行
     * @return 去掉方括号主诊标签后的文本；入参空白时返回 null
     */
    private String stripPrimaryDisplayTag(String line) {
        if (StringUtils.isBlank(line)) {
            return null;
        }
        String trimmed = line.trim();
        // 医生站行格式如 [主]2型糖尿病：去掉方括号标签，页眉只保留病名
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int end = trimmed.indexOf(']');
            if (end > 0 && end < trimmed.length() - 1) {
                return trimmed.substring(end + 1).trim();
            }
            // 格式异常（] 在末尾）：原样返回，避免截断为空
        }
        return trimmed;
    }

    /**
     * 仅当值非空白时写入上下文，与 {@link HealthEmrMetadataContextAdapter} 的 put 语义一致。
     *
     * @param target 上下文 Map
     * @param key 点分路径键
     * @param value 展示值
     */
    private static void put(Map<String, String> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }
}
