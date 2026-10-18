/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.valueobject;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跨批次先进先出分配结果（单批次明细）。
 */
@Getter
@Setter
public class BatchAllocationResult {

    /** 库存项标识。 */
    private Long inventoryItemId;

    /** 产品批号。 */
    private String lotNumber;

    /** 本批次分配数量。 */
    private BigDecimal allocatedQuantity;

    /** 本批次分配后剩余数量。 */
    private BigDecimal remainingQuantity;

    /** 库房标识。 */
    private Long locationId;

    /** 货位标识。 */
    private Long locationStoreId;

    /** 效期。 */
    private LocalDateTime expirationDate;
}
