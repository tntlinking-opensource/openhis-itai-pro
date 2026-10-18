/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import com.zxplat.framework.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务分页查询（内存过滤 Quartz 任务列表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduledJobQueryRequest extends BaseQueryRequest {

    /** 关键词（任务名/显示名/Bean 名）。 */
    private String keyword;

    /** 任务组精确匹配。 */
    private String jobGroup;

    /** 状态 code：NORMAL / PAUSE。 */
    private String status;

    /** 租户 ID（为空查全部；平台级任务 tenantId 为空）。 */
    private Long tenantId;
}
