/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 产品定义分页宽入参（应用层 / IF → Loader 桥接，兼容原 RM 分页字段形状）。
 *
 * <p>由 Mapper 从 HTTP Request 装配；{@link ProductDefinitionCatalogPageLoader} /
 * {@link ProductDefinitionPurchasePageLoader} 内转为
 * {@link com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery} 或
 * {@link com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery} 后调用领域
 * {@code *DefinitionQueryService}。领域层<strong>不</strong>直接依赖本类。</p>
 *
 * <p>{@code productTableNames} 非空时优先于单值 {@code productTableName}；
 * {@code pricingFlag = 1} 时排除药品表行（划价目录口径）；
 * {@code prepickedProductKeys} 与关键字组合为 OR。</p>
 */
@Getter
@Setter
public class ProductDefinitionPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 产品来源表名（单表；与 {@code productTableNames} 二选一，列表优先）。 */
    private String productTableName;

    /**
     * 来源表名多选（医嘱开立「全部」等场景）；元素为表名字面量。
     * 非空且含有效元素时，生成 {@code product_table_name IN (...)}，并忽略单值 {@code productTableName}。
     */
    private List<String> productTableNames;

    /** 分类编码。 */
    private String categoryCode;

    /** 排除药品目录 medication_category_code（如开立西药医嘱传 {@code ["4"]} 排除中草药）；仅药品检索。 */
    private List<String> excludeMedicationCategoryCodes;

    /**
     * 排除活动定义 {@code category_code}（如门诊 IF-DOR-11 排除嘱托 {@code ["52"]}，BR-ORD-032）。
     */
    private List<String> excludeCategoryCodes;

    /** 启用标志。 */
    private Boolean activeFlag;

    /** 供应商标识。 */
    private Long supplierId;

    /** 厂家名称。 */
    private String manufacturer;

    /**
     * 划价标记（Integer 与 AS 请求一致）。{@code 1}：不返回 {@code med_medication_definition} 行。
     */
    private Integer pricingFlag;

    /** 预选产品键（表名 + 定义主键），与关键字检索 OR 组合。 */
    private List<ProductDefinitionBatchLookup> prepickedProductKeys;

    /** 是否关联库存。 */
    private Boolean includeInventory;

    /** 排序字段。 */
    private String sortField;

    /** 排序方向。 */
    private String sortOrder;
}
