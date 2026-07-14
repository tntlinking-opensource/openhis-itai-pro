/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.support.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 物流调价批号快照行（IF-PRC-05，与草稿 {@code details} 同构）。
 */
@Getter
@Setter
public class LogisticsDetailSnapshotRowDto {

    /** 物品定义主键。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 聚合数量。 */
    private BigDecimal quantity;

    /** 调前进价。 */
    private BigDecimal oldPurchaseAmount;

    /** 调前售价。 */
    private BigDecimal oldSellingAmount;
}
