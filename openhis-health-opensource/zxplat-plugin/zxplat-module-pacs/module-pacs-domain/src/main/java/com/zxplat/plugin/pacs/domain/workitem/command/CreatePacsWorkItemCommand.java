/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建检查工单命令。
 */
@Getter
@Setter
public class CreatePacsWorkItemCommand {

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

    /** 计划检查时间。 */
    private LocalDateTime scheduledTime;

    /** 操作人主键。 */
    private Long operatorId;

    /** 医嘱关联项，至少 1 条。 */
    private List<CreatePacsOrderLinkItem> orderLinks;
}
