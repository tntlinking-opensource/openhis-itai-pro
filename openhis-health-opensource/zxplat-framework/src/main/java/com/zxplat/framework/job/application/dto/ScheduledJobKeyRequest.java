/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import lombok.Data;

/**
 * 定时任务键（对应 Quartz JobKey，持久化在 zxplat_qrtz_job_details）。
 */
@Data
public class ScheduledJobKeyRequest {

    /** Quartz 任务名称（job_name）。 */
    private String jobName;

    /** Quartz 任务分组（job_group）。 */
    private String jobGroup;
}
