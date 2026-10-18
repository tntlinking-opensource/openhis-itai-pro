/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 更新检查登记信息命令。
 */
@Getter
@Setter
public class UpdatePacsRegistrationCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 登记/执行科室；空则不改。 */
    private Long organizationId;

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

    /** 检查号；空且当前无号则生成。 */
    private String accessionNumber;

    /** 为 true 时执行 registered→scheduled（须保证 accession 非空）。 */
    private Boolean confirmSchedule;

    /** 操作人主键。 */
    private Long operatorId;
}
