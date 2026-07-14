/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

/**
 * 任务自定义参数键值对（持久化在 JobDataMap / whale_qrtz_job_details.job_data）。
 */
@Data
public class ScheduledJobParameterDto {

    /** 参数键。 */
    private String key;

    /** 参数值（字符串形式，任务内自行解析）。 */
    private String value;
}
