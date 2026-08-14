/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 就诊内服务申请列表行模型。
 */
@Getter
@Setter
public class ServiceRequestListModel {
    /** 主键。 */
    private Long id;
    /** 就诊主键。 */
    private Long encounterId;
    /** therapy枚举编码。 */
    private String therapyEnum;
    /** 患者主键。 */
    private Long patientId;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 原诊疗申请主键（退单逆向行非空）。 */
    private Long refundServiceId;
    /** 诊疗活动定义主键。 */
    private Long activityId;
    /** 诊疗类目 code（如出院 34、转科 32）。 */
    private String categoryEnum;
    /** 生成来源持久化码。 */
    private String generateSourceEnum;
    /** 申请人主键。 */
    private Long requesterId;
    /** 校对人主键（持久化 performer_check_id，BR-SRQ-022）。 */
    private Long performerCheckId;
    /** 开立时间（{@code req_opened_time}）。 */
    private LocalDateTime reqOpenedTime;
    /** 签发时间（{@code req_authored_time}）。 */
    private LocalDateTime reqAuthoredTime;
    /** 医生站列表展示排序号。 */
    private Integer sortNumber;
    /** 数量。 */
    private BigDecimal quantity;
    /** 单位编码。 */
    private String unitCode;
    /** 计划执行次数（临嘱/门诊护士站拆解；长嘱按频次可为空）。 */
    private Integer executionCount;
    /** 请求扩展 JSON。 */
    private String contentJson;
    /** 出院/转科等原因文本（持久化 reason_text）。 */
    private String reasonText;
    /** 计划开始/出院时间（持久化 occurrence_start_time）。 */
    private LocalDateTime occurrenceStartTime;
    /** 备注。 */
    private String remark;
    /** 嘱托内容。 */
    private String entrustContent;
    /** 执行/开立位置 id。 */
    private Long locationId;
    /** 执行科室组织 id。 */
    private Long orgId;
    /** 费用性质/合同覆盖 id。 */
    private Long contractId;
    /** 用药频次编码（部分诊疗套系复用）。 */
    private String rateCode;
    /** 前置业务锚表名。 */
    private String basedOnTable;
    /** 前置业务主键。 */
    private Long basedOnId;
    /** 父级诊疗请求主键（套餐子项等；出院门禁须 parent 为空）。 */
    private Long parentId;
    /** 计划结束时间（临床计划窗；非停嘱）。 */
    private LocalDateTime occurrenceEndTime;
    /** 停嘱时间（{@code stop_time}；STOPPED 时有值）。 */
    private LocalDateTime stopTime;
    /** 停嘱人主键（{@code stopper_id}；未停 null）。 */
    private Long stopperId;
    /** 停嘱校对人主键（{@code stop_performer_check_id}；未停嘱校对 null）。 */
    private Long stopPerformerCheckId;
    /** 停嘱校对时间（{@code stop_check_time}）。 */
    private LocalDateTime stopCheckTime;
    /** 绑定就诊诊断主键有序串（持久化 {@code wkf_service_request.encounter_diagnosis_ids}；有序，首位为主诊断）。 */
    private String encounterDiagnosisIds;
    /** 处方/申请单号（持久化 {@code wkf_service_request.prescription_no}）。 */
    private String prescriptionNo;
}
