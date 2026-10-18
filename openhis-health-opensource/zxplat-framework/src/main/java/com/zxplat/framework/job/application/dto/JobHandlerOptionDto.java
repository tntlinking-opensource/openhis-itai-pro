/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import lombok.Data;

/**
 * 可选的 IJob 处理器（供下拉选择）。
 */
@Data
public class JobHandlerOptionDto {

    /** Spring Bean 名称（jobBeanName）。 */
    private String beanName;

    /** 任务默认名称。 */
    private String jobName;

    /** 任务描述。 */
    private String description;
}
