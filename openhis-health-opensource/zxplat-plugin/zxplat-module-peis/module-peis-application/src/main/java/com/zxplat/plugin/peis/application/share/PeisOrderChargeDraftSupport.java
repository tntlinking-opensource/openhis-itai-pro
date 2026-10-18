/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.share;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.share.constants.ChargeItemDmConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.plugin.peis.domain.port.HisProductCatalogPort;

/**
 * PEIS 费用草稿命令项装配（D1/Port：不依赖 HIS {@code OrderChargeDraftAssembler}）。
 */
public final class PeisOrderChargeDraftSupport {

    /**
     * 禁止实例化。
     */
    private PeisOrderChargeDraftSupport() {
    }

    /**
     * 装配单条费用草稿命令项；金额经 {@link HisProductCatalogPort#applyOrderChargePricing} 写入。
     *
     * @param catalogPort            产品目录/定价端口
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param accountId              账户主键
     * @param orgId                  机构主键
     * @param serviceId              上游锚点主键
     * @param serviceTable           上游表名
     * @param productDefinitionTable 产品定义表名
     * @param productDefinitionId    产品定义主键
     * @param priceLine              定价行
     * @param quantity               计价数量
     * @param quantityUnit           计价单位
     * @param productRow             产品读模型
     * @return 命令项
     */
    public static CreateChargeItemCommand.Item toCreateChargeItemDraftItemFromOrder(
            HisProductCatalogPort catalogPort,
            Long encounterId,
            Long patientId,
            Long accountId,
            Long orgId,
            Long serviceId,
            String serviceTable,
            String productDefinitionTable,
            Long productDefinitionId,
            ChargeItemDefinition priceLine,
            BigDecimal quantity,
            String quantityUnit,
            ProductDefinitionDisplayDto productRow) {
        CreateChargeItemCommand.Item it = new CreateChargeItemCommand.Item();
        it.setPatientId(patientId);
        it.setEncounterId(encounterId);
        it.setAccountId(accountId);
        catalogPort.applyOrderChargePricing(it, productRow, priceLine, quantity, quantityUnit);
        it.setOccurrenceTime(LocalDateTime.now());
        it.setServiceTable(serviceTable);
        it.setServiceId(serviceId);
        applyProductCatalogAnchor(it, productDefinitionTable, productDefinitionId);
        it.setOrgId(orgId);
        it.setRequestingOrgId(orgId);
        it.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        it.setContextEnum(resolveChargeContext(serviceTable));
        return it;
    }

    /**
     * 写草稿 charge_item：合法产品表时回填 productTable/productId 锚点。
     *
     * @param item                   草稿行
     * @param productDefinitionTable 产品表名
     * @param productDefinitionId    产品 id
     */
    private static void applyProductCatalogAnchor(
            CreateChargeItemCommand.Item item,
            String productDefinitionTable,
            Long productDefinitionId) {
        if (item == null || productDefinitionId == null || StringUtils.isEmpty(productDefinitionTable)) {
            return;
        }
        String normalizedTable = productDefinitionTable.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(normalizedTable)
                && !TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(normalizedTable)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(normalizedTable)) {
            return;
        }
        item.setProductTable(normalizedTable);
        item.setProductId(productDefinitionId);
    }

    /**
     * 按 service_table 判定 charge context。
     *
     * @param serviceTable 请求或发放表名
     * @return context 码
     */
    private static String resolveChargeContext(String serviceTable) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
                || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_MEDICATION;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)
                || TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_DEVICE;
        }
        return ChargeItemDmConstants.CONTEXT_ACTIVITY;
    }
}
