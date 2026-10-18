/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检名单分页请求（6.x 旧版 API 别名，对应 PageGroupCheckupRostersRequest）。
 */
@Getter
@Setter
public class PageGroupRostersRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** 搜索关键词。 */
    private String keyword;

    /** 页码（从 1 开始）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
