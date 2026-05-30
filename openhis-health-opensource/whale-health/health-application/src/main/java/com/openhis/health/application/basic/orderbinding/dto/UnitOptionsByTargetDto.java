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

package com.openhis.health.application.basic.orderbinding.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 按绑定目标（诊疗或耗材）解析出的默认单位与可选单位下拉，用于保存绑定时单位字段联动。
 */
@Data
/**
 * UnitOptionsByTargetDto。
 */
public class UnitOptionsByTargetDto {

    /** 推荐默认选中的单位码（诊疗取销售单位；设备取多单位字段中的优先非空项）。 */
    /**
     * unitCode。
     */
    private String unitCode;

    /** 可选单位列表（来自术语解析或码值回退）。 */
    /**
     * unitOptions。
     */
    private List<OptionDto> unitOptions;
}
