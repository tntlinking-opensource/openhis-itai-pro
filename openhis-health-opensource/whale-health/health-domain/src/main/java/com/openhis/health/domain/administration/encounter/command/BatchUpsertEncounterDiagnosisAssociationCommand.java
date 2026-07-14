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
        /** 就诊主键。 */
        private Long encounterId;
        /** 诊断条目列表。 */
        private List<DiagnosisItemCommand> diagnosisItems;
    }

    /** 单条诊断明细。 */
    @Getter
    @Setter
    public static class DiagnosisItemCommand {
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
