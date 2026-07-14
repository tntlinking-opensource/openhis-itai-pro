/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 提交药房退库单审批请求。
 */
@Getter
@Setter
public class SubmitReturnToWarehouseApprovalRequest {

    /** 药房退库业务单号。 */
    private String busNo;
    /** 申请时间；空时由领域层取服务器时钟。 */
    private LocalDateTime applyClock;
}
