/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药库出库页初始化请求（IF-WHS-SO-01）。
 *
 * <p>机构上下文由 {@code CurrentManagedOrganizationIdsResolver} 从会话解析，请求体可空。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库出库页初始化请求 IF-WHS-SO-01")
public class LoadStockOutFormInitRequest {
}
