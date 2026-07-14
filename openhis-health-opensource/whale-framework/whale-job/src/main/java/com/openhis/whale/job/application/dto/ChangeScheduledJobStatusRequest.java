/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

/**
 * 修改任务运行状态（NORMAL / PAUSE）。
 */
@Data
public class ChangeScheduledJobStatusRequest {

    /** 任务名。 */
    private String jobName;

    /** 任务组。 */
    private String jobGroup;

    /** 目标状态 code。 */
    private String status;
}
