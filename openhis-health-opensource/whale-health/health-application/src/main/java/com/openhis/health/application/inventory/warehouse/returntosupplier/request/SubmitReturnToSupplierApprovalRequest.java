/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class SubmitReturnToSupplierApprovalRequest {

    /** 业务单号。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
    /** 申请时间。 */
    @Schema(description = "申请时间 applyClock")
    private LocalDateTime applyClock;
}
