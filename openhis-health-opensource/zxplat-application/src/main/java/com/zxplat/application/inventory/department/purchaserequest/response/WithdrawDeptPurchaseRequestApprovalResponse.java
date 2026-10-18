/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤回科室采购申请审批成功响应（IF-DEP-CPR-07）。
 */
@Getter
@Setter
public class WithdrawDeptPurchaseRequestApprovalResponse {

    /** 是否撤回成功。 */
    private Boolean success;

    /** 撤回后头行状态持久化码。 */
    private String statusEnum;
}
