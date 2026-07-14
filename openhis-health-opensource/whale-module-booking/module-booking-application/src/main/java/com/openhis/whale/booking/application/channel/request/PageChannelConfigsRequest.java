/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置分页请求（10.1）。
 */
@Getter
@Setter
public class PageChannelConfigsRequest {

    /** 机构/科室主键（可选筛选）。 */
    private Long organizationId;

    /** 关键字（渠道编码/名称）。 */
    private String keyword;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
