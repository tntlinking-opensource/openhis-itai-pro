/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.recheck.response;

import com.openhis.health.application.peis.recheck.dto.RecheckRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 复查列表分页响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchRecheckResponse extends PagedResult<RecheckRowDto> {
}
