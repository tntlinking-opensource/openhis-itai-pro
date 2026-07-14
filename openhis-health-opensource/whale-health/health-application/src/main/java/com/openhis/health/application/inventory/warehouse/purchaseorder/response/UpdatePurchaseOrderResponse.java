/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>修订订货单成功响应（IF-WHS-PO-05）：同号全量对齐后返回 {@code busNo} 与待审批状态码。</p>
 */
@Getter
@Setter
public class UpdatePurchaseOrderResponse {

    /**
     * 修订后订货单业务单号（与请求 {@code busNo} 一致）。
     */
    private String busNo;

    /**
     * 修订提交后展示用状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。
     */
    private String statusEnum;
}
