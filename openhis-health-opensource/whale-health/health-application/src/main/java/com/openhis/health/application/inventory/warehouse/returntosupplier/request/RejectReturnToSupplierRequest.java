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
 * 驳回药库退供应商单请求（IF-WHS-RT-09）。
 */
@Getter
@Setter
@Schema(description = "驳回退供应商单 IF-WHS-RT-09")
public class RejectReturnToSupplierRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 审批人 approverId。 */
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批时间 approvalClock。 */
    @Schema(description = "审批时间 approvalClock")
    private LocalDateTime approvalClock;

    /** 驳回原因 rejectReason。 */
    @Schema(description = "驳回原因 rejectReason")
    private String rejectReason;
}
