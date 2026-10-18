/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.response;

import com.zxplat.application.basic.inventoryopening.dto.LocationOpeningOptionDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 库存期初页初始化响应（IF-BAS-INV-OPN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初页初始化响应")
public class LoadInventoryOpeningFormInitResponse {

    /** 药库选项（含关闭标志）。 */
    @Schema(description = "药库选项")
    private List<LocationOpeningOptionDto> warehouseOptions;

    /** 药房选项。 */
    @Schema(description = "药房选项")
    private List<LocationOpeningOptionDto> pharmacyOptions;

    /** 耗材房/科室药房选项。 */
    @Schema(description = "耗材房选项")
    private List<LocationOpeningOptionDto> departmentPharmacyOptions;

    /** 供应状态下拉。 */
    @Schema(description = "状态选项")
    private List<OptionDto> statusOptions;

    /** 经手人下拉（可选）。 */
    @Schema(description = "经手人选项")
    private List<OptionDto> practitionerOptions;

    /** 供应商下拉（裁决 1A：行上供应商必填）。 */
    @Schema(description = "供应商选项")
    private List<OptionDto> supplierOptions;
}
