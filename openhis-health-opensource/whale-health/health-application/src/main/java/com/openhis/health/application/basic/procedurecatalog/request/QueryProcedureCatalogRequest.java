/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 手术目录分页查询请求：支持来源分类与关键字，默认按手术编码倒序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryProcedureCatalogRequest extends AbstractHealthPageRequest {

    /** 手术来源/分类业务码；与初始化「手术分类」下拉的 code 对齐。 */
    private String sourceEnum;

    {
        List<String> where = new ArrayList<>();
        where.add("name");
        where.add("procedureCode");
        where.add("pyStr");
        where.add("wbStr");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.PROCEDURE_CODE);
    }
}
