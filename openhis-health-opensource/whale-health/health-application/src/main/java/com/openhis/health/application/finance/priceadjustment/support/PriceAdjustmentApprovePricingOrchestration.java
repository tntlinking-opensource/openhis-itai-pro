/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.support;

import com.openhis.health.domain.administration.chargeitemdefinition.command.DeactivateActivePriceLinesCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDetailQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 调价审批同意定价落库：停用旧 {@code ACTIVE} 行并新增启用行（IF-PRC-13 / BR-PRC-APR-003～005）。
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentApprovePricingOrchestration {

    private final PriceAdjustmentQueryService priceAdjustmentQueryService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 按调价单号执行审批写价编排。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人（定价审计）
     */
    public void applyApprovedPricing(String busNo, Long operatorId) {
        // Pre：单号非法时不触发定价写侧。
        if (StringUtils.isEmpty(busNo)) {
            return;
        }
        // Pre：经 PriceAdjustment QS 读整单（本模块聚合）
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        if (document.getLines() == null) {
            return;
        }
        DeactivateActivePriceLinesCommand deactivateCmd = new DeactivateActivePriceLinesCommand();
        deactivateCmd.setOperatorId(operatorId);
        List<DeactivateActivePriceLinesCommand.Item> deactivateItems = new ArrayList<>();
        List<SavePriceLineCommand> addCommands = new ArrayList<>();
        for (PriceAdjustmentLineQueryModel line : document.getLines()) {
            if (line == null || StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
                continue;
            }
            String itemTable = line.getItemTable().trim();
            Long itemId = line.getItemId();
            // 业务分支：诊疗项目仅落目标售价（SALE）。
            if (PriceAdjustmentItemTableSupport.isClinicalItemTable(itemTable)) {
                this.collectClinicalPricing(line, itemTable, itemId, deactivateItems, addCommands, operatorId);
                continue;
            }
            // 业务分支：物流项目落项目级进/售价及批号级价类。
            if (PriceAdjustmentItemTableSupport.isLogisticsItemTable(itemTable)) {
                this.collectLogisticsPricing(line, itemTable, itemId, deactivateItems, addCommands, operatorId);
            }
        }
        deactivateCmd.setItems(deactivateItems);
        // Cmd：先批量停用匹配键下当前 ACTIVE 行（BR-PRC-APR-003）。
        if (!deactivateItems.isEmpty()) {
            this.chargeItemDefinitionDomainService.deactivateActivePriceLinesByCommand(deactivateCmd);
        }
        // Cmd：再按价类路由新增 ACTIVE 定价行（BR-PRC-APR-004～005）。
        for (SavePriceLineCommand addCmd : addCommands) {
            SavePriceLineCommand.Item item = addCmd.getItem();
            if (item == null) {
                continue;
            }
            String priceType = item.getPriceTypeCode();
            if (PriceType.PURCHASE.getCode().equals(priceType)) {
                this.chargeItemDefinitionDomainService.addItemPurchasePrice(addCmd);
            } else if (PriceType.SALE.getCode().equals(priceType)) {
                this.chargeItemDefinitionDomainService.addItemRetailPrice(addCmd);
            } else if (PriceType.LOT_NUMBER_COST.getCode().equals(priceType)) {
                this.chargeItemDefinitionDomainService.addLotPurchasePrice(addCmd);
            } else if (PriceType.LOT_NUMBER_PRICE.getCode().equals(priceType)) {
                this.chargeItemDefinitionDomainService.addLotSalePrice(addCmd);
            }
        }
    }

    /**
     * 诊疗调价行：有目标售价则停用旧 SALE 并收集新增价命令。
     *
     * @param line             调价明细读模型
     * @param itemTable        实例表
     * @param itemId           实例 id
     * @param deactivateItems  停用项收集器（mutate）
     * @param addCommands      新增价命令收集器（mutate）
     * @param operatorId       操作人
     */
    private void collectClinicalPricing(
        PriceAdjustmentLineQueryModel line,
        String itemTable,
        Long itemId,
        List<DeactivateActivePriceLinesCommand.Item> deactivateItems,
        List<SavePriceLineCommand> addCommands,
        Long operatorId
    ) {
        // Gate：诊疗行无目标售价则跳过写价。
        if (line.getNewSellingAmount() == null) {
            return;
        }
        deactivateItems.add(this.deactivateItem(itemTable, itemId, PriceType.SALE.getCode(), null));
        addCommands.add(this.addPriceCommand(
            itemTable,
            itemId,
            PriceType.SALE.getCode(),
            null,
            line.getNewSellingAmount(),
            this.requireTypeCodeForItem(itemTable, itemId),
            operatorId
        ));
    }

    /**
     * 物流调价行：项目级进/售价与批号级 LOT 价，分别收集停用与新增命令。
     *
     * @param line             调价明细读模型（含批号子表）
     * @param itemTable        实例表
     * @param itemId           实例 id
     * @param deactivateItems  停用项收集器（mutate）
     * @param addCommands      新增价命令收集器（mutate）
     * @param operatorId       操作人
     */
    private void collectLogisticsPricing(
        PriceAdjustmentLineQueryModel line,
        String itemTable,
        Long itemId,
        List<DeactivateActivePriceLinesCommand.Item> deactivateItems,
        List<SavePriceLineCommand> addCommands,
        Long operatorId
    ) {
        // 项目级进价：有目标进价则停用旧 PURCHASE 并新增。
        if (line.getNewPurchaseAmount() != null) {
            deactivateItems.add(this.deactivateItem(itemTable, itemId, PriceType.PURCHASE.getCode(), null));
            addCommands.add(this.addPriceCommand(
                itemTable,
                itemId,
                PriceType.PURCHASE.getCode(),
                null,
                line.getNewPurchaseAmount(),
                this.requireTypeCodeForItem(itemTable, itemId),
                operatorId
            ));
        }
        // 项目级售价：有目标售价则停用旧 SALE 并新增。
        if (line.getNewSellingAmount() != null) {
            deactivateItems.add(this.deactivateItem(itemTable, itemId, PriceType.SALE.getCode(), null));
            addCommands.add(this.addPriceCommand(
                itemTable,
                itemId,
                PriceType.SALE.getCode(),
                null,
                line.getNewSellingAmount(),
                this.requireTypeCodeForItem(itemTable, itemId),
                operatorId
            ));
        }
        if (line.getDetails() == null) {
            return;
        }
        // 批号级价：按子表批号展开 LOT_NUMBER_COST / LOT_NUMBER_PRICE。
        for (PriceAdjustmentDetailQueryModel detail : line.getDetails()) {
            if (detail == null || StringUtils.isEmpty(detail.getLotNumber())) {
                continue;
            }
            String lot = detail.getLotNumber().trim();
            if (line.getNewPurchaseAmount() != null) {
                deactivateItems.add(
                    this.deactivateItem(itemTable, itemId, PriceType.LOT_NUMBER_COST.getCode(), lot));
                addCommands.add(this.addPriceCommand(
                    itemTable,
                    itemId,
                    PriceType.LOT_NUMBER_COST.getCode(),
                    lot,
                    line.getNewPurchaseAmount(),
                    this.requireTypeCodeForItem(itemTable, itemId),
                    operatorId
                ));
            }
            if (line.getNewSellingAmount() != null) {
                deactivateItems.add(
                    this.deactivateItem(itemTable, itemId, PriceType.LOT_NUMBER_PRICE.getCode(), lot));
                addCommands.add(this.addPriceCommand(
                    itemTable,
                    itemId,
                    PriceType.LOT_NUMBER_PRICE.getCode(),
                    lot,
                    line.getNewSellingAmount(),
                    this.requireTypeCodeForItem(itemTable, itemId),
                    operatorId
                ));
            }
        }
    }

    private DeactivateActivePriceLinesCommand.Item deactivateItem(
        String itemTable,
        Long itemId,
        String priceTypeCode,
        String lotNo
    ) {
        DeactivateActivePriceLinesCommand.Item item = new DeactivateActivePriceLinesCommand.Item();
        item.setInstanceTable(itemTable);
        item.setInstanceId(itemId);
        item.setPriceTypeCode(priceTypeCode);
        item.setLotNo(lotNo);
        return item;
    }

    /**
     * 构造 {@link SavePriceLineCommand} 单条新增价项（状态 ACTIVE）。
     *
     * @param itemTable     实例表
     * @param itemId        实例 id
     * @param priceTypeCode 价类型 code
     * @param lotNo         批号；项目级为 null
     * @param priceValue    目标单价
     * @param typeCode      fin_type
     * @param operatorId    操作人
     * @return 可提交的 SavePriceLine 命令
     */
    private SavePriceLineCommand addPriceCommand(
        String itemTable,
        Long itemId,
        String priceTypeCode,
        String lotNo,
        BigDecimal priceValue,
        String typeCode,
        Long operatorId
    ) {
        SavePriceLineCommand cmd = new SavePriceLineCommand();
        cmd.setOperatorId(operatorId);
        SavePriceLineCommand.Item item = new SavePriceLineCommand.Item();
        item.setInstanceTable(itemTable);
        item.setInstanceId(itemId);
        item.setTypeCode(typeCode);
        item.setPriceTypeCode(priceTypeCode);
        item.setLotNo(lotNo);
        item.setPriceValue(priceValue);
        item.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 调价新增行前解析实例 fin_type（优先同实例历史定价行，停用前仍可读）。
     *
     * @param itemTable 项目表
     * @param itemId    项目主键
     * @return type_code
     * @throws BusinessException 无法解析 fin_type
     */
    private String requireTypeCodeForItem(String itemTable, Long itemId) {
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(itemTable);
        queryCommand.setInstanceId(itemId);
        // Pre：经 ChargeItemDefinition QS 读实例 fin_type（B.6.4）
        String typeCode = this.chargeItemDefinitionQueryService.findTypeCodeFromInstancePricing(queryCommand);
        if (StringUtils.isEmpty(typeCode)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "调价审批写定价缺少财务类型 type_code，itemTable=" + itemTable + ", itemId=" + itemId);
        }
        return typeCode.trim();
    }
}
