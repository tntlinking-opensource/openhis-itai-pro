/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 按 busNo 回读药库退供应商单详情（IF-WHS-RT-03）。
 */
@Getter
@Setter
@Schema(description = "退供应商单详情 IF-WHS-RT-03")
public class GetReturnToSupplierDetailRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
