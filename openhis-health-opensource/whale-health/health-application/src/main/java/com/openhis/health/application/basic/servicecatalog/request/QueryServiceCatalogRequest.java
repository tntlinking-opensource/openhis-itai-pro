/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊疗目录分页查询请求：关键字匹配产品名称、助记码与业务编码；默认按 {@link FieldNameConstants#BUS_NO} 倒序由上层与领域协同解析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryServiceCatalogRequest extends AbstractHealthPageRequest {

    /** 诊疗大类/分类编码过滤；空表示不按分类过滤。 */
    private String categoryCode;

    /**
     * 发布状态筛选（{@code ACTIVE} 已启用 / {@code INACTIVE} 已停用）；空/{@code null} 表示不限。
     */
    private String statusEnum;

    /**
     * 是否已绑定套餐子项筛选（对齐 {@code children_flag}）。
     * <p>{@code true}=仅组套父项；{@code false}=仅未绑子项；{@code null}=不限。</p>
     */
    private Boolean childrenFlag;

    /**
     * 是否包含已被套餐引用的子诊疗行。
     *
     * <p>{@code null}：本 IF（诊疗目录）按维护语义默认含明细；
     * {@code false}：医嘱组套等临床选品隐藏明细；{@code true}：显式含明细。</p>
     */
    private Boolean includeReferencedPackageChildren;

    {
        // 声明允许参与 where 的读模型/投影字段名，与产品定义分页查询契约对齐
        List<String> where = new ArrayList<>();
        where.add("productName");
        where.add("pyStr");
        where.add("wbStr");
        where.add("productBusNo");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.BUS_NO);
    }
}
