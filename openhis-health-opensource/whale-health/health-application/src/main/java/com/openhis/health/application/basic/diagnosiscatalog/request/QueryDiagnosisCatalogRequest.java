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

package com.openhis.health.application.basic.diagnosiscatalog.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 疾病目录分页查询请求：支持来源分类与关键字（走领域 {@code DiseasePageQuery}），默认按创建时间排序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryDiagnosisCatalogRequest extends AbstractHealthPageRequest {

    /** 疾病来源/分类业务码；与初始化「疾病分类」下拉的 code 对齐。 */
    private String sourceEnum;

    {
        // 限定可参与 where 的属性名，避免客户端传入未白名单字段
        List<String> where = new ArrayList<>();
        where.add("name");
        where.add("conditionCode");
        where.add("pyStr");
        where.add("wbStr");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.CREATED_AT);
    }
}
