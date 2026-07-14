/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检批次分页请求（6.x 旧版 API 别名，对应 PageGroupCheckupBatchesRequest）。
 */
@Getter
@Setter
public class PageGroupBatchesRequest {

    /** 团检单位主键。 */
    private Long groupOrgId;

    /** 页码（从 1 开始）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
