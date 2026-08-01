/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回科室采购申请请求（IF-DEP-CPR-09）。
 */
@Getter
@Setter
public class RejectDeptPurchaseRequestRequest {

    /** 业务单号；必填。 */
    private String busNo;

    /** 驳回原因；必填（审计留痕）。 */
    private String rejectReason;
}
