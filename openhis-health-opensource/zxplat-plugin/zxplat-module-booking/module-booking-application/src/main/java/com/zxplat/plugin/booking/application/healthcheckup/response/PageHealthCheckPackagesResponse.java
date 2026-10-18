/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐分页响应（9.1）。
 */
@Getter
@Setter
public class PageHealthCheckPackagesResponse extends PagedResult<HealthCheckPackageDto> {
}
