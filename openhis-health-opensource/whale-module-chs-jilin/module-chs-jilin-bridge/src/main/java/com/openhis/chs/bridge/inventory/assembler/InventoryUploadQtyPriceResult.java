/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import java.math.BigDecimal;

/**
 * 库房进销存上报用的操作单位数量与单价（成对结果，避免 {@code BigDecimal[]} 下标语义丢失）。
 */
public final class InventoryUploadQtyPriceResult {

    /** 操作单位数量（已由拆零量换算）。 */
    private final BigDecimal operationQuantity;

    /** 操作单位单价（与数量同单位；金钱刻度）。 */
    private final BigDecimal operationUnitPrice;

    /**
     * @param operationQuantity  操作单位数量
     * @param operationUnitPrice 操作单位单价
     */
    public InventoryUploadQtyPriceResult(BigDecimal operationQuantity, BigDecimal operationUnitPrice) {
        this.operationQuantity = operationQuantity;
        this.operationUnitPrice = operationUnitPrice;
    }

    /**
     * @return 操作单位数量
     */
    public BigDecimal getOperationQuantity() {
        return operationQuantity;
    }

    /**
     * @return 操作单位单价
     */
    public BigDecimal getOperationUnitPrice() {
        return operationUnitPrice;
    }
}
