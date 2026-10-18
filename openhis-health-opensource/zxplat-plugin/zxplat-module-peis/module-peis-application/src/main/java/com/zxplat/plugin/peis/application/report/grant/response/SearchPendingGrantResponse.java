/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.grant.response;

import com.zxplat.plugin.peis.application.report.grant.dto.GrantRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待发放列表分页响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingGrantResponse extends PagedResult<GrantRowDto> {
}
