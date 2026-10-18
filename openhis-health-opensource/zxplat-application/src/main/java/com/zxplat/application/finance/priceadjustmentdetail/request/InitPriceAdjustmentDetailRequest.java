/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustmentdetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 调价明细页初始化请求（IF-PRC-DTL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价明细页初始化请求")
public class InitPriceAdjustmentDetailRequest {
}
