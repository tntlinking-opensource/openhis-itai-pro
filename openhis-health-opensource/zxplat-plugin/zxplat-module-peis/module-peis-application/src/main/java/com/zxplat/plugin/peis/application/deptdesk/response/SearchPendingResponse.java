/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.response;

import com.zxplat.plugin.peis.application.deptdesk.dto.PendingDeskRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分科诊台待检列表分页响应（IF-PEIS-DES-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingResponse extends PagedResult<PendingDeskRowDto> {
}
