/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 驳回药库退供应商单成功响应（IF-WHS-RT-09）。
 */
@Getter
@Setter
@Schema(description = "驳回退供应商单 IF-WHS-RT-09")
public class RejectReturnToSupplierResponse {
    /** 成功 success。 */
    @Schema(description = "成功 success")
    private Boolean success;
}
