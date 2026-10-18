/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.periodbalancedetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库房期初期末明细导出行 DTO（与 {@link PeriodBalanceDetailListRowDto} 同构）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房期初期末明细导出行")
public class PeriodBalanceDetailExportRowDto extends PeriodBalanceDetailListRowDto {
}
