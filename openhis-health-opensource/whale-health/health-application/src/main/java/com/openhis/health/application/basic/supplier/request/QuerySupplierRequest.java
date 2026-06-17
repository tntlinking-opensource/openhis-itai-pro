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

package com.openhis.health.application.basic.supplier.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应商分页查询请求：默认模糊列与排序字段在实例初始化块中写入基类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "供应商查询指令DTO")
public class QuerySupplierRequest extends AbstractHealthPageRequest {

    /** 类型过滤（多选 code 或展示值，语义以前端约定为准）。 */
    private List<String> typeEnum;

    /** 默认 where 列为业务编号/名称/拼音/五笔，排序按创建时间。 */
    {
        List<String> where = new ArrayList<>();
        where.add("busNo");
        where.add("name");
        where.add("pyStr");
        where.add("wbStr");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.CREATED_AT);
    }
}
