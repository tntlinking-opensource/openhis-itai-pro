/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库入库页初始化响应（IF-WHS-IN-01）。
 *
 * <p>药房与药库位置分两个列表返回，便于前端分控件绑定；
 * 下拉项统一为 {@link OptionDto}（B.10.5）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库页初始化 IF-WHS-IN-01")
public class LoadInboundReceiptFormInitResponse {

    /** 药房选项 pharmacyLocationOptions。 */
    @Schema(description = "药房选项 pharmacyLocationOptions")
    private List<OptionDto> pharmacyLocationOptions;

    /** 药库选项 warehouseLocationOptions。 */
    @Schema(description = "药库选项 warehouseLocationOptions")
    private List<OptionDto> warehouseLocationOptions;

    /** 供应商选项 supplierOptions。 */
    @Schema(description = "供应商选项 supplierOptions")
    private List<OptionDto> supplierOptions;

    /** 药师选项 pharmacistOptions。 */
    @Schema(description = "药师选项 pharmacistOptions")
    private List<OptionDto> pharmacistOptions;

    /** 库管员选项 warehouseKeeperOptions。 */
    @Schema(description = "库管员选项 warehouseKeeperOptions")
    private List<OptionDto> warehouseKeeperOptions;

    /** 入库项目类型 inboundItemTableTypeOptions。 */
    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态 statusOptions。 */
    @Schema(description = "单据状态 statusOptions")
    private List<OptionDto> statusOptions;
}
