/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 药库退供应商单同意请求（IF-WHS-RT-08：触发库存扣减与追溯出库）。
 */
@Schema(description = "同意药库退供应商单 IF-WHS-RT-08")
@Getter
@Setter
public class AgreeReturnToSupplierRequest {
    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 医保契约 busNo contractBusNo。 */
    @Schema(description = "医保契约 busNo contractBusNo")
    private String contractBusNo;
}
