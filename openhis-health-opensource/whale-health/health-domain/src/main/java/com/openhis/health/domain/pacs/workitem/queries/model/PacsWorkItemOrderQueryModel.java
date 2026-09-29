/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 检查工单医嘱关联读模型。
 */
@Getter
@Setter
public class PacsWorkItemOrderQueryModel {

    /** 子实体主键。 */
    private Long id;

    /** 工单主键。 */
    private Long workItemId;

    /** ServiceRequest 主键。 */
    private Long serviceRequestId;

    /** ActivityDefinition 主键。 */
    private Long activityId;

    /** 关联态枚举 code。 */
    private String linkStatusEnum;

    /** 合并组号。 */
    private String groupCode;

    /** 组内排序。 */
    private Integer sortNumber;
}
