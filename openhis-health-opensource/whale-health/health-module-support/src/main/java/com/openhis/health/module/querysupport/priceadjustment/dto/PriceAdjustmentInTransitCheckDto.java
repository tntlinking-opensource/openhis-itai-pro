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

/**
 * 物流调价在途预检结果（IF-PRC-08 / BR-PRC-SUB-003）。
 */
@Getter
@Setter
public class PriceAdjustmentInTransitCheckDto {

    /** 是否阻断提交。 */
    private boolean blocked;

    /** 原因码（{@link com.openhis.health.domain.share.constants.CustomConstants.PriceAdjustment}）。 */
    private String reasonCode;

    /** 用户可读说明。 */
    private String reasonMessage;

    /** 阻断项目表名。 */
    private String itemTable;

    /** 阻断项目主键。 */
    private Long itemId;
}
