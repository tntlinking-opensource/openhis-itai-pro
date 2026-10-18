/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>撤回审批成功响应（IF-WHS-PO-07）。</p>
 */
@Getter
@Setter
public class WithdrawPurchaseOrderApprovalResponse {

    /**
     * 是否撤回成功；成功时为 {@code true}。
     */
    private Boolean success;

    /**
     * 撤回后头行状态持久化码（{@link com.zxplat.domain.share.enums.SupplyStatus#getCode()}）。
     */
    private String statusEnum;
}
