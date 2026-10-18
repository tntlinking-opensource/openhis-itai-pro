/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询个检可选套餐请求（6.x 旧版 API 别名）。
 */
@Getter
@Setter
public class ListIndividualPackagesRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;
}
