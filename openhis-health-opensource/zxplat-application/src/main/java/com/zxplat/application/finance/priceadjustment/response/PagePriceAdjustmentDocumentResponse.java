/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.response;

import com.zxplat.application.finance.priceadjustment.dto.PriceAdjustmentDocumentListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 调价单列表分页响应（IF-PRC-02）。
 */
@Schema(description = "调价单列表分页响应")
public class PagePriceAdjustmentDocumentResponse extends PagedResult<PriceAdjustmentDocumentListRowDto> {
}
