/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.support;

import com.zxplat.application.finance.cashiersettlementapproval.response.LoadCashierSettlementApprovalDetailResponse;
import com.zxplat.application.shared.pharmacydailysettlement.PharmacyDailySettlementCategoryRowDto;
import com.zxplat.application.shared.pharmacydailysettlement.PharmacyDailySettlementProductRowDto;
import com.zxplat.application.shared.pharmacydailysettlement.PharmacyDailySettlementPreviewAssembler;
import com.zxplat.application.inpatient.financial.dailysettlement.request.LoadInpatientCashierSettlementVoucherRequest;
import com.zxplat.application.inpatient.financial.dailysettlement.response.LoadInpatientCashierSettlementVoucherResponse;
import com.zxplat.application.inpatient.financial.dailysettlement.service.InpatientDailySettlementAppService;
import com.zxplat.application.outpatient.financial.dailysettlement.request.LoadOutpatientCashierSettlementVoucherRequest;
import com.zxplat.application.outpatient.financial.dailysettlement.response.LoadOutpatientCashierSettlementVoucherResponse;
import com.zxplat.application.outpatient.financial.dailysettlement.service.OutpatientDailySettlementAppService;
import com.zxplat.application.shared.financial.cashiersettlement.CashierSettlementMountedVoucherDto;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementHeaderQueryModel;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.CashierSettlementType;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 交账审批详情日结体现算（按挂载源表；DD §2.8.4）。
 *
 * <p>收款复用门/住日结历史重打现算；药房复用 {@link PharmacyDailySettlementPreviewAssembler}
 *（与门/住药房日结预览主区同口径）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CashierSettlementApprovalDetailBodySupport {

    /** 门诊收款日结（挂载现算打印体）。 */
    private final OutpatientDailySettlementAppService outpatientDailySettlementAppService;

    /** 住院收款日结（挂载现算打印体）。 */
    private final InpatientDailySettlementAppService inpatientDailySettlementAppService;

    /** 发药挂载读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 药房大类/品规装配。 */
    private final PharmacyDailySettlementPreviewAssembler pharmacyDailySettlementPreviewAssembler;

    /** 药品大类术语标签。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 按交账类型填充详情响应中的现算日结体。
     *
     * @param response 已含头表的详情响应
     * @param header   交账头投影
     */
    public void fillDetailBody(
        LoadCashierSettlementApprovalDetailResponse response,
        CashierSettlementHeaderQueryModel header) {
        if (response == null || header == null) {
            return;
        }
        String typeEnum = header.getTypeEnum();
        Long settlementId = header.getSettlementId();
        // 门诊收款：复用凭证现算
        if (CashierSettlementType.OP_PAYMENT.getCode().equals(typeEnum)) {
            this.fillFromOutpatientVoucher(response, settlementId);
            response.setDetailBodyRecomputeNote("收款日结单内容按挂载 payment 现算。");
            return;
        }
        // 住院收款
        if (CashierSettlementType.IP_PAYMENT.getCode().equals(typeEnum)) {
            this.fillFromInpatientVoucher(response, settlementId);
            response.setDetailBodyRecomputeNote("收款日结单内容按挂载 payment 现算。");
            return;
        }
        // 门诊药房（与 OutpatientPharmacyDailySettlement 预览主区同口径）
        if (CashierSettlementType.OP_PHARMACY.getCode().equals(typeEnum)) {
            this.fillPharmacyBody(response, settlementId, false, "门诊");
            return;
        }
        // 住院药房（与 InpatientPharmacyDailySettlement 预览主区同口径）
        if (CashierSettlementType.IP_PHARMACY.getCode().equals(typeEnum)) {
            this.fillPharmacyBody(response, settlementId, true, "住院");
            return;
        }
        response.setDetailBodyRecomputeNote("未知交账类型，仅展示头表。");
    }

    /**
     * 门诊收款：调用日结凭证现算填充 rows/summary。
     *
     * @param response     详情响应
     * @param settlementId 交账头主键
     */
    private void fillFromOutpatientVoucher(
        LoadCashierSettlementApprovalDetailResponse response,
        Long settlementId) {
        try {
            LoadOutpatientCashierSettlementVoucherRequest request =
                new LoadOutpatientCashierSettlementVoucherRequest();
            request.setSettlementId(settlementId);
            LoadOutpatientCashierSettlementVoucherResponse voucher =
                this.outpatientDailySettlementAppService.loadOutpatientCashierSettlementVoucher(request);
            if (voucher == null) {
                return;
            }
            this.applyPaymentMountedVoucher(response, voucher.getMountedVoucher());
        } catch (RuntimeException runtimeException) {
            log.warn("交账审批详情：门诊收款现算失败 settlementId={}", settlementId, runtimeException);
            response.setDetailBodyRecomputeNote("收款日结体现算失败，仅展示头表；请稍后重试或核对挂载。");
        }
    }

    /**
     * 住院收款：调用日结凭证现算填充 rows/summary。
     *
     * @param response     详情响应
     * @param settlementId 交账头主键
     */
    private void fillFromInpatientVoucher(
        LoadCashierSettlementApprovalDetailResponse response,
        Long settlementId) {
        try {
            LoadInpatientCashierSettlementVoucherRequest request =
                new LoadInpatientCashierSettlementVoucherRequest();
            request.setSettlementId(settlementId);
            LoadInpatientCashierSettlementVoucherResponse voucher =
                this.inpatientDailySettlementAppService.loadInpatientCashierSettlementVoucher(request);
            if (voucher == null) {
                return;
            }
            this.applyPaymentMountedVoucher(response, voucher.getMountedVoucher());
        } catch (RuntimeException runtimeException) {
            log.warn("交账审批详情：住院收款现算失败 settlementId={}", settlementId, runtimeException);
            response.setDetailBodyRecomputeNote("收款日结体现算失败，仅展示头表；请稍后重试或核对挂载。");
        }
    }

    /**
     * 将挂载现算打印体 rows/summary 写入审批详情。
     *
     * @param response       详情响应
     * @param mountedVoucher 挂载现算打印体
     */
    private void applyPaymentMountedVoucher(
        LoadCashierSettlementApprovalDetailResponse response,
        CashierSettlementMountedVoucherDto mountedVoucher) {
        if (mountedVoucher == null) {
            return;
        }
        response.setPaymentRows(mountedVoucher.getRows());
        response.setPaymentSummary(mountedVoucher.getSummary());
        // 笔数保持头表口径（不含工伤）；挂载仍含工伤 payment，禁止用全量挂载数覆盖快照
    }

    /**
     * 装配药房日结大类/品规（门/住同口径；空挂载仍回固定大类零行）。
     *
     * @param response      详情响应
     * @param settlementId  交账头主键
     * @param inpatientMode 住院药房为 true
     * @param scopeLabel    门诊/住院（写入现算说明）
     */
    private void fillPharmacyBody(
        LoadCashierSettlementApprovalDetailResponse response,
        Long settlementId,
        boolean inpatientMode,
        String scopeLabel) {
        try {
            List<MedicationDispense> dispenses =
                this.medicationDispenseQueryService.findDispensesByCashierSettlementId(settlementId);
            if (dispenses == null) {
                dispenses = Collections.emptyList();
            }
            Map<String, String> categoryLabelByCode =
                this.terminologyOptionLoader.buildLabelMap(
                    TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
            // Asm：与门/住药房日结预览主区同管线（进/售分桶 + 固定四大类）
            List<PharmacyDailySettlementCategoryRowDto> categoryRows =
                this.pharmacyDailySettlementPreviewAssembler.assembleCategoryRows(
                    dispenses,
                    categoryLabelByCode,
                    inpatientMode);
            List<PharmacyDailySettlementProductRowDto> productRows =
                this.pharmacyDailySettlementPreviewAssembler.assembleProductRows(
                    dispenses,
                    inpatientMode);
            response.setPharmacyCategoryRows(categoryRows);
            response.setPharmacyProductRows(productRows);
            response.setMountedSourceCount(dispenses.size());
            if (dispenses.isEmpty()) {
                response.setDetailBodyRecomputeNote(
                    "当前交账无挂载发药（可能已撤回/驳回清挂载）；大类为零值占位，品规为空。");
                return;
            }
            // Asm：有挂载时详情头金额与大类售价总额之和对齐（覆盖头表快照展示值）
            this.overlayPharmacyHeaderSaleTotal(response, categoryRows);
            response.setDetailBodyRecomputeNote(
                "药房日结大类/品规按挂载发药现算（对齐" + scopeLabel + "药房日结主区）。");
        } catch (RuntimeException runtimeException) {
            log.warn(
                "交账审批详情：药房日结现算失败 settlementId={} inpatientMode={}",
                settlementId,
                inpatientMode,
                runtimeException);
            response.setPharmacyCategoryRows(Collections.emptyList());
            response.setPharmacyProductRows(Collections.emptyList());
            response.setDetailBodyRecomputeNote("药房日结体现算失败，仅展示头表；请稍后重试或核对挂载。");
        }
    }

    /**
     * 详情头金额覆盖为各大类售价总额之和（与审批列表药房展示口径一致）。
     *
     * @param response     详情响应
     * @param categoryRows 大类汇总行
     */
    private void overlayPharmacyHeaderSaleTotal(
        LoadCashierSettlementApprovalDetailResponse response,
        List<PharmacyDailySettlementCategoryRowDto> categoryRows) {
        CashierSettlementApprovalHeaderDto header = response.getHeader();
        if (header == null) {
            return;
        }
        List<BigDecimal> saleAmounts = new ArrayList<>();
        if (categoryRows != null) {
            // Pre：收集各大类已结算售价
            for (PharmacyDailySettlementCategoryRowDto row : categoryRows) {
                if (row == null || row.getSaleAmount() == null) {
                    continue;
                }
                saleAmounts.add(row.getSaleAmount());
            }
        }
        header.setTotalIncome(MonetaryAmountSupport.sumThenSettle(saleAmounts));
    }
}
