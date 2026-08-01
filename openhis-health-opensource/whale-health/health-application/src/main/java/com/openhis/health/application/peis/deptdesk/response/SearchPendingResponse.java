/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.response;

import com.openhis.health.application.peis.deptdesk.dto.PendingDeskRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分科诊台待检列表分页响应（IF-PEIS-DES-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingResponse extends PagedResult<PendingDeskRowDto> {
}
