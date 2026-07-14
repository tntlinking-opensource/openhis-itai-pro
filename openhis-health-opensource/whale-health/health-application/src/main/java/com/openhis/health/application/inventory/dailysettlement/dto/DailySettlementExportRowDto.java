/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库房日结导出行 DTO（与 {@link DailySettlementListRowDto} 同构）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房日结导出行")
public class DailySettlementExportRowDto extends DailySettlementListRowDto {
}
