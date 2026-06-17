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

package com.openhis.health.domain.administration.specimendefinition.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 样本定义分页查询入参。
 */
@Getter
@Setter
public class PageSpecimenDefinitionQuery {
    /** 关键字（名称/编码/拼音/五笔）。 */
    private String keyword;
    /** 发布状态过滤。 */
    private String statusEnum;
    /** 标本种类过滤。 */
    private String typeEnum;
}
