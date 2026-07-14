/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.transferout.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库调出页初始化响应（IF-WHS-TR-01）。
 *
 * <p>字段名与药库调出模块对齐：{@code warehouseLocationOptions} 承载<strong>目的药库</strong>；
 * {@code pharmacyLocationOptions} 承载<strong>源药库</strong>。各下拉 {@code value} 为持久化主键或枚举
 * {@code code}（B.10.8.1）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class LoadTransferOutFormInitResponse {
    /** 源药库下拉。 */
    @Schema(description = "源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 目的药房下拉。 */

    @Schema(description = "目的药房下拉")
    private List<OptionDto> pharmacyLocationOptions;

    /** 调拨项目类型两档。 */
    @Schema(description = "调拨项目类型两档")
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
