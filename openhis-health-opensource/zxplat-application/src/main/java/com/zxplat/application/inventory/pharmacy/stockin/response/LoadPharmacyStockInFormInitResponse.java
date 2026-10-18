/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stockin.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药房入库页初始化响应（IF-PHA-IN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房入库页初始化 IF-PHA-IN-01")
public class LoadPharmacyStockInFormInitResponse {

    /** 目的药房下拉。 */
    @Schema(description = "目的药房下拉")
    private List<OptionDto> pharmacyLocationOptions;

    /** 来源药库下拉。 */
    @Schema(description = "来源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 入库项目类型两档。 */
    @Schema(description = "入库项目类型两档")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态下拉 SupplyStatus。 */
    @Schema(description = "单据状态下拉 SupplyStatus")
    private List<OptionDto> statusOptions;

    /** 药师人员下拉，可空。 */
    @Schema(description = "药师人员下拉，可空")
    private List<OptionDto> pharmacistOptions;

    /** 库管人员下拉，可空。 */
    @Schema(description = "库管人员下拉，可空")
    private List<OptionDto> warehouseKeeperOptions;
}
