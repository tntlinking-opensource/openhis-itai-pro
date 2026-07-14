/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.health.application.finance.priceadjustment.dto.AdjustableItemRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 可选调价项目搜索响应（IF-PRC-04）。
 */
@Schema(description = "可选调价项目搜索响应")
public class SearchAdjustableItemsResponse extends PagedResult<AdjustableItemRowDto> {
}
