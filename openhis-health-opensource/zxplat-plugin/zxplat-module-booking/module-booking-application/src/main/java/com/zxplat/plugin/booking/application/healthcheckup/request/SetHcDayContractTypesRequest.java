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
 * 设置日开放合同类型请求（全量替换）。
 */
@Getter
@Setter
public class SetHcDayContractTypesRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日期。 */
    @NotNull
    private LocalDate planDate;

    /** 合同类型编码列表（含 WALK_IN）。 */
    @NotNull
    private List<String> contractTypeCodes;
}
