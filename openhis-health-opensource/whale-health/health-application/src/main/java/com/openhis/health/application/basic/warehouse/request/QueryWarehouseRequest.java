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

package com.openhis.health.application.basic.warehouse.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 库房查询/批量启停请求：支持单 id 与 id 列表；{@link #resolveIds()} 先追加 ids 再追加单 id（不去重）。
 */
@Data
public class QueryWarehouseRequest {
    /** 单条主键（详情、删除、编辑后回查）。 */
    private Long id;
    /** 批量主键（启停等）。 */
    private List<Long> ids;

    /**
     * 合并 {@link #ids} 与 {@link #id} 为单一列表（null 安全）。
     *
     * @return 可能含重复项的 id 列表；两者皆空则返回空列表
     */
    public List<Long> resolveIds() {
        List<Long> result = new ArrayList<>();
        if (ids != null) {
            result.addAll(ids);
        }
        if (id != null) {
            result.add(id);
        }
        return result;
    }
}
