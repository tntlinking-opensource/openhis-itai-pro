/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 库存行本库仍在库追溯码弹窗行（IF-INV-DTL-13）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存行本库仍在库追溯码行")
public class InventoryItemTraceNumberRowDto {

    @Schema(description = "追溯码；可跳转追溯码查询报表")
    /** 追溯码；可跳转追溯码查询报表。 */
    private String traceNo;

    @Schema(description = "本库净拆零量")
    /** 本库净拆零量。 */
    private BigDecimal locationNetSplitQuantity;

    @Schema(description = "追溯码单位编码")
    /** 追溯码单位编码。 */
    private String traceUnitCode;

    @Schema(description = "全院在院状态 code")
    /** 全院在院状态 code。 */
    private String inHospitalStatusEnum;

    @Schema(description = "全院在院状态展示名")
    /** 全院在院状态展示名。 */
    private String inHospitalStatusLabel;
}
