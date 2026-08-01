/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.grant.response;

import com.openhis.health.application.peis.report.grant.dto.GrantRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待发放列表分页响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingGrantResponse extends PagedResult<GrantRowDto> {
}
