/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;

/**
 * PEIS → HIS 产品目录/定价端口（读展示、解析草稿定价行、写入 Charge 金额）。
 */
public interface HisProductCatalogPort {

    /**
     * 批量加载产品展示读模型（复合键 → DTO）。
     *
     * @param lookups 批量查询键
     * @return 复合键到展示 DTO
     */
    Map<String, ProductDefinitionDisplayDto> loadProductDisplayAsMap(List<ProductDefinitionBatchLookup> lookups);

    /**
     * 解析费用草稿定价行（与医生站诊疗挂费同口径）。
     *
     * @param productTableName    产品表名
     * @param productDefinitionId 产品主键
     * @param productRow          产品读模型
     * @param organizationId      机构
     * @param encounterId         就诊
     * @return 定价行；无命中时为 null
     */
    ChargeItemDefinition resolveDraftChargePriceLine(
            String productTableName,
            Long productDefinitionId,
            ProductDefinitionDisplayDto productRow,
            Long organizationId,
            Long encounterId);

    /**
     * 将定价行与业务数量/单位写入 Charge 命令项金额字段（BR-ORD-016 / BR-CHG-008）。
     *
     * @param item             待写入命令项
     * @param productRow       产品读模型
     * @param priceLine        定价行
     * @param quantity         数量
     * @param quantityUnitCode 单位 code
     */
    void applyOrderChargePricing(
            CreateChargeItemCommand.Item item,
            ProductDefinitionDisplayDto productRow,
            ChargeItemDefinition priceLine,
            BigDecimal quantity,
            String quantityUnitCode);
}
