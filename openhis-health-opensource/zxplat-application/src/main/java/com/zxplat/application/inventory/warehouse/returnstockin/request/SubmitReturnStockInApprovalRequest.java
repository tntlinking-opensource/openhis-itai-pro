/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 提交退库入库壳单审批请求（IF-WHS-RSI-06，壳单已终态：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "提交退库入库审批 IF-WHS-RSI-06（已拒绝）")
public class SubmitReturnStockInApprovalRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 申请时间 applyClock。 */
    @Schema(description = "申请时间 applyClock")
    private LocalDateTime applyClock;
}
