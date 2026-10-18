/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 一键生成未来 N 天号源请求。
 */
@Getter
@Setter
public class GenerateHcFutureDaysRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 生成天数。 */
    @NotNull
    private Integer days;

    /** 起始日期（为空则从明天起）。 */
    private LocalDate fromDate;

    /** 校区编码列表（校区模块启用时可选覆盖模板）。 */
    private List<String> campusCodes;
}
