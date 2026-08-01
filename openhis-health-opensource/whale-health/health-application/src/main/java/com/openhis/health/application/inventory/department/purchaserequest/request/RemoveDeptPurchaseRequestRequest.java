/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除科室采购申请单请求（IF-DEP-CPR-10）。
 */
@Getter
@Setter
public class RemoveDeptPurchaseRequestRequest {

    /** 业务单号；必填。 */
    private String busNo;
}
