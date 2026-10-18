/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import lombok.Data;

/**
 * 定时任务配置页租户下拉选项。
 */
@Data
public class ScheduledJobTenantOptionDto {

    /** 租户 ID。 */
    private Long id;

    /** 租户名称。 */
    private String name;

    /** 租户代码。 */
    private String code;
}
