/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import com.zxplat.application.inventory.warehouse.returnstockin.dto.ReturnStockInListRowDto;
import com.zxplat.application.shared.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联药库出库/配送单分页出参（IF-WHS-RSI-12，无主动建单入口：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "可关联出库/配送单分页 IF-WHS-RSI-12（已拒绝）")
public class PageLinkableDeliveriesResponse extends PagedResult<ReturnStockInListRowDto> {
}
