/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 同意药库出库单请求（IF-WHS-SO-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeStockOutRequest {

    /** 出库业务单号。 */
    @Schema(description = "出库业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 已废弃：审批时间由服务端写入。 */
    @Deprecated
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "已废弃：审批时间由服务端写入", deprecated = true)
    private LocalDateTime approvalTime;

    /** 已废弃：审批人取当前登录用户。 */
    @Deprecated
    @Schema(description = "已废弃：审批人取当前登录用户", deprecated = true)
    private Long approverId;

    /** 医保上报 medicareUploadEnabled。 */
    @Deprecated
    @Schema(description = "医保上报 medicareUploadEnabled")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
