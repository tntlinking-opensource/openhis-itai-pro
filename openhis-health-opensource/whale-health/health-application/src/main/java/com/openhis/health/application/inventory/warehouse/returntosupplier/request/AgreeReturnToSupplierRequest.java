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
public class AgreeReturnToSupplierRequest {

    /** 业务单号。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
    /** 审批人医务人员主键。 */
    @Schema(description = "审批人 approverId")
    private Long approverId;
    /** 审批时间。 */
    @Schema(description = "审批时间 approvalClock")
    private LocalDateTime approvalClock;
    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    /** 是否上报医保进销存。 */
    @Schema(description = "医保上报 medicareUploadEnabled")
    private Boolean medicareUploadEnabled;
    /** 医保契约 busNo；上传进销存时使用。 */
    private String contractBusNo;
}
