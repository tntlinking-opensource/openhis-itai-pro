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

package com.openhis.health.module.querysupport.priceadjustment.dto;

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
