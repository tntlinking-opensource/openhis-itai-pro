/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建检查工单时的医嘱关联项。
 */
@Getter
@Setter
public class CreatePacsOrderLinkItem {

    /** ServiceRequest 主键。 */
    private Long serviceRequestId;

    /** ActivityDefinition 主键。 */
    private Long activityId;

    /** 合并组号。 */
    private String groupCode;

    /** 组内排序。 */
    private Integer sortNumber;
}
