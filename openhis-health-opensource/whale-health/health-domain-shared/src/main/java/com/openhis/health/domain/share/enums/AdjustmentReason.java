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
/**
 * 调整原因枚举。
 *
 * <p>存储口径：字符串编码。</p>
 */
public enum AdjustmentReason implements EnumInterface {
    /**
     * MARKET_CHANGE。
     */
    MARKET_CHANGE(0L, "MARKET_CHANGE", "市场价格调整", "", 1L),
    /**
     * PROMOTION。
     */
    PROMOTION(0L, "PROMOTION", "促销活动", "", 2L),
    /**
     * COST_CHANGE。
     */
    COST_CHANGE(0L, "COST_CHANGE", "成本变动", "", 3L),
    /**
     * POLICY_CHANGE。
     */
    POLICY_CHANGE(0L, "POLICY_CHANGE", "政策调整", "", 4L),
    /**
     * MISTAKE。
     */
    MISTAKE(0L, "MISTAKE", "数据错误修正", "", 5L),
    /**
     * OTHER。
     */
    OTHER(0L, "OTHER", "其他原因", "", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * AdjustmentReason。
     */
    AdjustmentReason(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

}
