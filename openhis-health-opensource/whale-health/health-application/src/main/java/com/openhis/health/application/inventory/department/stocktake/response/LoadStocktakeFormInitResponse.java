/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室药房盘点页初始化出参（IF-DEP-ST-01；仅 {@link com.openhis.health.domain.share.enums.LocationForm#DEPARTMENT_PHARMACY} 位置列表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点页初始化 IF-DEP-ST-01")
public class LoadStocktakeFormInitResponse {

    /** 科室药房下拉 pharmacyLocationOptions。 */
    @Schema(description = "科室药房下拉 pharmacyLocationOptions")
    private List<OptionDto> pharmacyLocationOptions;

    /** 入库项目类型 inboundItemTableTypeOptions。 */
    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 状态下拉 statusOptions。 */
    @Schema(description = "状态下拉 statusOptions")
    private List<OptionDto> statusOptions;

    /** 药师/经手人下拉 pharmacistOptions（科室盘点为全体在职医务人员）。 */
    @Schema(description = "经手人下拉 pharmacistOptions（全体在职医务人员，含拼音）")
    private List<OptionDto> pharmacistOptions;

    /** 库管下拉 warehouseKeeperOptions（科室盘点可为空，经手人已并入 pharmacistOptions）。 */
    @Schema(description = "库管下拉 warehouseKeeperOptions")
    private List<OptionDto> warehouseKeeperOptions;
}
