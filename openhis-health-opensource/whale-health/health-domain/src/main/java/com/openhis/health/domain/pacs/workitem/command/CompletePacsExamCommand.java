/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 确认检查已完成命令。
 */
@Getter
@Setter
public class CompletePacsExamCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 实际完成时间；空则取当前时间。 */
    private LocalDateTime performedTime;

    /** 执行人主键。 */
    private Long performerPractitionerId;

    /** 执行设备主键。 */
    private Long deviceId;

    /** 操作人主键。 */
    private Long operatorId;
}
