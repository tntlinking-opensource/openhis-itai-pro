/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位分页响应（6.x 旧版 API 别名，对应 PageGroupCheckupOrgsResponse）。
 */
@Getter
@Setter
public class PageGroupOrgsResponse extends PagedResult<GroupOrgDto> {
}
