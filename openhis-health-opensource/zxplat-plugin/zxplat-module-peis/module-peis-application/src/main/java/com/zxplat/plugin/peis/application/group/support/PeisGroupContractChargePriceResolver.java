/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupContract;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 团检合同金额 → 套餐挂费单价解析。
 *
 * <p>优先使用 {@code peis_group_contract.contract_amount}；未配置时返回 {@code null} 由调用方回落目录价。</p>
 */
@Component
@RequiredArgsConstructor
public class PeisGroupContractChargePriceResolver {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 解析团检套餐应收单价（人次）。
     *
     * @param contractId        合同主键
     * @param packageActivityId 本批次/就诊套餐 AD
     * @param orgId             机构（目录价回落用）
     * @return 合同议价单价；无合同时返回 {@code null}
     */
    public BigDecimal resolvePackageUnitPrice(Long contractId, Long packageActivityId, Long orgId) {
        if (contractId == null) {
            return null;
        }
        PeisGroupContract contract = peisGroupCheckQueryService.requireContract(contractId);
        BigDecimal contractAmount = contract.getContractAmount();
        if (contractAmount == null || contractAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        contractAmount = MonetaryAmountSupport.scaleCalc(contractAmount);
        List<Long> packageIds = peisGroupCheckQueryService.listContractPackageIds(contractId);
        if (packageIds.isEmpty()) {
            return contractAmount;
        }
        if (packageIds.size() == 1) {
            return contractAmount;
        }
        Long targetPackageId = packageActivityId;
        if (targetPackageId == null || !packageIds.contains(targetPackageId)) {
            targetPackageId = packageIds.get(0);
        }
        Map<Long, BigDecimal> catalogByPackageId =
            chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, packageIds);
        BigDecimal catalogSum = BigDecimal.ZERO;
        for (Long packageId : packageIds) {
            BigDecimal catalogPrice = catalogByPackageId.get(packageId);
            if (catalogPrice == null || catalogPrice.compareTo(BigDecimal.ZERO) <= 0) {
                catalogSum = BigDecimal.ZERO;
                break;
            }
            catalogSum = catalogSum.add(MonetaryAmountSupport.scaleCalc(catalogPrice));
        }
        if (catalogSum.compareTo(BigDecimal.ZERO) <= 0) {
            return contractAmount.divide(
                BigDecimal.valueOf(packageIds.size()), 2, RoundingMode.HALF_UP);
        }
        BigDecimal targetCatalog = catalogByPackageId.get(targetPackageId);
        if (targetCatalog == null || targetCatalog.compareTo(BigDecimal.ZERO) <= 0) {
            return contractAmount.divide(
                BigDecimal.valueOf(packageIds.size()), 2, RoundingMode.HALF_UP);
        }
        return MonetaryAmountSupport.scaleCalc(
            contractAmount.multiply(targetCatalog).divide(catalogSum, 8, RoundingMode.HALF_UP));
    }

    /**
     * 将合同单价写入定价行（保留目录定义元数据，仅覆盖 priceValue）。
     *
     * @param contractUnitPrice 合同单价
     * @param catalogPriceLine  目录定价行（可空）
     * @param orgId             机构
     * @return 可用于挂费的定价行
     */
    public ChargeItemDefinition toChargePriceLine(
            BigDecimal contractUnitPrice, ChargeItemDefinition catalogPriceLine, Long orgId) {
        ChargeItemDefinition priceLine = catalogPriceLine == null ? new ChargeItemDefinition() : catalogPriceLine;
        if (priceLine.getOrgId() == null) {
            priceLine.setOrgId(orgId);
        }
        priceLine.setPriceValue(MonetaryAmountSupport.scaleCalc(contractUnitPrice));
        return priceLine;
    }

    /**
     * 目录价回落：套餐 SALE 价，缺失时子项 SALE 合计。
     */
    public ChargeItemDefinition resolveCatalogPackagePriceLine(Long packageActivityId, Long orgId) {
        if (packageActivityId == null) {
            return null;
        }
        Map<Long, BigDecimal> saleByPackageId =
            chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, List.of(packageActivityId));
        BigDecimal salePrice = saleByPackageId.get(packageActivityId);
        if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0) {
            ChargeItemDefinition priceLine = new ChargeItemDefinition();
            priceLine.setOrgId(orgId);
            priceLine.setPriceValue(MonetaryAmountSupport.scaleCalc(salePrice));
            return priceLine;
        }
        return resolvePackagePriceFromChildren(packageActivityId, orgId);
    }

    private ChargeItemDefinition resolvePackagePriceFromChildren(Long packageActivityId, Long orgId) {
        var children = activityDefinitionQueryService.listChildrenByActivityId(packageActivityId);
        if (children == null || children.isEmpty()) {
            return null;
        }
        List<Long> childIds = children.stream()
            .filter(child -> child != null && child.getChildrenId() != null)
            .map(child -> child.getChildrenId())
            .toList();
        if (childIds.isEmpty()) {
            return null;
        }
        Map<Long, BigDecimal> saleByChildId =
            chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, childIds);
        BigDecimal total = BigDecimal.ZERO;
        for (var child : children) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            BigDecimal unitPrice = saleByChildId.get(child.getChildrenId());
            if (unitPrice == null) {
                return null;
            }
            BigDecimal childNum = BigDecimal.ONE;
            if (child.getChildrenNum() != null && child.getChildrenNum() > 0) {
                childNum = BigDecimal.valueOf(child.getChildrenNum());
            }
            total = total.add(MonetaryAmountSupport.lineTotal(unitPrice, childNum));
        }
        ChargeItemDefinition synthetic = new ChargeItemDefinition();
        synthetic.setPriceValue(MonetaryAmountSupport.scaleCalc(total));
        synthetic.setOrgId(orgId);
        return synthetic;
    }
}
