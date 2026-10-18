/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检查工单详情读模型（不含 PHI）。
 */
@Getter
@Setter
public class PacsWorkItemDetailQueryModel {

    /** 工单主键。 */
    private Long id;

    /** 检查号。 */
    private String accessionNumber;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 登记/执行科室。 */
    private Long organizationId;

    /** 检查执行态枚举 code。 */
    private String statusEnum;

    /** 模态码。 */
    private String modalityCode;

    /** 检查部位码。 */
    private String bodySiteCode;

    /** 执行设备主键。 */
    private Long deviceId;

    /** 执行人主键。 */
    private Long performerPractitionerId;

    /** 计划检查时间。 */
    private LocalDateTime scheduledTime;

    /** 实际完成时间。 */
    private LocalDateTime performedTime;

    /** 是否已有影像/关键图。 */
    private Boolean hasMedia;

    /** 关联医嘱数。 */
    private Integer orderCount;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 医嘱关联列表。 */
    private List<PacsWorkItemOrderQueryModel> orderLinks;
}
