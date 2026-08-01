/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交科室采购申请审批成功响应（IF-DEP-CPR-06）。
 */
@Getter
@Setter
public class SubmitDeptPurchaseRequestApprovalResponse {

    /** 是否提交成功。 */
    private Boolean success;

    /** 提交后头行状态持久化码。 */
    private String statusEnum;
}
