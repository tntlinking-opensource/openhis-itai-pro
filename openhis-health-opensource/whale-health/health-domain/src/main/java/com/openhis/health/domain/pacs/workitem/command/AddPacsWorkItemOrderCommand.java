/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 向检查工单追加医嘱关联命令。
 */
@Getter
@Setter
public class AddPacsWorkItemOrderCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** ServiceRequest 主键。 */
    private Long serviceRequestId;

    /** ActivityDefinition 主键。 */
    private Long activityId;

    /** 合并组号。 */
    private String groupCode;

    /** 组内排序。 */
    private Integer sortNumber;

    /** 操作人主键。 */
    private Long operatorId;
}
