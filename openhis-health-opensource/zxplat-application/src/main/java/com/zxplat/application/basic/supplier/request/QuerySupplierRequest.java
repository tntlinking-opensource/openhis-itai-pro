/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.supplier.request;

import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
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

    /** 供应商查询指令DTO。 */
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
