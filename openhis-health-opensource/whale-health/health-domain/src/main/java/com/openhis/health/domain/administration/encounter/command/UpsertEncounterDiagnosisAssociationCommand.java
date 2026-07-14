/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 维护单条就诊诊断关联命令：新增或更新一条诊断与就诊的关联及扩展属性。
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
        /** 就诊主键。 */
        private Long encounterId;
        /** 病情/诊断条件主键。 */
        private Long conditionId;
        /** 是否主诊断。 */
        private Boolean primaryFlag;
        /** 排序序号。 */
        private Integer rank;
        /** 诊断描述。 */
        private String description;
        /** 诊断类型码（术语 code 字符串）。 */
        private String diagTypeCode;
        /** 诊断编码。 */
        private Integer diagCode;
        /** 病情编码。 */
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
