/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 先进先出可用库存读模型。
 */
@Getter
@Setter
public class AvailableInventoryModel {

    /** 主键。 */
    private Long id;

    /** 批号号。 */
    private String lotNumber;

    /** expiration日期。 */
    private LocalDateTime expirationDate;

    /** 数量。 */
    private BigDecimal quantity;
}
