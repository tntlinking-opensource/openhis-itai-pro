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

    private String productTableName;
    private List<String> productTableNames;
    private String categoryCode;
    /** 排除药品 medication_category_code（开立西药医嘱排除中草药等）。 */
    private List<String> excludeMedicationCategoryCodes;
    private Boolean activeFlag;
    private Long supplierId;
    private String manufacturer;
    /** 划价目录：{@code 1} 时排除药品表（与 RM 口径一致）。 */
    private Integer pricingFlag;
    private List<ProductDefinitionBatchLookup> prepickedProductKeys;
    private String sortField;
    private String sortOrder;
}
