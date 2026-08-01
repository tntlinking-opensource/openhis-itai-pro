/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.query;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 医嘱/目录开立分页入参（读主表；由应用层 {@code ProductDefinitionPageQuery} 转入）。
 *
 * <p>诊疗表 {@code pageForCatalog} 会排除已被套餐子表引用的子项目录行；
 * 组套/历史回填依赖 {@link #prepickedProductKeys} 豁免。</p>
 */
@Getter
@Setter
public class ProductDefinitionCatalogPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** product表名Name（ProductDefinitionCatalogPageQuery）。 */
    private String productTableName;
    /** product表名Names（ProductDefinitionCatalogPageQuery）。 */
    private List<String> productTableNames;
    /** 类别编码（ProductDefinitionCatalogPageQuery）。 */
    private String categoryCode;
    /** 排除药品 medication_category_code（开立西药医嘱排除中草药等）。 */
    private List<String> excludeMedicationCategoryCodes;

    /**
     * 排除诊疗/耗材等定义的 {@code category_code}（如门诊禁开嘱托传 {@code ["52"]}）。
     *
     * <p>仅作用于活动定义等使用本字段的 QueryService；药品侧仍用 {@link #excludeMedicationCategoryCodes}。</p>
     */
    private List<String> excludeCategoryCodes;

    private Boolean activeFlag;
    /** 供应商主键（ProductDefinitionCatalogPageQuery）。 */
    private Long supplierId;
    /** manufacturer（ProductDefinitionCatalogPageQuery）。 */
    private String manufacturer;
    /** 划价目录：{@code 1} 时排除药品表（与 RM 口径一致）。 */
    private Integer pricingFlag;
    private List<ProductDefinitionBatchLookup> prepickedProductKeys;
    /** sortField（ProductDefinitionCatalogPageQuery）。 */
    private String sortField;
    /** sortOrder（ProductDefinitionCatalogPageQuery）。 */
    private String sortOrder;
}
