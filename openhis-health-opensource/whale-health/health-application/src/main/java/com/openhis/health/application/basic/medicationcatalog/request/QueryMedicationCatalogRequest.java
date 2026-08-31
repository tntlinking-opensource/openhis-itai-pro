/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
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

    /**
     * 重点药类型筛选（{@link com.openhis.health.domain.share.enums.KeyDrugType#getCode()}）；空/{@code null} 表示不限，非空时筛对应标识为 true 的药品。
     */
    private String keyDrugType;

    {
        List<String> where = new ArrayList<>();
        this.setWhere(where);
        this.getWhere().add("productName");
        this.getWhere().add("pyStr");
        this.getWhere().add("wbStr");
        this.getWhere().add("productBusNo");
        this.setSortBy(FieldNameConstants.BUS_NO);
    }
}
