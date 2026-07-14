/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 价格调整页初始化请求（IF-PRC-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "价格调整页初始化请求")
public class InitPriceAdjustmentRequest {
}
