/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stocktake.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库盘点页初始化出参（IF-WHS-ST-01；仅药库位置列表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点页初始化 IF-WHS-ST-01")
public class LoadStocktakeFormInitResponse {

    /** 药库下拉 pharmacyLocationOptions。 */
    @Schema(description = "药库下拉 pharmacyLocationOptions")
    private List<OptionDto> pharmacyLocationOptions;

    /** 入库项目类型 inboundItemTableTypeOptions。 */
    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 状态下拉 statusOptions。 */
    @Schema(description = "状态下拉 statusOptions")
    private List<OptionDto> statusOptions;

    /** 药师下拉 pharmacistOptions。 */
    @Schema(description = "药师下拉 pharmacistOptions")
    private List<OptionDto> pharmacistOptions;

    /** 库管下拉 warehouseKeeperOptions。 */
    @Schema(description = "库管下拉 warehouseKeeperOptions")
    private List<OptionDto> warehouseKeeperOptions;
}
