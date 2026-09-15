/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 列出可约时段请求。
 */
@Getter
@Setter
public class ListHcBookableSlotsRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日期。 */
    @NotNull
    private LocalDate planDate;

    /** 合同类型编码。 */
    @NotNull
    private String contractTypeCode;

    /** 校区编码（校区模块启用时可选）。 */
    private String campusCode;
}
