/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.support;

import com.openhis.health.application.finance.priceadjustment.request.ApprovePriceAdjustmentRequest;
import com.openhis.health.application.finance.priceadjustment.service.PriceAdjustmentAppService;
import com.openhis.health.application.inventory.department.gainloss.request.AgreeGainLossRequest;
import com.openhis.health.application.inventory.department.gainloss.service.InventoryDepartmentGainLossAppService;
import com.openhis.health.application.inventory.department.stocktake.request.AgreeStocktakeRequest;
import com.openhis.health.application.inventory.department.stocktake.service.InventoryDepartmentStocktakeAppService;
import com.openhis.health.application.inventory.pharmacy.gainloss.request.AgreePharmacyGainLossRequest;
import com.openhis.health.application.inventory.pharmacy.gainloss.service.InventoryPharmacyGainLossAppService;
import com.openhis.health.application.inventory.pharmacy.stocktake.request.AgreePharmacyStocktakeRequest;
import com.openhis.health.application.inventory.pharmacy.stocktake.service.InventoryPharmacyStocktakeAppService;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.AgreeInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService;
import com.openhis.health.application.inventory.warehouse.returntosupplier.request.AgreeReturnToSupplierRequest;
import com.openhis.health.application.inventory.warehouse.returntosupplier.service.InventoryWarehouseReturnToSupplierAppService;
import com.openhis.health.application.inventory.warehouse.gainloss.service.InventoryWarehouseGainLossAppService;
import com.openhis.health.application.inventory.warehouse.stocktake.service.InventoryWarehouseStocktakeAppService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FinanceApprovalConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 财务审批同意分发编排（IF-APR-03 / AS-FIN-APR-001-SDD §3.4）。
 *
 * <p>按 documentKind/type <strong>互斥</strong>复用各业务 AppService 已落地同意路径（进货/退货/损益/盘点/调价），
 * 禁止在本包复制发放、库存、落价等动账规则。</p>
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalAgreeOrchestration {

    /** 药库进货同意（IF-WHS-IN-08）。 */
    private final InventoryWarehouseInboundReceiptAppService inboundReceiptAppService;

    /** 药库退供应商同意（IF-WHS-RT-08）。 */
    private final InventoryWarehouseReturnToSupplierAppService returnToSupplierAppService;

    /** 药库损益同意。 */
    private final InventoryWarehouseGainLossAppService warehouseGainLossAppService;

    /** 药房损益同意。 */
    private final InventoryPharmacyGainLossAppService pharmacyGainLossAppService;

    /** 科室损益同意。 */
    private final InventoryDepartmentGainLossAppService departmentGainLossAppService;

    /** 药库盘点同意。 */
    private final InventoryWarehouseStocktakeAppService warehouseStocktakeAppService;

    /** 药房盘点同意。 */
    private final InventoryPharmacyStocktakeAppService pharmacyStocktakeAppService;

    /** 科室盘点同意。 */
    private final InventoryDepartmentStocktakeAppService departmentStocktakeAppService;

    /** 调价同意（IF-PRC-13：落价→unlock→approve）。 */
    private final PriceAdjustmentAppService priceAdjustmentAppService;

    /**
     * 分发同意：写库点在被复用业务路径内。
     *
     * @param busNo        单号
     * @param documentKind SUPPLY / PRICE_ADJUSTMENT
     * @param typeEnum     供应码；调价可空
     * @return 同意后状态码（供应 {@code AGREE} / 调价 {@code approved}）
     * @throws BusinessException 类型非法或下游动账失败
     */
    public String agree(String busNo, String documentKind, String typeEnum) {
        String kind = documentKind.trim();
        String trimmedBusNo = busNo.trim();
        // 调价：复用 AS-FIN-PRC-001 IF-PRC-13（落价→物流 unlock→approve）
        if (FinanceApprovalConstants.DOCUMENT_KIND_PRICE_ADJUSTMENT.equals(kind)) {
            ApprovePriceAdjustmentRequest request = new ApprovePriceAdjustmentRequest();
            request.setBusNo(trimmedBusNo);
            this.priceAdjustmentAppService.approve(request);
            return PriceAdjustmentStatus.APPROVED.getCode();
        }
        // 非供应种类：拒绝，防止误入供应分支
        if (!FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY.equals(kind)) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "documentKind 非法");
        }
        // 供应必须带财务族 type，否则无法互斥分发
        if (StringUtils.isEmpty(typeEnum)) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "供应类型不能为空");
        }
        String typeCode = typeEnum.trim();
        // 进货：复用 IF-WHS-IN-08
        if (SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(typeCode)) {
            AgreeInboundReceiptRequest request = new AgreeInboundReceiptRequest();
            request.setBusNo(trimmedBusNo);
            this.inboundReceiptAppService.agreeInboundReceipt(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 退供应商：复用 IF-WHS-RT-08
        if (SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(typeCode)) {
            AgreeReturnToSupplierRequest request = new AgreeReturnToSupplierRequest();
            request.setBusNo(trimmedBusNo);
            this.returnToSupplierAppService.agreeReturnToSupplier(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 药库损益
        if (SupplyType.CABINET_GAIN_LOSS.getCode().equals(typeCode)) {
            com.openhis.health.application.inventory.warehouse.gainloss.request.AgreeGainLossRequest request =
                new com.openhis.health.application.inventory.warehouse.gainloss.request.AgreeGainLossRequest();
            request.setBusNo(trimmedBusNo);
            this.warehouseGainLossAppService.agreeGainLoss(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 药房损益
        if (SupplyType.PHARMACY_GAIN_LOSS.getCode().equals(typeCode)) {
            AgreePharmacyGainLossRequest request = new AgreePharmacyGainLossRequest();
            request.setBusNo(trimmedBusNo);
            this.pharmacyGainLossAppService.agreePharmacyGainLoss(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 科室损益
        if (SupplyType.DEPT_GAIN_LOSS.getCode().equals(typeCode)) {
            AgreeGainLossRequest request = new AgreeGainLossRequest();
            request.setBusNo(trimmedBusNo);
            this.departmentGainLossAppService.agreeGainLoss(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 药库盘点（含 createStocktakingDelivery）
        if (SupplyType.CABINET_STOCKTAKE.getCode().equals(typeCode)) {
            com.openhis.health.application.inventory.warehouse.stocktake.request.AgreeStocktakeRequest request =
                new com.openhis.health.application.inventory.warehouse.stocktake.request.AgreeStocktakeRequest();
            request.setBusNo(trimmedBusNo);
            this.warehouseStocktakeAppService.agreeStocktake(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 药房盘点
        if (SupplyType.PHARMACY_STOCKTAKE.getCode().equals(typeCode)) {
            AgreePharmacyStocktakeRequest request = new AgreePharmacyStocktakeRequest();
            request.setBusNo(trimmedBusNo);
            this.pharmacyStocktakeAppService.agreePharmacyStocktake(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 科室盘点
        if (SupplyType.DEPT_STOCKTAKE.getCode().equals(typeCode)) {
            AgreeStocktakeRequest request = new AgreeStocktakeRequest();
            request.setBusNo(trimmedBusNo);
            this.departmentStocktakeAppService.agreeStocktake(request);
            return SupplyStatus.AGREE.getCode();
        }
        // 白名单外类型（含已废止的科室耗材盘点）不得经本页审
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单据类型不在财务审批范围");
    }
}
