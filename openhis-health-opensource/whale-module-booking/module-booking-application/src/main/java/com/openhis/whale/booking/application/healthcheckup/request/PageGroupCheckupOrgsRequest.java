/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位分页请求（9.3）。
 */
@Getter
@Setter
public class PageGroupCheckupOrgsRequest {

    /** 搜索关键词。 */
    private String keyword;

    /** 页码（从 1 开始）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
