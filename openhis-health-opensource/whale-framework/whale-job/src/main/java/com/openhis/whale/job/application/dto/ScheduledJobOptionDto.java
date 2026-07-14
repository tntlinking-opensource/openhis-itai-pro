/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

/**
 * 枚举选项（状态/策略/预设等下拉）。
 */
@Data
public class ScheduledJobOptionDto {

    /** 选项 code。 */
    private String code;

    /** 中文标签。 */
    private String label;
}
