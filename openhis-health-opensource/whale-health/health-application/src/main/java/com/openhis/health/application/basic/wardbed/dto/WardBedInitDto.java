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

package com.openhis.health.application.basic.wardbed.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 病区病房维护页初始化：形态、状态、机构与病区/病房候选下拉。
 */
@Data
public class WardBedInitDto {
    /** 病区、库房、床位等形态固定选项。 */
    private List<OptionDto> formEnums;
    /** 位置状态术语选项。 */
    private List<OptionDto> locationStatusOptions;
    /** 机构下拉。 */
    private List<OptionDto> organizationOptions;
    /** 已存在的病区位置候选。 */
    private List<OptionDto> wardOptions;
    /** 已存在的病房（库房形态）位置候选。 */
    private List<OptionDto> houseOptions;
}
