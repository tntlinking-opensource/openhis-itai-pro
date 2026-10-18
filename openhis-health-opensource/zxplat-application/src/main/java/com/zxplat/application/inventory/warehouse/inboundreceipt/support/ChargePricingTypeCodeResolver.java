/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.support;

import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.FinTypeCodeMappingSupport;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 批号价/调价等写路径：解析实例维度 {@code type_code}（优先同实例历史定价行，回落目录分类）。
 */
public final class ChargePricingTypeCodeResolver {

    /**
     * 禁止实例化：仅提供静态解析方法。
     */
    private ChargePricingTypeCodeResolver() {
    }

    /**
     * 为批号维定价行解析 {@code type_code}。
     *
     * @param instanceTable 实例表名
     * @param instanceId    实例主键
     * @param product       产品读模型（药品回落 {@code categoryCode} 时可空）
     * @param queryService  定价读服务（读本聚合实例行）
     * @return 财务类型编码；无法解析时为 {@code null}
     */
    public static String resolveTypeCodeForLotPriceLine(
        String instanceTable,
        Long instanceId,
        ProductDefinitionDisplayDto product,
        ChargeItemDefinitionQueryService queryService) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null || queryService == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(instanceTable.trim());
        queryCommand.setInstanceId(instanceId);
        String fromSibling = queryService.findTypeCodeFromInstancePricing(queryCommand);
        if (StringUtils.isNotEmpty(fromSibling)) {
            return fromSibling.trim();
        }
        if (product == null) {
            return null;
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(instanceTable.trim())) {
            return FinTypeCodeMappingSupport.normalizeCategoryCodeToFinType(product.getCategoryCode());
        }
        return null;
    }
}
