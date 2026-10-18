/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 驳回药库退供应商单请求（IF-WHS-RT-09）。
 */
@Getter
@Setter
@Schema(description = "驳回退供应商单 IF-WHS-RT-09")
public class RejectReturnToSupplierRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 驳回原因 rejectReason。 */
    @Schema(description = "驳回原因 rejectReason")
    private String rejectReason;
}
