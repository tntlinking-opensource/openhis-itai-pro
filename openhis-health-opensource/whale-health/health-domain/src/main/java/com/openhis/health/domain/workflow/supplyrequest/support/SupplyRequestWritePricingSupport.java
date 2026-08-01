/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.support;

import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptPurchaseRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStockOutCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateTransferCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应请求写侧行级金额落库门禁（**BR-MON-001** / **BR-CHG-008** / **BR-INV-DTL-007**）。
 *
 * <p>由 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService} 在持久化前调用，
 * 防止应用层绕过 {@code SupplyRequestPricedWriteOrchestrator} 直写前端价格。</p>
 *
 * <p><strong>订货 / 进货</strong>：接受协商单价，行额按拆零数量 + 包装价套 BR-INV-DTL-007；
 * <strong>其它单据</strong>：单价从批号成本/目录进价解析（每包装）后同公式重算行金额。</p>
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
     * 出库单：批号成本/目录进价权威单价。
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
     * 调拨/调出：同出库口径。
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
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
     * 请领单：目录进价权威单价。
     *
     * @param command 领域写命令
     */
    public void applyRequisitionCommand(CreateRequisitionCommand command) {
        if (command == null) {
            return;
        }
        for (CreateRequisitionCommand.CreateRequisitionItemCommand item : command.resolveItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal unit = resolveCatalogPurchaseUnitPrice(item.getItemTable(), item.getItemId());
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
     * 科室发药：批号成本/进价权威单价。
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
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
     * 科室退药：批号成本/进价权威单价。
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
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
     * 盘点：实盘与盈亏金额按权威单价重算。
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            applyAuthoritativeStocktakeLineAmounts(item, unit);
        }
    }

    /**
     * 退库单：批号成本/进价权威单价（含编排壳单复制行）。
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
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
     * 药库退供应商：优先沿用命令行已带的进货落库单价（BR-PRICE-SRC-001），缺省再解析批号/目录进价。
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
                // 应用层未从进货行回填单价时，回落批号/目录权威进价（与退库同构）
                unit = resolveLotOrCatalogUnitPrice(
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
     * 损益：同盘点口径。
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
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
            BigDecimal unit = resolveLotOrCatalogUnitPrice(
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
     * 批号成本优先，缺失则回落目录进价。
     *
     * @param itemTable 品项表
     * @param itemId    品项 id
     * @param lotNumber 批号
     * @return 单价；皆不可解析则 null
     */
    private BigDecimal resolveLotOrCatalogUnitPrice(String itemTable, Long itemId, String lotNumber) {
        if (StringUtils.isNotEmpty(itemTable) && itemId != null && StringUtils.isNotEmpty(lotNumber)) {
            BigDecimal lotCost = chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                itemTable.trim(), itemId, lotNumber.trim());
            if (lotCost != null) {
                return lotCost;
            }
        }
        return resolveCatalogPurchaseUnitPrice(itemTable, itemId);
    }

    /**
     * 目录首条进价（PurchasePrice）。
     *
     * @param itemTable 品项表
     * @param itemId    品项 id
     * @return 进价；参数不全则 null
     */
    private BigDecimal resolveCatalogPurchaseUnitPrice(String itemTable, Long itemId) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return null;
        }
        return chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(itemTable.trim(), itemId);
    }
}
