/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.response;

import com.openhis.health.application.peis.masterreview.dto.MasterPendingRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待总检列表分页响应（IF-PEIS-MAS-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingMasterResponse extends PagedResult<MasterPendingRowDto> {
}
