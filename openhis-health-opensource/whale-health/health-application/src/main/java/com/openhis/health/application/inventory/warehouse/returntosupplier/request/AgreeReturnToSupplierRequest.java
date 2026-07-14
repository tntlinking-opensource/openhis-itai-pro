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
 * 药库退供应商单同意请求（IF-WHS-RT-08：触发库存扣减与追溯出库）。
 */
@Schema(description = "同意药库退供应商单 IF-WHS-RT-08")
@Getter
@Setter
public class AgreeReturnToSupplierRequest {
    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 审批人 approverId。 */
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批时间 approvalClock。 */
    @Schema(description = "审批时间 approvalClock")
    private LocalDateTime approvalClock;

    /**
     * 是否上报医保进销存。
     *
     * @deprecated CHS 模块启用即上传，此开关不再生效
     */
    @Deprecated
    @Schema(description = "医保上报 medicareUploadEnabled")
    /** 医保上报 medicareUploadEnabled。 */
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo contractBusNo。 */
    @Schema(description = "医保契约 busNo contractBusNo")
    private String contractBusNo;
}
