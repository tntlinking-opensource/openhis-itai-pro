/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.response;

import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInListRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 退库入库壳单列表分页响应（IF-WHS-RSI-02）。
 *
 * <p>行数据为库房单据读模型展平（{@link ReturnStockInListRowDto}）。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库壳单列表分页 IF-WHS-RSI-02")
public class PageReturnStockInsResponse extends PagedResult<ReturnStockInListRowDto> {
}
