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

package com.openhis.health.application.basic.frequency.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 频次分页查询请求：支持按名称、编码关键字检索，默认按创建时间倒序。
 *
 * <p>实例初始化块预置可筛选字段名列表与排序字段，供通用查询层解析（与 {@link AbstractPageQuery} 契约一致）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryFrequencyRequest extends AbstractHealthPageRequest {

    /** 按频次名称模糊匹配（与 rateCode、keyword 三选一优先级由应用服务归一）。 */
    private String name;

    /** 按频次编码模糊匹配。 */
    private String rateCode;

    {
        // 声明本请求允许参与 where 条件的属性名，避免客户端随意拼属性注入排序/过滤
        List<String> where = new ArrayList<>();
        where.add("name");
        where.add("rateCode");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.CREATED_AT);
    }
}
