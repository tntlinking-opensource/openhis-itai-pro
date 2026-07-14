/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.gainloss.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库损益页初始化响应（IF-WHS-GL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库损益页初始化 IF-WHS-GL-01")
public class LoadGainLossFormInitResponse {

    /** 药库下拉 warehouseLocationOptions。 */
    @Schema(description = "药库下拉 warehouseLocationOptions")
    private List<OptionDto> warehouseLocationOptions;

    /** 入库项目类型 inboundItemTableTypeOptions。 */
    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 状态下拉 statusOptions。 */
    @Schema(description = "状态下拉 statusOptions")
    private List<OptionDto> statusOptions;

    /** 药师下拉 pharmacistOptions，可空。 */
    @Schema(description = "药师下拉 pharmacistOptions，可空")
    private List<OptionDto> pharmacistOptions;

    /** 库管下拉 warehouseKeeperOptions，可空。 */
    @Schema(description = "库管下拉 warehouseKeeperOptions，可空")
    private List<OptionDto> warehouseKeeperOptions;
}
