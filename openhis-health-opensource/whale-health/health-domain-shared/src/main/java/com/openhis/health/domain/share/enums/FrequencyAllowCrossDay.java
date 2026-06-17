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
 * 频次跨日选项（DM-CUS-008 §4.4）。
 */
public enum FrequencyAllowCrossDay implements EnumInterface {

    /** 执行时点必须在当日内。 */
    SAME_DAY(0L, "same-day", "不允许跨日", "执行时点必须在当日内", 1L),

    /** 执行时点可跨越自然日。 */
    CROSS_DAY(0L, "cross-day", "允许跨日", "执行时点可跨越自然日", 2L),

    /** 执行时点按护理班次划分。 */
    BY_SHIFT(0L, "by-shift", "按班次", "执行时点按护理班次划分", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    FrequencyAllowCrossDay(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder
    ) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
