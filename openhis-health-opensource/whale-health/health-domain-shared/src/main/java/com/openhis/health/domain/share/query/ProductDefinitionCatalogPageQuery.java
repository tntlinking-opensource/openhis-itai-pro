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
