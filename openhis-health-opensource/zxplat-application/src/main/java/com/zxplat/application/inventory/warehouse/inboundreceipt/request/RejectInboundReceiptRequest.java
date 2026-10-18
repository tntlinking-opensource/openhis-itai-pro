/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回药库入库请求（IF-WHS-IN-09）。
 *
 * <p>驳回仅变更供应请求状态与原因，不触发放与库存。
 * {@link #rejectReason} 写入领域驳回项的变更原因字段。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回药库入库 IF-WHS-IN-09")
public class RejectInboundReceiptRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 驳回原因 rejectReason。 */
    @Schema(description = "驳回原因 rejectReason")
    private String rejectReason;
}
