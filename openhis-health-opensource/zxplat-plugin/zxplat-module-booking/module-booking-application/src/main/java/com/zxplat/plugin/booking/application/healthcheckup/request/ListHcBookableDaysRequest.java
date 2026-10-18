/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 列出可约日期请求。
 */
@Getter
@Setter
public class ListHcBookableDaysRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 合同类型编码。 */
    @NotNull
    private String contractTypeCode;

    /** 校区编码（校区模块启用时可选）。 */
    private String campusCode;

    /** 起始日期（含）。 */
    @NotNull
    private LocalDate fromDate;

    /** 结束日期（含）。 */
    @NotNull
    private LocalDate toDate;
}
