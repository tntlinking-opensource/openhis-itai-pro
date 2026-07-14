/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.support.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 提交锁预览行（IF-PRC-07 / BR-PRC-LOCK-008）。
 */
@Getter
@Setter
public class PriceAdjustmentLockPreviewRowDto {

    /** 库存项主键。 */
    private Long inventoryId;

    /** 物品表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 库位主键。 */
    private Long locationId;

    /** 批号。 */
    private String lotNumber;

    /** 账面数量。 */
    private BigDecimal quantity;

    /** 是否已被其它调价单锁定。 */
    private boolean lockedByOtherBusNo;

    /** 锁定方业务单号（本单已锁同时展示）。 */
    private String lockedBusNo;
}
