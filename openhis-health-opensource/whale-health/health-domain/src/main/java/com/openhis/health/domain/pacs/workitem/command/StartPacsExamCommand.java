/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 开始检查执行命令。
 */
@Getter
@Setter
public class StartPacsExamCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 执行人主键。 */
    private Long performerPractitionerId;

    /** 执行设备主键。 */
    private Long deviceId;

    /** 操作人主键。 */
    private Long operatorId;
}
