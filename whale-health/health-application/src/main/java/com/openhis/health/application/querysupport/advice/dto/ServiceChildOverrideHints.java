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

package com.openhis.health.application.querysupport.advice.dto;

import lombok.Getter;

/**
 * 诊疗草稿 {@code childOverridesJson} 中可结构化解析的常用键（执行次数、单位）。
 */
@Getter
public final class ServiceChildOverrideHints {

    /** 无覆盖键时的空实例。 */
    public static final ServiceChildOverrideHints EMPTY = new ServiceChildOverrideHints(null, null);

    /** 套餐子项执行次数覆盖（可空）。 */
    private final Integer executionCount;

    /** 计量单位 code 覆盖（可空）。 */
    private final String unitCode;

    /**
     * @param executionCount 执行次数覆盖
     * @param unitCode       单位 code 覆盖
     */
    public ServiceChildOverrideHints(Integer executionCount, String unitCode) {
        this.executionCount = executionCount;
        this.unitCode = unitCode;
    }
}
