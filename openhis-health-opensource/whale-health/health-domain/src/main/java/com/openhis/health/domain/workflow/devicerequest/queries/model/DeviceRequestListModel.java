/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 就诊内耗材请求列表行模型。
 */
@Getter
@Setter
public class DeviceRequestListModel {
    /** 主键。 */
    private Long id;
    /** 设备def主键。 */
    private Long deviceDefId;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 原耗材申请主键（退单逆向行非空）。 */
    private Long refundDeviceId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 业务号。 */
    private String busNo;
    /** 生成来源持久化码。 */
    private String generateSourceEnum;
    /** 开立时间。 */
    private LocalDateTime reqAuthoredTime;
    /** 医生站列表展示排序号。 */
    private Integer sortNumber;
    /** 请求人主键。 */
    private Long requesterId;
    /** 数量。 */
    private BigDecimal quantity;
    /** 单位编码。 */
    private String unitCode;
    /** 产品批号。 */
    private String lotNumber;
    /** 备注。 */
    private String remark;
    /** 发材/执行位置 id。 */
    private Long performLocation;
    /** 开立位置 id。 */
    private Long locationId;
    /** 用药频次编码（耗材复用频次场景）。 */
    private String rateCode;
    /** 规格说明文本。 */
    private String deviceSpecifications;
    /** 前置业务锚表名。 */
    private String basedOnTable;
    /** 前置业务主键。 */
    private Long basedOnId;
    /** 诊断定义主键（持久化 {@code wkf_device_request.condition_definition_id}）。 */
    private Long conditionDefinitionId;
    /** 就诊诊断主键（持久化 {@code wkf_device_request.encounter_diagnosis_id}）。 */
    private Long encounterDiagnosisId;
}
