/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除药库退供应商单请求（IF-WHS-RT-10，仅草稿/可删态）。
 */
@Getter
@Setter
@Schema(description = "删除退供应商单 IF-WHS-RT-10")
public class RemoveReturnToSupplierRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
