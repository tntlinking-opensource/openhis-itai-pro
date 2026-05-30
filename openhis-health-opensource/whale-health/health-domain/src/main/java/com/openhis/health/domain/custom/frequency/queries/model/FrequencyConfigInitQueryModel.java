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

package com.openhis.health.domain.custom.frequency.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 频次配置页初始化查询模型。
 */
@Getter
@Setter
public class FrequencyConfigInitQueryModel {
    /**
     * 周期类型候选值列表（用于前端初始化）。
     */
    private List<String> cycleTypes;
    /**
     * 周期单位候选值列表（用于前端初始化）。
     */
    private List<String> periodUnits;
    /**
     * 适用场景候选值列表（用于前端初始化）。
     */
    private List<String> applicableScenes;
    /**
     * 跨日选项候选值列表（用于前端初始化）。
     */
    private List<String> allowCrossDayOptions;
}
