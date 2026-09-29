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
 * 创建服务申请草稿命令。
 */
@Getter
@Setter
public class CreateServiceRequestDraftCommand extends BatchCommand<CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand> {

    /** 当前操作人（开嘱人真源，持久化 {@code wkf_service_request.requester_id}）。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class CreateServiceRequestDraftItemCommand {
        /** 就诊主键 {@code adm_encounter.id}。 */
        private Long encounterId;
        /** 患者主键 {@code adm_patient.id}，须与就诊归属一致。 */
        private Long patientId;
        /** 诊疗项目/活动定义主键 {@code wkf_activity_definition.id}。 */
        private Long activityId;
        /** 开立数量（与 {@link #unitCode} 成对）。 */
        private BigDecimal quantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 计划执行次数（临嘱必填；长嘱可空，按频次执行）。 */
        private Integer executionCount;
        /** 医嘱类别持久化码（长期/临时等）。 */
        private String categoryEnum;
        /**
         * 优先级持久化码（DM-WKF-004 PriorityLevel；检验/检查加急为 {@code EM}；空表示非急）。
         */
        private String priorityEnum;
        /** 治疗类别持久化码（治疗/检查等）。 */
        private String therapyEnum;
        /** 开嘱人 practitioner 主键（通常由外层 {@link #operatorId} 覆盖，兼容显式传入）。 */
        private Long requesterId;
        /** 明细引用行（检查部位、附加耗材等）。 */
        private List<DetailItemCommand> detailItems;
        /** 医嘱备注（展示用；与 {@link #entrustContent} 分立，嘱托正文不落本字段）。 */
        private String note;
        /**
         * 嘱托医嘱正文（持久化 {@code wkf_service_request.entrust_content}；
         * 住院嘱托行必填，见 AS-IP-DOR-003 · BR-IP-ORD-028）。
         */
        private String entrustContent;
        /** 生成来源持久化码（医生站列表与药品/耗材对称过滤）。 */
        private String generateSourceEnum;
        /** 医生站列表展示排序号（可选）。 */
        private Integer sortNumber;
        /**
         * 绑定就诊诊断主键有序串（医生站绑定诊断；持久化 {@code wkf_service_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
         */
        private String encounterDiagnosisIds;
        /** 用药频次编码（长期诊疗必填；持久化 {@code wkf_service_request.rate_code}）。 */
        private String rateCode;
        /** 执行科室（持久化 {@code wkf_service_request.org_id}）。 */
        private Long orgId;
        /** 执行地点（持久化 {@code wkf_service_request.location_id}）。 */
        private Long locationId;
        /** 费用性质覆盖（持久化 {@code wkf_service_request.contract_id}）。 */
        private Long contractId;
        /** 申请单/处方号（持久化 {@code wkf_service_request.prescription_no}）。 */
        private String prescriptionNo;
        /**
         * 来源锚点表名（皮试检查 SR 锚定皮试药 {@code med_medication_request} 等；与 {@link #basedOnId} 成对）。
         */
        private String basedOnTable;
        /**
         * 来源锚点主键（与 {@link #basedOnTable} 成对）。
         */
        private Long basedOnId;
        /**
         * 主手术标志；申请预填，执行可改。
         */
        private Boolean mainProcedureFlag;
        /**
         * 手术级别字典码（医师申请）。
         */
        private String procedureLevelCode;
        /**
         * 切口类别字典码（医师申请可预填）。
         */
        private String incisionTypeCode;
        /**
         * 麻醉方法字典码（医师申请可预填）。
         */
        private String anesthesiaMethodCode;
        /**
         * 手术时间（医师申请可填；执行护士可改）。
         */
        private LocalDateTime procedureTime;
        /**
         * 主刀医师。
         */
        private Long surgeonId;
        /**
         * 一助。
         */
        private Long firstAssistantId;
        /**
         * 二助。
         */
        private Long secondAssistantId;
        /**
         * 配合护士。
         */
        private Long nurseId;
        /**
         * 麻醉医师。
         */
        private Long anesthetistId;
        /**
         * 嵌套麻醉申请单号。
         */
        private String nestedAnesthesiaPrescriptionNo;
    }

    /** 明细行子命令（每行一个 target_table + target_id 引用）。 */
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
