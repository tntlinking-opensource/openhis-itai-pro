/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位分页请求（6.x 旧版 API 别名，对应 PageGroupCheckupOrgsRequest）。
 */
@Getter
@Setter
public class PageGroupOrgsRequest {

    /** 搜索关键词。 */
    private String keyword;

    /** 页码（从 1 开始）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
