/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.support;

import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.workflow.supplyrequest.command.CreateDeptIssueCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateDeptIssueReturnCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateDeptPurchaseRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateStockOutCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateTransferCommand;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应请求写侧行级金额落库门禁（**BR-MON-001** / **BR-CHG-008** / **BR-INV-DTL-007**）。
 *
 * <p>由 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService} 在持久化前调用，
 * 防止应用层绕过 {@code SupplyRequestPricedWriteOrchestrator} 直写前端价格。</p>
 *
 * <p><strong>订货 / 进货 / 科室采购申请</strong>：接受协商单价（进货选品可用目录 {@code PURCHASE} 作参考默认值），
 * 行额按拆零数量 + 包装价套 BR-INV-DTL-007。
 * <strong>请领 / 出库 / 调拨 / 退库 / 退供应商 / 盘点 / 损益 / 科室发退药</strong>：单价必须存在
 * {@code LOT_NUMBER_COST}（每包装），缺失直接拒绝落库；目录药典价禁止参与业务流转金额。</p>
 */
@Component
@RequiredArgsConstructor
public class SupplyRequestWritePricingSupport {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final SupplyRequestWarehouseLineAmountSupport warehouseLineAmountSupport;

    /**
     * 订货单：协商单价刻度与行总额（拆零数量 + 包装价）。
     *
     * @param command 领域写命令
     */
    public void applyPurchaseOrderCommand(CreatePurchaseOrderCommand command) {
        if (command == null) {
            return;
        }
        for (CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            applyNegotiatedPurchaseOrderLine(item);
        }
    }

    /**
     * 科室采购申请：与订货同构的协商/参考单价刻度与行总额。
     *
     * @param command 领域写命令
     */
    public void applyDeptPurchaseRequestCommand(CreateDeptPurchaseRequestCommand command) {
        if (command == null) {
            return;
        }
        for (CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            applyNegotiatedDeptPurchaseRequestLine(item);
        }
    }

    /**
     * 进货单：同订货口径。
     *
     * @param command 领域写命令
     */
    public void applyInboundReceiptCommand(CreateInboundReceiptCommand command) {
        if (command == null) {
            return;
        }
        for (CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            applyNegotiatedInboundLine(item);
        }
    }

    /**
     * 出库单：强制批号进价（{@code LOT_NUMBER_COST}）。
     *
     * @param command 领域写命令
     */
    public void applyStockOutCommand(CreateStockOutCommand command) {
        if (command == null) {
            return;
        }
        applyLotAnchoredStockOutItems(command.resolveItems());
    }

    /**
     * 调拨/调出：强制批号进价（同出库）。
     *
     * @param command 领域写命令
     */
    public void applyTransferCommand(CreateTransferCommand command) {
        if (command == null) {
            return;
        }
        for (CreateTransferCommand.CreateTransferItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeUnitAndLineTotal(
                item.getItemTable(),
                item.getItemId(),
                unit,
                item.getItemQuantity(),
                item::setPrice,
                item::setTotalPrice
            );
        }
    }

