/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同意退库入库壳单请求（IF-WHS-RSI-08，库存已在下游退库 agree 落账：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "同意退库入库 IF-WHS-RSI-08（已拒绝）")
public class AgreeReturnStockInRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 审批人 approverId。 */
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批时间 approvalClock。 */
    @Schema(description = "审批时间 approvalClock")
    private LocalDateTime approvalClock;
}
