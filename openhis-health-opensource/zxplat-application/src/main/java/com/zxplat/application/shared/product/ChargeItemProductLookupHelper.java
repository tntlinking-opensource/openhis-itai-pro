/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * ChargeItem → 产品定义批量 lookup 锚点解析（含挂号费 {@code adm_healthcare_service} 回落）。
 */
public final class ChargeItemProductLookupHelper {

    /** 工具类禁止实例化。 */
    private ChargeItemProductLookupHelper() {
    }

    /**
     * 由收费明细行解析产品定义 lookup：优先 {@code product_table/product_id}，挂号锚点回落 {@code service_id}。
     *
     * @param chargeItem 收费明细
     * @return lookup；无法解析时 {@code null}
     */
    public static ProductDefinitionBatchLookup buildLookupFromChargeItem(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return null;
        }
        String productTable = chargeItem.getProductTable();
        Long productId = chargeItem.getProductId();
        if (StringUtils.isNotEmpty(productTable) && productId != null) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(productTable.trim());
            lookup.setProductDefinitionId(productId);
            return lookup;
        }
        String serviceTable = chargeItem.getServiceTable();
        Long serviceId = chargeItem.getServiceId();
        if (StringUtils.isEmpty(serviceTable) || serviceId == null) {
            return null;
        }
        String normalizedServiceTable = serviceTable.trim();
        if (!TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(normalizedServiceTable)) {
            return null;
        }
        // 挂号费未写 product_* 时，以号源类型（healthcare_service）作为产品展示锚点（BR-OP-FDT-006 挂号大类）
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        lookup.setProductDefinitionId(serviceId);
        return lookup;
    }
}
