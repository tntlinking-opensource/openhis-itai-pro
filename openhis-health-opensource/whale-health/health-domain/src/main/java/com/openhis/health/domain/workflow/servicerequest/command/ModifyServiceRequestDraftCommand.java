/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 修改服务申请草稿命令。
 */
@Getter
@Setter
public class ModifyServiceRequestDraftCommand extends BatchCommand<ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand> {

    /** 当前操作人（审计与部分字段覆盖写）。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class ModifyServiceRequestDraftItemCommand {
        /** 服务申请主键 {@code wkf_service_request.id}。 */
        private Long id;
        /** 修订后数量；非空时覆盖。 */
        private BigDecimal quantity;
        /** 修订后计量单位编码；非空时覆盖。 */
        private String unitCode;
        /** 计划执行次数（临嘱；长嘱可空）。 */
        private Integer executionCount;
        /** 医嘱类别持久化码；非空时覆盖。 */
        private String categoryEnum;
        /** 治疗类别持久化码；非空时覆盖。 */
        private String therapyEnum;
        /** 开嘱人 practitioner 主键；非空时覆盖。 */
        private Long requesterId;
        /** 修订原因/说明（审计留痕）。 */
        private String reasonText;
        /** 执行人 practitioner 主键；非空时覆盖。 */
        private Long performerId;
        /** 执行地点 {@code adm_location.id}；非空时覆盖。 */
        private Long locationId;
        /** 执行科室 {@code adm_organization.id}；非空时覆盖。 */
        private Long orgId;
        /** 行备注；非空时覆盖。 */
        private String remark;
        /** 嘱托内容；非空时覆盖。 */
        private String entrustContent;
        /** 明细引用全量替换列表；非 null 时软删旧明细后重建。 */
        private List<DetailItemCommand> detailItems;
        /** 计划执行开始时刻；非空时覆盖。 */
        private LocalDateTime occurrenceStartTime;
        /** 计划执行结束时刻；非空时覆盖。 */
        private LocalDateTime occurrenceEndTime;
        /** 医生站列表展示排序号；非空时更新。 */
        private Integer sortNumber;
        /**
         * 诊断定义主键（草稿修改；非空且与存量不同时更新 {@code wkf_service_request.condition_definition_id}）。
         */
        private Long conditionDefinitionId;
        /**
         * 就诊诊断主键（草稿修改；非空且与存量不同时更新 {@code wkf_service_request.encounter_diagnosis_id}）。
         */
        private Long encounterDiagnosisId;
        /**
         * 用药频次编码（长期诊疗必填；传空串表示清空 {@code rate_code}）。
         */
        private String rateCode;
    }

    /** 明细行子命令（为本命令独立类型，每行一个 target_table + target_id 引用）。 */
    @Getter
    @Setter
    public static class DetailItemCommand {
        /** 明细目标表名。 */
        private String targetTable;
        /** 明细目标表主键。 */
        private Long targetId;
        /** 备注。 */
        private String remark;
    }
}
