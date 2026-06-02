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

/* (C)2026 */
package com.openhis.health.application.basic.servicecatalog.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊疗目录分页查询请求：关键字匹配产品名称、助记码与业务编码；默认按 {@link FieldNameConstants#CREATE_TIME} 倒序由上层与领域协同解析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryServiceCatalogRequest extends AbstractHealthPageRequest {

    /** 诊疗大类/分类编码过滤；空表示不按分类过滤。 */
    private String categoryCode;

    {
        // 声明允许参与 where 的读模型/投影字段名，与产品定义分页查询契约对齐
        List<String> where = new ArrayList<>();
        where.add("productName");
        where.add("pyStr");
        where.add("wbStr");
        where.add("productBusNo");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.CREATE_TIME);
    }
}
