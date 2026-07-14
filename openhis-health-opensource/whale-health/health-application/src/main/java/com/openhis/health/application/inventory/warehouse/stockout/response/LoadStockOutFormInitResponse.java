/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库出库页初始化响应（IF-WHS-SO-01）。
 *
 * <p>各下拉 {@code value} 为持久化主键或枚举 {@code code}（B.10.8.1）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class LoadStockOutFormInitResponse {

    /** 源药库下拉。 */
    @Schema(description = "源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 目的药房下拉。 */
    @Schema(description = "目的药房下拉")
    private List<OptionDto> pharmacyLocationOptions;

    /** 入库项目类型两档。 */
    @Schema(description = "入库项目类型两档")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态下拉。 */
    @Schema(description = "单据状态下拉")
    private List<OptionDto> statusOptions;

    /** 药师下拉。 */
    @Schema(description = "药师下拉")
    private List<OptionDto> pharmacistOptions;

    /** 库管下拉。 */
    @Schema(description = "库管下拉")
    private List<OptionDto> warehouseKeeperOptions;
}
