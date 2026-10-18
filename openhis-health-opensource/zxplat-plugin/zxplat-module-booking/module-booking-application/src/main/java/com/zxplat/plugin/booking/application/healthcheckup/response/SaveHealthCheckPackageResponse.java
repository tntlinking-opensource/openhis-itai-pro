/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存体检套餐响应（9.1）。
 */
@Getter
@Setter
public class SaveHealthCheckPackageResponse {

    /** 体检套餐主键。 */
    private Long id;
}
