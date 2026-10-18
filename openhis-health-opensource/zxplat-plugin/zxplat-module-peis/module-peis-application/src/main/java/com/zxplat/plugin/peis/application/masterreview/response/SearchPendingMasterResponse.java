/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.response;

import com.zxplat.plugin.peis.application.masterreview.dto.MasterPendingRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待总检列表分页响应（IF-PEIS-MAS-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingMasterResponse extends PagedResult<MasterPendingRowDto> {
}
