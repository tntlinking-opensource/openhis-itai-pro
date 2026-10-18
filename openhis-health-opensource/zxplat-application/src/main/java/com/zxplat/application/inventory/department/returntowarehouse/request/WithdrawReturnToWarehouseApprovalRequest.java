/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤回科室退库单审批请求。
 */
@Getter
@Setter
public class WithdrawReturnToWarehouseApprovalRequest {

    /** 业务单号。 */
    private String busNo;
}
