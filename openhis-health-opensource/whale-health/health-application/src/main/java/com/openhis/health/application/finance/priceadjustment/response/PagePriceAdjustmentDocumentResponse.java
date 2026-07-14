/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.health.application.finance.priceadjustment.dto.PriceAdjustmentDocumentListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 调价单列表分页响应（IF-PRC-02）。
 */
@Schema(description = "调价单列表分页响应")
public class PagePriceAdjustmentDocumentResponse extends PagedResult<PriceAdjustmentDocumentListRowDto> {
}
