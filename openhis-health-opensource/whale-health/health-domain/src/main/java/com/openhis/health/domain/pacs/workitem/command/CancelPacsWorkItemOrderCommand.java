/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 作废检查工单单项医嘱关联命令。
 */
@Getter
@Setter
public class CancelPacsWorkItemOrderCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** ServiceRequest 主键。 */
    private Long serviceRequestId;

    /** 作废原因。 */
    private String reason;

    /** 操作人主键。 */
    private Long operatorId;
}
