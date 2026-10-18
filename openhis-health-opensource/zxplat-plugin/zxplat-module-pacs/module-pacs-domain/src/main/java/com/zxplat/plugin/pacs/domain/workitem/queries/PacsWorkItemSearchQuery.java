/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查工单分页检索条件。
 */
@Getter
@Setter
public class PacsWorkItemSearchQuery {

    /** 登记/执行科室。 */
    private Long organizationId;

    /**
     * 排除科室（他科已检：排除本科室 completed）。
     */
    private Long excludeOrganizationId;

    /** 模态码。 */
    private String modalityCode;

    /** 时间范围起（按 createdAt）。 */
    private LocalDateTime dateFrom;

    /** 时间范围止（按 createdAt）。 */
    private LocalDateTime dateTo;
}
