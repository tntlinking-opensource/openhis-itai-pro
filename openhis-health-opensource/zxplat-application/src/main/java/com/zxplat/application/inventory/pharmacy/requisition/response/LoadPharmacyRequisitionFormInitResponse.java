/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.requisition.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药房请领页初始化响应（IF-PHA-QL-01）。
 *
 * <p>返回目的药房、来源药库与单据状态下拉；位置选项经 {@code CurrentManagedLocationIdsResolver} 管辖收窄。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房请领页初始化 IF-PHA-QL-01")
public class LoadPharmacyRequisitionFormInitResponse {

    /** 目的药房下拉。 */
    @Schema(description = "目的药房下拉")
    private List<OptionDto> pharmacyLocationOptions;

    /** 来源药库下拉。 */
    @Schema(description = "来源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 单据状态下拉 SupplyStatus。 */
    @Schema(description = "单据状态下拉 SupplyStatus")
    private List<OptionDto> statusOptions;
}
