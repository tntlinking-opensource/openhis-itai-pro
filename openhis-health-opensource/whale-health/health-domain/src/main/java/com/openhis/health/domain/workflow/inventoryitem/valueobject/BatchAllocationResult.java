/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.inventoryitem.valueobject;

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

