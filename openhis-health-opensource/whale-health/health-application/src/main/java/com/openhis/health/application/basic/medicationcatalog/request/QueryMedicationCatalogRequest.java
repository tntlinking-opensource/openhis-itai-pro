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
package com.openhis.health.application.basic.medicationcatalog.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 药品目录分页查询请求（读模型 {@code product_definition}）。
 *
 * <p>实例初始化块：为兼容旧前端构造默认 {@code where} 检索列清单与默认排序字段；分页继承 {@link AbstractHealthPageRequest}（B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryMedicationCatalogRequest extends AbstractHealthPageRequest {

    /** 药品分类编码筛选（可空表示不限）。 */
    private String categoryCode;

    {
        List<String> where = new ArrayList<>();
        this.setWhere(where);
        this.getWhere().add("productName");
        this.getWhere().add("pyStr");
        this.getWhere().add("wbStr");
        this.getWhere().add("productBusNo");
        this.setSortBy(FieldNameConstants.CREATE_TIME);
    }
}
