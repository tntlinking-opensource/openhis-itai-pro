/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pricing;

import com.openhis.health.domain.administration.chargeitemdefinition.command.DeactivateActivePriceLinesCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 实例级目录参考价（{@link PriceType#PURCHASE}/{@link PriceType#SALE}）与当前生效包装价对齐。
 *
 * <p>用途：进货/期初同意后，把本行（或院点加价后）的包装进/售回写为目录参考价，供选品默认与目录展示；
 * <strong>不是</strong>批号真价，亦<strong>不</strong>参与报表/已落库账单回放（见 BR-PRICE-SRC-001）。</p>
 * <p>写路径对齐调价审批：异值先停用旧 ACTIVE，再新增 ACTIVE；同值幂等跳过。</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogReferencePriceSyncSupport {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;

    /**
     * 将实例级采购/销售参考价分别对齐为给定包装单价（可只传一侧，另一侧传 {@code null} 跳过）。
     *
     * @param instanceTable         实例表（药品/耗材定义表）
     * @param instanceId            实例主键
     * @param typeCode              财务类型；空则拒绝写价
     * @param purchasePackagePrice  目标 {@code PURCHASE}；{@code null} 不写采购参考价
     * @param salePackagePrice      目标 {@code SALE}；{@code null} 不写销售参考价
     * @param operatorId            操作人
     * @throws BusinessException 缺 type_code 或写价失败时
     */
    public void syncPurchaseAndSaleIfChanged(
        String instanceTable,
        Long instanceId,
        String typeCode,
        BigDecimal purchasePackagePrice,
        BigDecimal salePackagePrice,
        Long operatorId
    ) {
        // Gate：主键或财务类型缺失时无法落参考价
        if (StringUtils.isEmpty(instanceTable) || instanceId == null) {
            return;
        }
        if (purchasePackagePrice == null && salePackagePrice == null) {
            return;
        }
        if (StringUtils.isEmpty(typeCode)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "目录参考价回写缺少财务类型 type_code，instanceTable="
                    + instanceTable
                    + ", instanceId="
                    + instanceId);
        }
        String table = instanceTable.trim();
        String finType = typeCode.trim();
        // Cmd：采购参考价与当前 ACTIVE 异值则停用旧行再新增
        if (purchasePackagePrice != null) {
            this.syncOnePriceTypeIfChanged(
                table,
                instanceId,
                finType,
                PriceType.PURCHASE.getCode(),
                purchasePackagePrice,
                operatorId);
        }
        // Cmd：销售参考价与当前 ACTIVE 异值则停用旧行再新增
        if (salePackagePrice != null) {
            this.syncOnePriceTypeIfChanged(
                table,
                instanceId,
                finType,
                PriceType.SALE.getCode(),
                salePackagePrice,
                operatorId);
        }
    }

    /**
     * 仅回写销售参考价（院点中草药批号售价加价后调用，避免采购参考价被售价抬高）。
     *
     * @param instanceTable    实例表
     * @param instanceId       实例主键
     * @param typeCode         财务类型
     * @param salePackagePrice 目标 {@code SALE}；空则跳过
     * @param operatorId       操作人
     */
    public void syncSaleIfChanged(
        String instanceTable,
        Long instanceId,
        String typeCode,
        BigDecimal salePackagePrice,
        Long operatorId
    ) {
        this.syncPurchaseAndSaleIfChanged(
            instanceTable, instanceId, typeCode, null, salePackagePrice, operatorId);
    }

    /**
     * 单价款类幂等回写：同值跳过；异值或尚无 ACTIVE 则停用（若有）后新增。
     *
     * @param instanceTable 实例表
     * @param instanceId    实例主键
     * @param typeCode      财务类型
     * @param priceTypeCode {@link PriceType#PURCHASE} 或 {@link PriceType#SALE}
     * @param targetPrice   目标包装单价
     * @param operatorId    操作人
     */
    private void syncOnePriceTypeIfChanged(
        String instanceTable,
        Long instanceId,
        String typeCode,
        String priceTypeCode,
        BigDecimal targetPrice,
        Long operatorId
    ) {
        BigDecimal current;
        // Pre：读当前 ACTIVE 参考价，决定是否需要写
        if (PriceType.PURCHASE.getCode().equals(priceTypeCode)) {
            current = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                instanceTable, instanceId);
        } else {
            current = this.chargeItemDefinitionQueryService.resolveFirstSalePriceValue(
                instanceTable, instanceId);
        }
        // 同值：幂等跳过，避免无意义停用/新增
        if (current != null && current.compareTo(targetPrice) == 0) {
            return;
        }
        // Cmd：有旧 ACTIVE 则先停用（无旧行时跳过停用，直接新增）
        if (current != null) {
            DeactivateActivePriceLinesCommand deactivate = new DeactivateActivePriceLinesCommand();
            deactivate.setOperatorId(operatorId);
            List<DeactivateActivePriceLinesCommand.Item> items = new ArrayList<>();
            DeactivateActivePriceLinesCommand.Item deact = new DeactivateActivePriceLinesCommand.Item();
            deact.setInstanceTable(instanceTable);
            deact.setInstanceId(instanceId);
            deact.setPriceTypeCode(priceTypeCode);
            deact.setLotNo(null);
            items.add(deact);
            deactivate.setItems(items);
            this.chargeItemDefinitionDomainService.deactivateActivePriceLinesByCommand(deactivate);
        }
        SavePriceLineCommand addCmd = new SavePriceLineCommand();
        addCmd.setOperatorId(operatorId);
        SavePriceLineCommand.Item addItem = new SavePriceLineCommand.Item();
        addItem.setInstanceTable(instanceTable);
        addItem.setInstanceId(instanceId);
        addItem.setTypeCode(typeCode);
        addItem.setPriceTypeCode(priceTypeCode);
        addItem.setLotNo(null);
        addItem.setPriceValue(targetPrice);
        addItem.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        addCmd.setItem(addItem);
        // Cmd：采购走 addItemPurchasePrice，销售走 addItemRetailPrice（实例级无批号）
        if (PriceType.PURCHASE.getCode().equals(priceTypeCode)) {
            this.chargeItemDefinitionDomainService.addItemPurchasePrice(addCmd);
        } else {
            this.chargeItemDefinitionDomainService.addItemRetailPrice(addCmd);
        }
    }
}
