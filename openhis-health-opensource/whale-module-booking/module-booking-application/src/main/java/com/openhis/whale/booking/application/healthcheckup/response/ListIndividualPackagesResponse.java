/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 查询个检可选套餐响应（6.x 旧版 API 别名）。
 */
@Getter
@Setter
public class ListIndividualPackagesResponse {

    /** 体检套餐列表。 */
    private List<HealthCheckPackageDto> packages;
}
