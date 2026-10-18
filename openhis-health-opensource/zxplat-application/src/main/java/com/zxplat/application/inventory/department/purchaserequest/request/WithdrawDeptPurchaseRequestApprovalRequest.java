/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤回科室采购申请审批请求（IF-DEP-CPR-07）。
 */
@Getter
@Setter
public class WithdrawDeptPurchaseRequestApprovalRequest {

    /** 业务单号；必填。 */
    private String busNo;
}
