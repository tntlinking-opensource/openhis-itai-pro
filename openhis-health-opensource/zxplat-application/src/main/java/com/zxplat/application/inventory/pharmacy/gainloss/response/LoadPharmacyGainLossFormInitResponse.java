/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.gainloss.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 药房损益页初始化出参（IF-PHA-GL-01；仅药房位置列表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "损益页初始化 IF-PHA-GL-01")
public class LoadPharmacyGainLossFormInitResponse {

    /** 药房下拉 pharmacyLocationOptions。 */
    @Schema(description = "药房下拉 pharmacyLocationOptions")
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
