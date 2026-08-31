/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.shared.advice.OrderChargePricingAssembler;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 团检套餐 ChargeItem 草稿装配（方案 A：锚定 {@code peis_group_contract}）。
 */
public final class PeisGroupPackageChargeDraftAssembler {

    private PeisGroupPackageChargeDraftAssembler() {
    }

    /**
     * 装配团检套餐费草稿命令项。
     *
     * @param encounterId           就诊主键
     * @param patientId             患者主键
     * @param accountId             账户主键
     * @param orgId                 机构主键
     * @param contractId            团检合同主键（service 锚点）
     * @param packageActivityId     套餐 AD 主键（product 锚点）
     * @param priceLine             生效定价行（须含 definitionId）
     * @param contractPriceApplied  是否合同议价覆盖目录价
     * @param catalogUnitPrice      目录单价（合同议价时写入改价说明）
     * @param product               套餐产品读模型
     * @return 命令项
     */
    public static CreateChargeItemCommand.Item toDraftItem(
            Long encounterId,
            Long patientId,
            Long accountId,
            Long orgId,
            Long contractId,
            Long packageActivityId,
            ChargeItemDefinition priceLine,
            boolean contractPriceApplied,
            BigDecimal catalogUnitPrice,
            ProductDefinitionDisplayDto product) {
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(patientId);
        item.setEncounterId(encounterId);
        item.setAccountId(accountId);
        OrderChargePricingAssembler.applyOrderChargePricing(
            item,
            product,
            priceLine,
            BigDecimal.ONE,
            resolveQuantityUnit(product));
        item.setOccurrenceTime(LocalDateTime.now());
        item.setServiceTable(TableNameConstants.PEIS_GROUP_CONTRACT);
        item.setServiceId(contractId);
        item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        item.setProductId(packageActivityId);
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        item.setGenerateSourceEnum(GenerateSource.PHYSICAL_EXAM_GROUP.getCode());
        item.setContextEnum(ChargeItemDmConstants.CONTEXT_ACTIVITY);
        if (contractPriceApplied) {
            item.setOverrideReasonCode(PhysicalExamChargeConstants.OVERRIDE_REASON_PEIS_GROUP_CONTRACT);
            item.setOverrideReasonText(buildContractOverrideText(contractId, catalogUnitPrice, priceLine.getPriceValue()));
            item.setManualAdjustedPrice(priceLine.getPriceValue());
        }
        return item;
    }

    private static String resolveQuantityUnit(ProductDefinitionDisplayDto product) {
        if (product != null && StringUtils.isNotEmpty(product.getPackageUnitCode())) {
            return product.getPackageUnitCode().trim();
        }
        if (product != null && StringUtils.isNotEmpty(product.getPurchaseUnitCode())) {
            return product.getPurchaseUnitCode().trim();
        }
        return ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT;
    }

    private static String buildContractOverrideText(
            Long contractId, BigDecimal catalogUnitPrice, BigDecimal contractUnitPrice) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("contractId=").append(contractId);
        if (catalogUnitPrice != null) {
            sb.append(",catalogPrice=").append(catalogUnitPrice);
        }
        if (contractUnitPrice != null) {
            sb.append(",contractPrice=").append(contractUnitPrice);
        }
        return sb.toString();
    }
}
