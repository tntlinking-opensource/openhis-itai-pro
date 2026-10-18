/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>提交审批成功响应（IF-WHS-PO-06）。</p>
 */
@Getter
@Setter
public class SubmitPurchaseOrderApprovalResponse {

    /**
     * 是否提交成功；成功时为 {@code true}。
     */
    private Boolean success;

    /**
     * 提交后头行状态持久化码（{@link com.zxplat.domain.share.enums.SupplyStatus#getCode()}）。
     */
    private String statusEnum;
}
