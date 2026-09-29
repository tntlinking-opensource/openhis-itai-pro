/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 本地补登记创建检查工单命令（无 ServiceRequest orderLink）。
 */
@Getter
@Setter
public class CreateLocalPacsWorkItemCommand {

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 登记/执行科室。 */
    private Long organizationId;

    /** 租户主键。 */
    private Long tenantId;

    /** 模态码。 */
    private String modalityCode;

    /** 检查部位码。 */
    private String bodySiteCode;

    /** 可执行项目主键；非空时校验 capability。 */
    private Long activityId;

    /** 执行设备主键。 */
    private Long deviceId;

    /** 计划检查时间。 */
    private LocalDateTime scheduledTime;

    /** 操作人主键。 */
    private Long operatorId;
}
