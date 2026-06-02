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

package com.openhis.health.application.basic.warehouse.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 库房维护页初始化：状态字典、机构选项、仓库/药房形态固定选项。
 */
@Data
public class WarehouseInitDto {
    /** 位置状态下拉（术语枚举选项）。 */
    private List<OptionDto> locationStatusOptions;
    /** 机构树拍平后的下拉选项。 */
    private List<OptionDto> organizationOptions;
    /** 仓库与药房形态固定两项下拉。 */
    private List<OptionDto> formEnumOptions;
}
