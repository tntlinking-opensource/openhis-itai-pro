/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 驳回退库入库壳单请求（IF-WHS-RSI-09，系统壳单无独立审批链：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "驳回退库入库 IF-WHS-RSI-09（已拒绝）")
public class RejectReturnStockInRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 驳回原因 rejectReason。 */
    @Schema(description = "驳回原因 rejectReason")
    private String rejectReason;
}
