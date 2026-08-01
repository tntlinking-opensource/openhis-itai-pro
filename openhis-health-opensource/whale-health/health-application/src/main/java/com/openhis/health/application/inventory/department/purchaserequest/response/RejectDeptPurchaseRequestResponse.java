/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回科室采购申请成功响应（IF-DEP-CPR-09）。
 */
@Getter
@Setter
public class RejectDeptPurchaseRequestResponse {

    /** 是否驳回成功。 */
    private Boolean success;
}
