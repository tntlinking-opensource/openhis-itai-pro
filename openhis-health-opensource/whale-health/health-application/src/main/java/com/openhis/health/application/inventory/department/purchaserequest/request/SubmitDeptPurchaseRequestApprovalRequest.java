/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交科室采购申请审批请求（IF-DEP-CPR-06）。
 */
@Getter
@Setter
public class SubmitDeptPurchaseRequestApprovalRequest {

    /** 业务单号；必填。 */
    private String busNo;
}
