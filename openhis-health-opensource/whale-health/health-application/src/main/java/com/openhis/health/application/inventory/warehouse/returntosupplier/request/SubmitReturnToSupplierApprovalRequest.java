/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 提交药库退供应商单审批请求（IF-WHS-RT-06）。
 */
@Getter
@Setter
@Schema(description = "提交退供应商审批 IF-WHS-RT-06")
public class SubmitReturnToSupplierApprovalRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 申请时间 applyClock。 */
    @Schema(description = "申请时间 applyClock")
    private LocalDateTime applyClock;
}
