/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 驳回药房退库单请求。
 */
@Getter
@Setter
public class RejectReturnToWarehouseRequest {

    /** 药房退库业务单号。 */
    private String busNo;
    /** 审批人主键；空时回落为当前操作人。 */
    private Long approverId;
    /** 审批时间；空时由领域层取服务器时钟。 */
    private LocalDateTime approvalClock;
    /** 驳回原因（必填）。 */
    private String rejectReason;
}
