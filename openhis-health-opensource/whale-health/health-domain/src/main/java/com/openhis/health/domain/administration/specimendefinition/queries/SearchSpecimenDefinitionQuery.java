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
 * 样本定义关键字检索入参。
 */
@Getter
@Setter
public class SearchSpecimenDefinitionQuery {
    /** 关键字。 */
    private String keyword;
    /** 发布状态；默认仅 ACTIVE。 */
    private String statusEnum;
    /** 标本种类过滤。 */
    private String typeEnum;
    /** 返回条数上限，默认 50。 */
    private Integer limit;
}
