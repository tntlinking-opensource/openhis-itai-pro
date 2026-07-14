/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除药库退供应商单成功响应（IF-WHS-RT-10）。
 */
@Getter
@Setter
@Schema(description = "删除退供应商单 IF-WHS-RT-10")
public class RemoveReturnToSupplierResponse {
    /** 成功 success。 */
    @Schema(description = "成功 success")
    private Boolean success;
}
