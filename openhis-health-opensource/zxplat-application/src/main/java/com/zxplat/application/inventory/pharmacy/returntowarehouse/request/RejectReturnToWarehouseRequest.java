/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回药房退库单请求。
 */
@Getter
@Setter
public class RejectReturnToWarehouseRequest {

    /** 药房退库业务单号。 */
    private String busNo;
    /** 驳回原因（必填）。 */
    private String rejectReason;
}
