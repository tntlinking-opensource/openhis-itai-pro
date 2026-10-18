/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 维护单条就诊诊断关联命令：新增或更新一条诊断与就诊的关联及扩展属性。
 *
 * <p>更新时带 {@code encounterDiagnosisId}；新建时带 {@code conditionDefinitionId}，直挂诊断目录。</p>
 */
@Getter
@Setter
public class UpsertEncounterDiagnosisAssociationCommand extends BatchCommand<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条维护项。 */
    @Getter
    @Setter
    public static class UpsertEncounterDiagnosisAssociationItemCommand {
        /** 就诊主键 {@code adm_encounter.id}。 */
        private Long encounterId;
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
