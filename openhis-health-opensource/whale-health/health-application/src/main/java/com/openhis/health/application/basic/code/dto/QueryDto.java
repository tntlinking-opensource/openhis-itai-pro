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

package com.openhis.health.application.basic.code.dto;

import lombok.Data;

import java.util.List;

/**
 * 通用查询条件片段：where 字段名列表 + 关键字 + 排序字段。
 */
@Data
public class QueryDto {

    /** 参与检索的字段名列表（语义由调用方解释）。 */
    private List<String> where;
    /** 模糊检索关键字。 */
    private String keyword;
    /** 排序属性名。 */
    private String sortBy;
}
