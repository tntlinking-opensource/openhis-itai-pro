/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.response;

import com.zxplat.application.inventory.warehouse.returntosupplier.dto.LinkableGoodsReceiptListRowDto;
import com.zxplat.application.shared.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联进货单分页响应（IF-WHS-RT-12，仅含仍有可退余量的已同意进货单）。
 */
@Getter
@Setter
@Schema(description = "可关联进货单分页 IF-WHS-RT-12")
public class PageLinkableGoodsReceiptsResponse extends PagedResult<LinkableGoodsReceiptListRowDto> {
}
