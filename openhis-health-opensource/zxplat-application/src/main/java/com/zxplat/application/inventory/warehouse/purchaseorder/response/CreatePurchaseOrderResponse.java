/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>新建订货单成功响应（IF-WHS-PO-04）：返回领域取号后的 {@code busNo} 与提交后展示用状态码。</p>
 */
@Getter
@Setter
public class CreatePurchaseOrderResponse {

    /**
     * 新建订货单业务单号（领域批量取号）。
     */
    private String busNo;

    /**
     * 提交后展示用状态持久化码（{@link com.zxplat.domain.share.enums.SupplyStatus#getCode()}）。
     */
    private String statusEnum;
}
