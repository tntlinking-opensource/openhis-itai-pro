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

package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 定价类型枚举。
 *
 * <p>存储口径：字符串编码（与定价聚合保持一致）。</p>
 */
@Getter
public enum PriceType implements EnumInterface {
    SALE(0L, "SALE", "零售价", "", 1L),
    PURCHASE(0L, "PURCHASE", "采购进价", "", 2L),
    LIMIT(0L, "LIMIT", "限价", "", 3L),
    LOT_NUMBER_COST(0L, "LOT_NUMBER_COST", "批号进价", "", 4L),
    LOT_NUMBER_PRICE(0L, "LOT_NUMBER_PRICE", "批号售价", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PriceType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 处理String code逻辑。
     * @return 返回结果
     */
    public static boolean isLotNumberPriceType(String code) {
        if (code == null) {
            return false;
        }
        return LOT_NUMBER_COST.getCode().equals(code) || LOT_NUMBER_PRICE.getCode().equals(code);
    }

}
