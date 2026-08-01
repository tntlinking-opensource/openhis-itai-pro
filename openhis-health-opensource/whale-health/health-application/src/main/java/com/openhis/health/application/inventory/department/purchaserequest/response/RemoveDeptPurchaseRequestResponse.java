/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除科室采购申请单成功响应（IF-DEP-CPR-10）。
 */
@Getter
@Setter
public class RemoveDeptPurchaseRequestResponse {

    /** 是否删除成功。 */
    private Boolean success;
}
