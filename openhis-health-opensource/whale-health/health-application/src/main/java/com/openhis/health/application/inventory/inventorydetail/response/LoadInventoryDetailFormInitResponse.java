/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 库存明细页初始化出参（IF-INV-DTL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存明细页初始化 IF-INV-DTL-01")
public class LoadInventoryDetailFormInitResponse {
    /** 医保等级筛选项。 */
    @Schema(description = "医保等级筛选项")
    private List<OptionDto> chrgitmLvOptions;

    /** 供应状态筛选项。 */
    @Schema(description = "供应状态筛选项")
    private List<OptionDto> inventoryStatusOptions;

    /** 药品类型筛选项。 */
    @Schema(description = "药品类型筛选项")
    private List<OptionDto> medicationCategoryOptions;

    /** 耗材类型筛选项。 */
    @Schema(description = "耗材类型筛选项")
    private List<OptionDto> deviceCategoryOptions;

    /** 库存范围筛选项（数量等于0 / 数量大于0）。 */
    @Schema(description = "库存范围筛选项（数量等于0 / 数量大于0）")
    private List<OptionDto> inventoryRangeOptions;

    /** 管辖范围内启用态药库、药房与科室药房筛选项。 */
    @Schema(description = "管辖范围内启用态药库、药房与科室药房筛选项")
    private List<OptionDto> warehouseOptions;

    /** 供应商筛选项。 */
    @Schema(description = "供应商筛选项")
    private List<OptionDto> supplierOptions;

    /** 效期预警天数阈值。 */
    @Schema(description = "效期预警天数阈值")
    private Integer expireWarningDays;

    /** 库存预警包装单位阈值。 */
    @Schema(description = "库存预警包装单位阈值")
    private Integer minPackageQuantityWarning;
}
