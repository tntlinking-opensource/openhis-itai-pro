/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批同意科室采购申请请求（IF-DEP-CPR-08）。
 */
@Getter
@Setter
public class AgreeDeptPurchaseRequestRequest {

    /** 业务单号；必填。 */
    private String busNo;
}
