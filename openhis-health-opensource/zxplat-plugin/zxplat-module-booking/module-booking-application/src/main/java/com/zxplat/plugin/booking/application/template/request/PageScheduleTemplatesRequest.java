/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.template.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * T2 模板分页请求。
 */
@Getter
@Setter
public class PageScheduleTemplatesRequest {

    /** 科室主键。 */
    private Long organizationId;

    /** 关键词。 */
    private String keyword;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}