    /**
     * 请领单：强制批号进价（{@code LOT_NUMBER_COST}），禁止目录 {@code PURCHASE} 回落。
     *
     * <p>药房/科室请领均为批号级明细；无批号或无批号成本时直接抛错，避免参考价污染业务金额。
     * 写回使用显式 {@code setPrice}/{@code setTotalPrice}（不用 method-ref），并在目录价与批号价不一致时
     * 拒绝「落库单价等于目录价」的污染结果（防止旧 class / 错误回落静默落库）。</p>
     *
     * @param command 领域写命令
     * @throws BusinessException 批号空白、无 {@code LOT_NUMBER_COST}、或单价疑似目录价污染
     */
    public void applyRequisitionCommand(CreateRequisitionCommand command) {
        if (command == null) {
            return;
        }
        for (CreateRequisitionCommand.CreateRequisitionItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            // —— 请领金额只认批号成本：无 LOT_NUMBER_COST 拒绝落库（目录 PURCHASE 仅参考，不参与流转）。——
            BigDecimal lotCostUnit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(requireResolvableUnitPrice(lotCostUnit));
            BigDecimal lineTotal = this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
                item.getItemTable(),
                item.getItemId(),
                null,
                item.getItemQuantity(),
                scaledUnit
            );
            // —— 覆盖前端/目录参考价：显式写回，避免请求体单价残留。——
            item.setPrice(scaledUnit);
            item.setTotalPrice(lineTotal);
            if (item.getPrice() == null || item.getTotalPrice() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                    "请领行批号进价重算失败，禁止落库：批号=" + item.getLotNumber());
            }
            // —— 卫兵：批号价与目录价不一致时，落库单价不得等于目录价（拦截旧逻辑/错误回落）。——
            assertRequisitionPriceNotCatalogPollution(
                item.getItemTable(), item.getItemId(), item.getLotNumber(), lotCostUnit, item.getPrice());
        }
    }

    /**
     * 请领写侧卫兵：当目录 {@code PURCHASE} 与批号 {@code LOT_NUMBER_COST} 数值不同时，
     * 禁止落库单价等于目录价（典型故障：旧字节码仍走目录权威价）。
     *
     * @param itemTable   品项表
     * @param itemId      品项 id
     * @param lotNumber   批号（仅用于异常文案）
     * @param lotCostUnit 已解析的批号进价
     * @param savedPrice  即将落库的行单价
     * @throws BusinessException 疑似目录价污染
     */
    private void assertRequisitionPriceNotCatalogPollution(
        String itemTable,
        Long itemId,
        String lotNumber,
        BigDecimal lotCostUnit,
        BigDecimal savedPrice
    ) {
        BigDecimal catalogPurchase = chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
            itemTable, itemId);
        if (catalogPurchase == null || savedPrice == null || lotCostUnit == null) {
            return;
        }
        BigDecimal scaledCatalog = MonetaryAmountSupport.scaleCalc(catalogPurchase);
        BigDecimal scaledLot = MonetaryAmountSupport.scaleCalc(lotCostUnit);
        if (scaledLot.compareTo(scaledCatalog) == 0) {
            return;
        }
        if (savedPrice.compareTo(scaledCatalog) == 0) {
            throw new BusinessException(
                ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                "请领行单价疑似目录价污染，禁止落库：批号="
                    + (lotNumber == null ? "" : lotNumber.trim())
                    + "，批号进价="
                    + scaledLot
                    + "，目录价="
                    + scaledCatalog);
        }
    }

    /**
     * 科室发药：强制批号进价。
     *
     * @param command 领域写命令
     */
    public void applyDeptIssueCommand(CreateDeptIssueCommand command) {
        if (command == null) {
            return;
        }
        for (CreateDeptIssueCommand.CreateDeptIssueItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeUnitAndLineTotal(
                item.getItemTable(),
                item.getItemId(),
                unit,
                item.getItemQuantity(),
                item::setPrice,
                item::setTotalPrice
            );
        }
    }

    /**
     * 科室退药：强制批号进价。
     *
     * @param command 领域写命令
     */
    public void applyDeptIssueReturnCommand(CreateDeptIssueReturnCommand command) {
        if (command == null) {
            return;
        }
        for (CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeUnitAndLineTotal(
                item.getItemTable(),
                item.getItemId(),
                unit,
                item.getItemQuantity(),
                item::setPrice,
                item::setTotalPrice
            );
        }
    }

    /**
     * 盘点：实盘与盈亏金额按批号进价重算。
     *
     * @param command 领域写命令
     */
    public void applyStocktakeCommand(CreateStocktakeCommand command) {
        if (command == null) {
            return;
        }
        for (CreateStocktakeCommand.CreateStocktakeItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeStocktakeLineAmounts(item, unit);
        }
    }

    /**
     * 退库单：强制批号进价（含编排壳单复制行）。
     *
     * @param command 领域写命令
     */
    public void applyReturnCommand(CreateReturnCommand command) {
        if (command == null) {
            return;
        }
        for (CreateReturnCommand.CreateReturnItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeUnitAndLineTotal(
                item.getItemTable(),
                item.getItemId(),
                unit,
                item.getItemQuantity(),
                item::setPrice,
                item::setTotalPrice
            );
        }
    }

    /**
     * 药库退供应商：优先沿用命令行已带的进货落库单价；缺省强制批号进价（禁止目录药典价）。
     *
     * @param command 领域写命令
     */
    public void applyReturnToSupplierCommand(CreateReturnToSupplierCommand command) {
        if (command == null) {
            return;
        }
        for (CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = item.getPrice();
            if (unit == null) {
                // 应用层未从进货行回填单价时，强制批号进价（禁止目录 PURCHASE）
                unit = requireLotNumberCostUnitPrice(
                    item.getItemTable(), item.getItemId(), item.getLotNumber());
            }
            applyAuthoritativeUnitAndLineTotal(
                item.getItemTable(),
                item.getItemId(),
                unit,
                item.getItemQuantity(),
                item::setPrice,
                item::setTotalPrice
            );
        }
    }

    /**
     * 损益：同盘点口径（强制批号进价）。
     *
     * @param command 领域写命令
     */
    public void applyGainLossCommand(CreateGainLossCommand command) {
        if (command == null) {
            return;
        }
        for (CreateGainLossCommand.CreateGainLossItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeStocktakeLineAmounts(item, unit);
        }
    }

    /**
     * 进货行：协商单价刻度 + 拆零行额（BR-INV-DTL-007）。
     *
     * @param item 进货明细命令行
     */
    private void applyNegotiatedInboundLine(CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item) {
        BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(item.getPrice());
        item.setPrice(scaledUnit);
        item.setTotalPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            item.getUnitCode(),
            item.getItemQuantity(),
            scaledUnit
        ));
    }

    /**
     * 订货行：协商单价刻度 + 拆零行额。
     *
     * @param item 订货明细命令行
     */
    private void applyNegotiatedPurchaseOrderLine(CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item) {
        BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(item.getPrice());
        item.setPrice(scaledUnit);
        item.setTotalPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            item.getUnitCode(),
            item.getItemQuantity(),
            scaledUnit
        ));
    }

    /**
     * 科室采购申请行：协商/参考单价刻度后重算行总额（拆零数量 + 包装价）。
     *
     * @param item 申请命令行
     */
    private void applyNegotiatedDeptPurchaseRequestLine(
        CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item) {

        BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(item.getPrice());
        item.setPrice(scaledUnit);
        item.setTotalPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            item.getUnitCode(),
            item.getItemQuantity(),
            scaledUnit
        ));
    }

    /**
     * 出库明细批量：批号/目录权威单价后写入 price 与 totalPrice。
     *
     * @param items 出库明细命令行
     */
    private void applyLotAnchoredStockOutItems(List<CreateStockOutCommand.CreateStockOutItemCommand> items) {
        if (items == null) {
            return;
        }
        for (CreateStockOutCommand.CreateStockOutItemCommand item : items) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = requireLotNumberCostUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeUnitAndLineTotal(
                item.getItemTable(),
                item.getItemId(),
                unit,
                item.getItemQuantity(),
                item::setPrice,
                item::setTotalPrice
            );
        }
    }

    /**
     * 权威包装单价 + 拆零数量 → 行 price/totalPrice。
     *
     * @param itemTable     物品表
     * @param itemId        物品主键
     * @param unitPrice     每包装权威单价
     * @param splitQuantity 拆零数量
     * @param priceSetter   写回 price
     * @param totalSetter   写回 totalPrice
     */
    private void applyAuthoritativeUnitAndLineTotal(
        String itemTable,
        Long itemId,
        BigDecimal unitPrice,
        BigDecimal splitQuantity,
        java.util.function.Consumer<BigDecimal> priceSetter,
        java.util.function.Consumer<BigDecimal> totalSetter
    ) {
        BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(requireResolvableUnitPrice(unitPrice));
        priceSetter.accept(scaledUnit);
        totalSetter.accept(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            itemTable,
            itemId,
            null,
            splitQuantity,
            scaledUnit
        ));
    }

    /**
     * 盘点/损益：实盘额与盈亏额均按拆零数量套 BR-INV-DTL-007。
     *
     * @param item      盘点命令行
     * @param unitPrice 每包装权威单价
     */
    private void applyAuthoritativeStocktakeLineAmounts(
        CreateStocktakeCommand.CreateStocktakeItemCommand item,
        BigDecimal unitPrice
    ) {
        BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(requireResolvableUnitPrice(unitPrice));
        item.setPrice(scaledUnit);
        item.setTotalPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            null,
            item.getTotalQuantity(),
            scaledUnit
        ));
        item.setProfitLossPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            null,
            item.getItemQuantity(),
            scaledUnit
        ));
    }

    /**
     * 损益行：同盘点拆零行额口径。
     *
     * @param item      损益命令行
     * @param unitPrice 每包装权威单价
     */
    private void applyAuthoritativeStocktakeLineAmounts(
        CreateGainLossCommand.CreateGainLossItemCommand item,
        BigDecimal unitPrice
    ) {
        BigDecimal scaledUnit = MonetaryAmountSupport.scaleCalc(requireResolvableUnitPrice(unitPrice));
        item.setPrice(scaledUnit);
        item.setTotalPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            null,
            item.getTotalQuantity(),
            scaledUnit
        ));
        item.setProfitLossPrice(this.warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
            item.getItemTable(),
            item.getItemId(),
            null,
            item.getItemQuantity(),
            scaledUnit
        ));
    }

    /**
     * 行单价非空门禁（禁止前端价直落库）。
     *
     * @param unitPrice 解析结果
     * @return 非 null 单价
     * @throws BusinessException 无法解析
     */
    private BigDecimal requireResolvableUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "无法解析行单价，禁止落库");
        }
        return unitPrice;
    }

    /**
     * 库房流转写侧：解析批号成本；批号空白或无 {@code LOT_NUMBER_COST} 时抛错（禁止目录药典价）。
     *
     * @param itemTable 品项表
     * @param itemId    品项 id
     * @param lotNumber 批号
     * @return 非空批号进价（每包装）
     * @throws BusinessException 无法解析批号成本
     */
    private BigDecimal requireLotNumberCostUnitPrice(String itemTable, Long itemId, String lotNumber) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "明细缺少品项，无法解析批号进价");
        }
        if (StringUtils.isEmpty(lotNumber)) {
            throw new BusinessException(
                ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "明细缺少批号，无法解析批号进价");
        }
        BigDecimal lotCost = chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
            itemTable.trim(), itemId, lotNumber.trim());
        if (lotCost == null) {
            throw new BusinessException(
                ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
                "未找到批号进价（LOT_NUMBER_COST），禁止落库：批号=" + lotNumber.trim());
        }
        return lotCost;
    }
}
