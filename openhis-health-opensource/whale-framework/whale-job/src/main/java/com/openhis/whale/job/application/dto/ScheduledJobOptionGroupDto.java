/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务页面下拉选项分组。
 */
@Data
public class ScheduledJobOptionGroupDto {

    /** 分组 code，如 status、misfirePolicy。 */
    private String groupCode;

    /** 分组中文标题。 */
    private String groupLabel;

    /** 选项列表。 */
    private List<ScheduledJobOptionDto> options = new ArrayList<>();
}
