/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.request;

import com.zxplat.application.inventory.warehouse.stockout.dto.StockOutLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 修订药库出库单请求（IF-WHS-SO-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class UpdateStockOutRequest {

    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 源药库。 */
    @Schema(description = "源药库", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 目的位置。 */
    @Schema(description = "目的位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 供应类别。 */
    @Schema(description = "供应类别", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /** 关联上游单号。 */
    @Schema(description = "关联上游单号")
    private String originalBusNo;

    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 明细行。 */
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StockOutLineDto> lines;
}
