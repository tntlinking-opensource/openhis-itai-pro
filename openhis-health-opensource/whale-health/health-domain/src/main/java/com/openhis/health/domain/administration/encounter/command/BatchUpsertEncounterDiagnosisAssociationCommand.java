/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量维护就诊诊断关联命令：按就诊合并诊断列表（含中西医扩展字段）。
 *
 * <p>匹配存量行优先 {@code encounterDiagnosisId}，否则按 {@code conditionDefinitionId}
 * （中医另加 {@code tcmSyndromeGroup}）对齐，不再经 {@code cln_condition}。</p>
 */
@Getter
@Setter
public class BatchUpsertEncounterDiagnosisAssociationCommand extends BatchCommand<BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单就诊批量诊断项。 */
    @Getter
    @Setter
    public static class BatchUpsertEncounterDiagnosisAssociationItemCommand {
        /** 就诊主键 {@code adm_encounter.id}。 */
        private Long encounterId;
        /** 本批次全量诊断条目列表。 */
        private List<DiagnosisItemCommand> diagnosisItems;
    }

    /** 单条诊断明细：目录定义 + 可选存量就诊诊断行主键。 */
    @Getter
    @Setter
    public static class DiagnosisItemCommand {
        /** 就诊诊断关联行主键 {@code adm_encounter_diagnosis.id}；更新时必填。 */
        private Long encounterDiagnosisId;
        /** 诊断目录主键 {@code cln_condition_definition.id}；新建时必填。 */
        private Long conditionDefinitionId;
        /** 是否主诊断。 */
        private Boolean primaryFlag;
        /** 排序序号。 */
        private Integer rank;
        /** 诊断描述。 */
        private String description;
        /** 诊断类型码（术语 code 字符串）。 */
        private String diagTypeCode;
        /** 诊断编码（历史列，可空）。 */
        private Integer diagCode;
        /** 病情编码（历史列，可空）。 */
        private Integer conditionCode;
        /** 医保或诊疗类别。 */
        private String medType;
        /** 是否最高诊断标识。 */
        private Boolean highestDiagFlag;
        /** 中医证候组。 */
        private String tcmSyndromeGroup;
        /** 是否中医诊断。 */
        private Boolean tcmFlag;
    }
}
