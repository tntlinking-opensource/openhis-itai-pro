/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.recheck.response;

import com.zxplat.plugin.peis.application.recheck.dto.RecheckRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 复查列表分页响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchRecheckResponse extends PagedResult<RecheckRowDto> {
}
