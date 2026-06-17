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

package com.openhis.health.application.basic.specimendefinition.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Data;

import java.util.List;

/**
 * 样本定义维护页初始化数据：标本种类与发布状态下拉选项。
 */
@Data
public class SpecimenDefinitionInitDto {

    /** 标本种类下拉，码值对齐 {@link com.openhis.health.domain.share.enums.SpecimenType}。 */
    private List<OptionDto> typeEnumOptions;

    /** 发布状态下拉，码值对齐 {@link com.openhis.health.domain.share.enums.PublicationStatus}。 */
    private List<OptionDto> statusEnumOptions;
}
