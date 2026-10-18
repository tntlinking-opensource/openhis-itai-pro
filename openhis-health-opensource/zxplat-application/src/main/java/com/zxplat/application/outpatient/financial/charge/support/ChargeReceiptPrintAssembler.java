/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.support;

import com.zxplat.application.outpatient.financial.charge.dto.ChargeFeeListRowDto;
import com.zxplat.application.outpatient.financial.charge.dto.ChargeReceiptChargeLineDto;
import com.zxplat.application.outpatient.financial.charge.dto.ChargeReceiptSlipDto;
import com.zxplat.application.outpatient.financial.charge.dto.ChargeSettlementInsuranceSummaryDto;
import com.zxplat.application.outpatient.financial.charge.dto.ChargeSettlementPaymentLineDto;
import com.zxplat.application.outpatient.financial.charge.dto.ChargeSettlementSetlinfoDto;
import com.zxplat.application.shared.financial.InsuranceReceiptAccountBalanceSupport;
import com.zxplat.application.shared.report.ChargeItemOrderingDoctorFromRequestSupport;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.share.constants.DoctorStationTcmConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.OutpatientChargeConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.ChargeTypeCategory;
import com.zxplat.domain.share.enums.OrganizationType;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.shared.product.ChargeItemProductLookupHelper;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 门诊收费打印字段组装（小票 op-charge-print / 发票 op-invoice-print / 费用清单 op-feeList-print）。
 */
@Component
@RequiredArgsConstructor
public class ChargeReceiptPrintAssembler {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterContextReader encounterContextReader;
    private final EncounterQueryService encounterQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemOrderingDoctorFromRequestSupport chargeItemOrderingDoctorFromRequestSupport;
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;
    private final OutpatientChargeInsuranceExtension outpatientChargeInsuranceExtension;
    private final InsuranceRegisterBenefitQueryExtension insuranceRegisterBenefitQueryExtension;

    /** 针式发票日期栏：yyyy/MM/dd。 */
    private static final DateTimeFormatter INVOICE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 药品目录：中成药。 */
    private static final String MEDICATION_CATEGORY_PATENT = "1";

    /** 药品目录：西药。 */
    private static final String MEDICATION_CATEGORY_WESTERN = "2";

    /** 费用清单标题：普通门诊。 */
    private static final String FEE_LIST_TITLE_GENERAL = "门诊患者费用清单";

    /** 费用清单标题：慢病号。 */
    private static final String FEE_LIST_TITLE_CHRONIC = "门诊慢病患者费用清单";

    /** 就诊类型回落：普通门诊。 */
    private static final String VISIT_TYPE_GENERAL = "普通门诊";

    /** 就诊类型回落：门诊慢病。 */
    private static final String VISIT_TYPE_CHRONIC = "门诊慢病";

    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 按结算单（成功支付明细）解析医保分摊摘要，供收费页患者信息栏展示。
     *
     * <p>口径与小票 {@link #applyPaymentBreakdown} 一致：统筹/个账取自 {@code INSURANCE_*}，
     * 个人自付为现金/微信/支付宝/银行卡合计；无成功支付时返回 {@code null}。</p>
     *
     * @param encounterId 就诊主键
     * @param paymentId   支付流水；空则取就诊最近成功支付
     * @return 摘要；无支付明细时 null
     */
    public ChargeSettlementInsuranceSummaryDto resolveSettlementInsuranceSummary(
        Long encounterId,
        Long paymentId) {
        if (encounterId == null) {
            return null;
        }
        Long resolvedPaymentId = paymentId;
        if (resolvedPaymentId == null) {
            Map<Long, Long> paymentByEncounter =
                paymentReconciliationQueryService.findLatestSuccessPaymentIdsByEncounterIds(List.of(encounterId));
            resolvedPaymentId = paymentByEncounter.get(encounterId);
        }
        if (resolvedPaymentId == null) {
            return null;
        }
        List<PaymentRecDetailQueryModel> details =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(resolvedPaymentId);
        if (details == null || details.isEmpty()) {
            return null;
        }
        BigDecimal fundPay = BigDecimal.ZERO;
        BigDecimal acctPay = BigDecimal.ZERO;
        BigDecimal selfPay = BigDecimal.ZERO;
        BigDecimal medfee = BigDecimal.ZERO;
        boolean hasInsurance = false;
        // 非零渠道展示名按出现序去重，供支付记录「收费明细」支付方式列
        LinkedHashSet<String> payMethodLabels = new LinkedHashSet<>();
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null || detail.getAmount() == null) {
                continue;
            }
            BigDecimal amount = detail.getAmount();
            medfee = medfee.add(amount);
            String payEnum = StringUtils.trimToEmpty(detail.getPayEnum());
            if (PaymentChannel.INSURANCE_FUND.getCode().equals(payEnum)) {
                fundPay = fundPay.add(amount);
                hasInsurance = true;
            } else if (PaymentChannel.INSURANCE_ACCT.getCode().equals(payEnum)) {
                acctPay = acctPay.add(amount);
                hasInsurance = true;
            } else {
                PaymentChannel channel = PaymentChannel.fromCode(payEnum);
                if (channel != null && channel.isUserSelectable() && !channel.isUiOnly()) {
                    selfPay = selfPay.add(amount);
                }
            }
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                String payMethodLabel = this.resolveSettlementPayMethodLabel(payEnum);
                if (StringUtils.isNotEmpty(payMethodLabel)) {
                    payMethodLabels.add(payMethodLabel);
                }
            }
        }
        ChargeSettlementInsuranceSummaryDto summary = new ChargeSettlementInsuranceSummaryDto();
        summary.setPaymentId(resolvedPaymentId);
        summary.setInsuranceSettled(hasInsurance);
        summary.setMedfeeSumamt(MonetaryAmountSupport.scaleSettle(medfee));
        summary.setFundPaySumamt(MonetaryAmountSupport.scaleSettle(fundPay));
        summary.setAcctPay(MonetaryAmountSupport.scaleSettle(acctPay));
        summary.setPsnCashPay(MonetaryAmountSupport.scaleSettle(selfPay));
        if (!payMethodLabels.isEmpty()) {
            summary.setPayMethodText(String.join("、", payMethodLabels));
        }
        this.fillSettlementSummaryPaymentHeader(summary, resolvedPaymentId);
        this.fillSettlementSetlinfo(summary, resolvedPaymentId);
        return summary;
    }

    /**
     * 支付记录摘要：子明细 {@code pay_enum} → 支付方式展示名。
     *
     * <p>收银台占位回落为「现金」；折扣/预交金冲抵不展示；未识别 code 保留原文。</p>
     *
     * @param payEnum 持久化支付渠道 code
     * @return 展示名；空白或不展示渠道时为 {@code null}
     */
    private String resolveSettlementPayMethodLabel(String payEnum) {
        if (StringUtils.isEmpty(payEnum)) {
            return null;
        }
        PaymentChannel channel = PaymentChannel.fromCode(payEnum);
        if (PaymentChannel.DISCOUNT.equals(channel) || PaymentChannel.DEPOSIT_OFFSET.equals(channel)) {
            return null;
        }
        if (PaymentChannel.CASHIER.equals(channel)) {
            return PaymentChannel.CASH.getDisplay();
        }
        if (channel != null && StringUtils.isNotEmpty(channel.getDisplay())) {
            return channel.getDisplay().trim();
        }
        return payEnum;
    }

    /**
     * 列出就诊下全部成功支付的结算分摊摘要（账单时间倒序）。
     *
     * @param encounterId 就诊主键
     * @return 摘要列表；无成功支付时为空列表
     */
    public List<ChargeSettlementInsuranceSummaryDto> listSettlementInsuranceSummaries(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.listSuccessPaymentsByEncounterId(encounterId);
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        List<ChargeSettlementInsuranceSummaryDto> options = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            ChargeSettlementInsuranceSummaryDto summary =
                this.resolveSettlementInsuranceSummary(encounterId, payment.getId());
            if (summary != null) {
                options.add(summary);
            }
        }
        return options;
    }

    /**
     * 从支付主单回填业务号、账单时间、收款人与展示金额。
     *
     * @param summary   摘要 DTO
     * @param paymentId 支付主键
     */
    private void fillSettlementSummaryPaymentHeader(
        ChargeSettlementInsuranceSummaryDto summary,
        Long paymentId) {
        if (summary == null || paymentId == null) {
            return;
        }
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(paymentId);
        if (payment == null) {
            return;
        }
        summary.setPaymentNo(payment.getPaymentNo());
        summary.setBillDate(payment.getBillDate());
        // 收款人：与收费小票一致，取支付流水 enterer_id 对应医务人员姓名
        summary.setCashierName(this.resolveCashierName(payment.getEntererId()));
        BigDecimal displayAmount = payment.getDisplayAmount();
        if (displayAmount == null) {
            displayAmount = payment.getTenderedAmount();
        }
        summary.setDisplayAmount(displayAmount);
        List<Long> chargeItemIds = parseChargeItemIds(payment.getChargeItemIds());
        summary.setChargeItemIds(chargeItemIds);
        summary.setChargeLines(this.buildSettlementPaymentLines(chargeItemIds));
    }

    /**
     * 从第三方正式结算回执回填 2206/2207 setlinfo；混合结算多笔时金额汇总。
     *
     * <p>有有效回执时同步覆盖摘要区医疗费/统筹/个账/自付为平台口径。</p>
     *
     * @param summary   支付结算摘要
     * @param paymentId 支付主键
     */
    private void fillSettlementSetlinfo(ChargeSettlementInsuranceSummaryDto summary, Long paymentId) {
        if (summary == null || paymentId == null || !outpatientChargeInsuranceExtension.isEnabled()) {
            return;
        }
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            outpatientChargeInsuranceExtension.listSettlementsByPayment(paymentId);
        if (records == null || records.isEmpty()) {
            return;
        }
        ChargeSettlementSetlinfoDto setlinfo = null;
        for (OutpatientChargeSettlementRecordExtensionOut record : records) {
            if (record == null || "REVOKED".equals(StringUtils.trimToEmpty(record.getStatus()))) {
                continue;
            }
            if (setlinfo == null) {
                setlinfo = new ChargeSettlementSetlinfoDto()
                    .setSetlId(StringUtils.trimToNull(record.getSettlementId()))
                    .setMdtrtId(StringUtils.trimToNull(record.getEncounterPlatformId()))
                    .setChrgBchno(StringUtils.trimToNull(record.getChargeBatchNo()))
                    .setClrOptins(StringUtils.trimToNull(record.getClrOptins()))
                    .setClrWay(StringUtils.trimToNull(record.getClrWay()))
                    .setClrType(StringUtils.trimToNull(record.getClrType()))
                    .setPoolPropSelfpay(record.getPoolPropSelfpay())
                    .setCvlservFlag(StringUtils.trimToNull(record.getCvlservFlag()))
                    .setBalc(record.getBalc());
            } else if (record.getBalc() != null) {
                // 多笔时余额取最后一笔非空值
                setlinfo.setBalc(record.getBalc());
            }
            // 公务员标志多笔时保留首笔非空（标识类字段不求和）
            if (setlinfo.getCvlservFlag() == null) {
                setlinfo.setCvlservFlag(StringUtils.trimToNull(record.getCvlservFlag()));
            }
            setlinfo.setMedfeeSumamt(sumNullable(setlinfo.getMedfeeSumamt(), record.getMedfeeSumamt()));
            setlinfo.setFulamtOwnpayAmt(sumNullable(setlinfo.getFulamtOwnpayAmt(), record.getFulamtOwnpayAmt()));
            setlinfo.setOverlmtSelfpay(sumNullable(setlinfo.getOverlmtSelfpay(), record.getOverlmtSelfpay()));
            setlinfo.setPreselfpayAmt(sumNullable(setlinfo.getPreselfpayAmt(), record.getPreselfpayAmt()));
            setlinfo.setInscpScpAmt(sumNullable(setlinfo.getInscpScpAmt(), record.getInscpScpAmt()));
            setlinfo.setActPayDedc(sumNullable(setlinfo.getActPayDedc(), record.getActPayDedc()));
            setlinfo.setHifpPay(sumNullable(setlinfo.getHifpPay(), record.getHifpPay()));
            setlinfo.setCvlservPay(sumNullable(setlinfo.getCvlservPay(), record.getCvlservPay()));
            setlinfo.setHifesPay(sumNullable(setlinfo.getHifesPay(), record.getHifesPay()));
            setlinfo.setHifmiPay(sumNullable(setlinfo.getHifmiPay(), record.getHifmiPay()));
            setlinfo.setHifobPay(sumNullable(setlinfo.getHifobPay(), record.getHifobPay()));
            setlinfo.setMafPay(sumNullable(setlinfo.getMafPay(), record.getMafPay()));
            setlinfo.setHifdmPay(sumNullable(setlinfo.getHifdmPay(), record.getHifdmPay()));
            setlinfo.setOthPay(sumNullable(setlinfo.getOthPay(), record.getOthPay()));
            setlinfo.setFundPaySumamt(sumNullable(setlinfo.getFundPaySumamt(), record.getFundPaySumamt()));
            setlinfo.setPsnPartAmt(sumNullable(setlinfo.getPsnPartAmt(), record.getPsnPartAmt()));
            setlinfo.setAcctPay(sumNullable(setlinfo.getAcctPay(), record.getAcctPay()));
            setlinfo.setPsnCashPay(sumNullable(setlinfo.getPsnCashPay(), record.getPsnCashPay()));
            setlinfo.setHospPartAmt(sumNullable(setlinfo.getHospPartAmt(), record.getHospPartAmt()));
            setlinfo.setAcctMulaidPay(sumNullable(setlinfo.getAcctMulaidPay(), record.getAcctMulaidPay()));
        }
        if (setlinfo == null) {
            return;
        }
        summary.setSetlinfo(setlinfo);
        summary.setInsuranceSettled(true);
        if (setlinfo.getMedfeeSumamt() != null) {
            summary.setMedfeeSumamt(MonetaryAmountSupport.scaleSettle(setlinfo.getMedfeeSumamt()));
        }
        if (setlinfo.getFundPaySumamt() != null) {
            summary.setFundPaySumamt(MonetaryAmountSupport.scaleSettle(setlinfo.getFundPaySumamt()));
        }
        if (setlinfo.getAcctPay() != null) {
            summary.setAcctPay(MonetaryAmountSupport.scaleSettle(setlinfo.getAcctPay()));
        }
        if (setlinfo.getPsnCashPay() != null) {
            summary.setPsnCashPay(MonetaryAmountSupport.scaleSettle(setlinfo.getPsnCashPay()));
        }
    }

    private static BigDecimal sumNullable(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    /**
     * 按支付关联收费项 id 组装只读明细（不按收银台状态过滤，含已退费/挂号锚点等）。
     *
     * <p>项目名/数量单位口径与 {@link #buildChargeItemLines}、待收费列表一致：优先目录
     * {@code product_name} 与单位 band 展示名；禁止用单据号回落项目名。</p>
     *
     * @param chargeItemIds 收费项主键
     * @return 明细行；无 id 时为空列表
     */
    private List<ChargeSettlementPaymentLineDto> buildSettlementPaymentLines(List<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return List.of();
        }
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setIds(chargeItemIds);
        List<ChargeItem> loaded = chargeItemQueryService.loadChargeItemsByIds(command);
        if (loaded == null || loaded.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem item : loaded) {
            if (item != null && item.getDefinitionId() != null) {
                definitionIds.add(item.getDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> definitionMap =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        // 批量加载目录，供项目名与数量单位展示解析（与小票明细同口径）
        Map<String, ProductDefinitionDisplayDto> productMap = this.loadProductMapForChargeItems(loaded);
        List<ChargeSettlementPaymentLineDto> lines = new ArrayList<>();
        for (ChargeItem item : loaded) {
            if (item == null || item.getId() == null) {
                continue;
            }
            ChargeSettlementPaymentLineDto line = new ChargeSettlementPaymentLineDto();
            line.setChargeItemId(item.getId());
            line.setBusNo(StringUtils.trimToNull(item.getBusNo()));
            ChargeItemDefinition definition = null;
            if (item.getDefinitionId() != null) {
                definition = definitionMap.get(item.getDefinitionId());
            }
            ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(item, productMap);
            line.setItemName(firstNonBlank(
                this.resolveChargeLineDisplayName(item, definition, product),
                OutpatientChargeConstants.PENDING_CHARGE_ITEM_DISPLAY_FALLBACK));
            if (item.getQuantityValue() != null) {
                line.setQuantityDisplay(item.getQuantityValue().stripTrailingZeros().toPlainString());
            }
            // 单位 code → 目录 band 展示名（如「盒」）；未命中时回落持久化 code
            String unitDisplay = this.resolveQuantityUnitDisplay(item.getQuantityUnit(), product);
            if (StringUtils.isNotEmpty(unitDisplay)) {
                line.setQuantityUnit(unitDisplay);
            } else {
                line.setQuantityUnit(StringUtils.trimToNull(item.getQuantityUnit()));
            }
            line.setStatusCode(StringUtils.trimToNull(item.getStatusEnum()));
            ChargeItemStatus status =
                EnumUtil.fromCode(ChargeItemStatus.class, StringUtils.trimToEmpty(item.getStatusEnum()));
            if (status != null) {
                line.setStatusText(status.getDisplay());
            } else {
                line.setStatusText(StringUtils.trimToNull(item.getStatusEnum()));
            }
            line.setTotalPrice(item.getTotalPrice());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 组装门诊收费小票打印字段。
     *
     * @param slip 输出 DTO（非 null）
     * @param encounterId 就诊主键
     * @param paymentId 支付流水主键；可空时按就诊取最近成功支付
     */
    public void enrichPrintFields(ChargeReceiptSlipDto slip, Long encounterId, Long paymentId) {
        if (slip == null || encounterId == null) {
            return;
        }
        slip.setEncounterId(encounterId);
        OutpatientEncounterContextQueryModel context =
            encounterContextReader.getOutpatientContextByEncounterId(encounterId).orElse(null);
        String patientName = null;
        if (context != null) {
            patientName = context.getPatientName();
        }
        slip.setPatientName(firstNonBlank(patientName, ""));
        slip.setHospitalName(firstNonBlank(resolveHospitalName(), ""));
        slip.setGenderEnumText(firstNonBlank(resolveGenderText(context), ""));
        String ageText = null;
        if (context != null) {
            ageText = context.getAgeText();
        }
        slip.setAgeText(firstNonBlank(ageText, ""));

        Long resolvedPaymentId = paymentId;
        if (resolvedPaymentId == null) {
            Map<Long, Long> paymentByEncounter =
                paymentReconciliationQueryService.findLatestSuccessPaymentIdsByEncounterIds(List.of(encounterId));
            resolvedPaymentId = paymentByEncounter.get(encounterId);
        }

        PaymentReconciliationQueryModel payment = null;
        if (resolvedPaymentId != null) {
            payment = paymentReconciliationQueryService.mustGetPayment(resolvedPaymentId);
        }

        List<ChargeItem> chargeItems = loadChargeItemsForPayment(payment, encounterId);
        slip.setFeeNatureText(firstNonBlank(resolveFeeNatureText(chargeItems, encounterId), ""));
        List<ChargeReceiptChargeLineDto> chargeLines = this.buildChargeItemLines(chargeItems, true);
        slip.setChargeItemLines(chargeLines);

        BigDecimal totalAmount = resolveTotalAmount(payment, chargeLines);
        slip.setTotalAmountText(formatAmountText(totalAmount));

        OutpatientRegistrationPlatformQueryExtensionOut insuranceSnapshot = resolveInsuranceSnapshot(encounterId);
        applyPaymentBreakdown(slip, payment, insuranceSnapshot, totalAmount);

        if (payment != null) {
            slip.setCashierName(firstNonBlank(resolveCashierName(payment.getEntererId()), ""));
            slip.setChargeTimeText(formatChargeTime(payment.getBillDate()));
        } else {
            slip.setCashierName("");
            slip.setChargeTimeText("");
        }
        this.fillInvoiceSheet(slip, context, payment, chargeItems, totalAmount, encounterId);
        this.fillFeeListSheet(slip, context, chargeItems);
    }

    /**
     * 加载小票收费明细：优先 payment.chargeItemIds，否则回落就诊全量 ChargeItem。
     *
     * @param payment     支付读模型（可 null）
     * @param encounterId 就诊主键
     * @return 收费项列表
     */
    private List<ChargeItem> loadChargeItemsForPayment(
        PaymentReconciliationQueryModel payment,
        Long encounterId) {
        String chargeItemIdsJson = null;
        if (payment != null) {
            chargeItemIdsJson = payment.getChargeItemIds();
        }
        List<Long> chargeItemIds = parseChargeItemIds(chargeItemIdsJson);
        if (!chargeItemIds.isEmpty()) {
            ChargeItemQueryCommand command = new ChargeItemQueryCommand();
            command.setIds(chargeItemIds);
            List<ChargeItem> loaded = chargeItemQueryService.loadChargeItemsByIds(command);
            if (loaded != null && !loaded.isEmpty()) {
                return loaded;
            }
        }
        ChargeItemQueryCommand fallback = new ChargeItemQueryCommand();
        fallback.setEncounterIds(List.of(encounterId));
        List<ChargeItem> lines = chargeItemQueryService.listChargeItemsByEncounterIds(fallback);
        if (lines == null) {
            return List.of();
        }
        return lines;
    }

    /**
     * 院点收费小票：在产品 enrich 之后把**检验**带子项明细换成子项行。
     *
     * <p>检查/治疗仍打父项。不改写合计金额与支付分摊（仍按支付单）。无检验子项时与产品明细一致。</p>
     *
     * @param slip        已 enrich 的小票
     * @param encounterId 就诊主键
     * @param paymentId   支付主键；可空则按就诊最近成功支付
     */
    public void rebuildChargeItemLinesPrintingActivityDefinitionChildren(
        ChargeReceiptSlipDto slip, Long encounterId, Long paymentId) {
        if (slip == null || encounterId == null) {
            // 无小票或无就诊则无法替换明细
            return;
        }
        Long resolvedPaymentId = paymentId;
        if (resolvedPaymentId == null) {
            // 未传支付单时与产品 enrich 一致：取就诊最近成功支付
            Map<Long, Long> paymentByEncounter =
                paymentReconciliationQueryService.findLatestSuccessPaymentIdsByEncounterIds(List.of(encounterId));
            resolvedPaymentId = paymentByEncounter.get(encounterId);
        }
        PaymentReconciliationQueryModel payment = null;
        if (resolvedPaymentId != null) {
            // 有支付单才加载支付模型，供取关联收费项
            payment = paymentReconciliationQueryService.mustGetPayment(resolvedPaymentId);
        }
        List<ChargeItem> chargeItems = this.loadChargeItemsForPayment(payment, encounterId);
        slip.setChargeItemLines(this.buildChargeItemLines(chargeItems, false));
    }

    /**
     * 收费项 → 小票明细行（含目录名、数量单位与收费类别展示）。
     *
     * <p>中草药明细合并为一行「中药费」：不打印单味药名与剂量，金额为各味药合计；
     * 代煎费、西药/中成药等非中草药行仍逐项展示。合并行插在首条中草药原位置。</p>
     *
     * @param chargeItems    收费项列表
     * @param printParentOnly true 时跳过子账单（产品默认）；false 时仅检验带子项只打子账单
     * @return 打印明细行
     */
    private List<ChargeReceiptChargeLineDto> buildChargeItemLines(
        List<ChargeItem> chargeItems, boolean printParentOnly) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem line : chargeItems) {
            if (line != null && line.getDefinitionId() != null) {
                definitionIds.add(line.getDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> definitionById;
        if (definitionIds.isEmpty()) {
            definitionById = Map.of();
        } else {
            definitionById = chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(new ArrayList<>(definitionIds));
        }
        Map<String, ProductDefinitionDisplayDto> productMap = loadProductMapForChargeItems(chargeItems);
        Set<Long> labActivityDefinitionParentIds =
            this.collectLabActivityDefinitionParentChargeIds(chargeItems, productMap);

        List<ChargeReceiptChargeLineDto> result = new ArrayList<>();
        List<BigDecimal> herbalAmounts = new ArrayList<>();
        int herbalInsertIndex = -1;
        for (ChargeItem line : chargeItems) {
            if (line == null) {
                // 空收费项不进小票
                continue;
            }
            if (printParentOnly) {
                // 产品默认：子账单不进收费小票（只打父行）
                if (line.getParentId() != null) {
                    continue;
                }
            } else {
                if (line.getId() != null && labActivityDefinitionParentIds.contains(line.getId())) {
                    // 只打检验子项：隐藏检验带子项父账单
                    continue;
                }
                if (line.getParentId() != null
                    && !labActivityDefinitionParentIds.contains(line.getParentId())) {
                    // 检查/治疗子账单与组套等其它 parent_id 子行仍不进小票
                    continue;
                }
            }
            ChargeItemDefinition definition = definitionById.get(line.getDefinitionId());
            ProductDefinitionDisplayDto product = resolveProductForChargeItem(line, productMap);
            // 中草药：累计金额，跳过逐味明细（剂量不进小票）
            if (isHerbalTcmChargeItem(line, product)) {
                if (herbalInsertIndex < 0) {
                    herbalInsertIndex = result.size();
                }
                if (line.getTotalPrice() != null) {
                    herbalAmounts.add(line.getTotalPrice());
                }
                continue;
            }
            ChargeReceiptChargeLineDto row = new ChargeReceiptChargeLineDto();
            row.setItemName(firstNonBlank(
                resolveChargeLineDisplayName(line, definition, product),
                OutpatientChargeConstants.PENDING_CHARGE_ITEM_DISPLAY_FALLBACK));
            row.setQuantityText(formatQuantityText(line, product));
            row.setAmountText(formatAmountText(line.getTotalPrice()));
            // 类别列：医保收费项目等级（甲类/乙类/自费），非价目 fin_type
            row.setChargeCategoryText(resolveChrgitmLvCategoryText(product));
            result.add(row);
        }
        if (herbalInsertIndex >= 0) {
            ChargeReceiptChargeLineDto herbalSummary = new ChargeReceiptChargeLineDto();
            herbalSummary.setItemName(OutpatientChargeConstants.CHARGE_RECEIPT_MERGED_TCM_FEE_ITEM_NAME);
            // 合并行不展示数量/剂量与甲乙类（多味可能不一致）
            herbalSummary.setQuantityText("");
            herbalSummary.setAmountText(formatAmountText(MonetaryAmountSupport.sumThenSettle(herbalAmounts)));
            herbalSummary.setChargeCategoryText("");
            int insertAt = herbalInsertIndex;
            if (insertAt > result.size()) {
                insertAt = result.size();
            }
            result.add(insertAt, herbalSummary);
        }
        return result;
    }

    /**
     * 收集检验带子项的父账单 id（父产品类目为 22/PROOF）。
     *
     * @param chargeItems 本笔支付关联收费项
     * @param productMap  产品读模型复合键索引
     * @return 应隐藏的检验父账单 id
     */
    private Set<Long> collectLabActivityDefinitionParentChargeIds(
        List<ChargeItem> chargeItems, Map<String, ProductDefinitionDisplayDto> productMap) {
        LinkedHashSet<Long> parentIds = new LinkedHashSet<>();
        if (chargeItems == null) {
            // 无收费项则无需收集检验父 id
            return parentIds;
        }
        Map<Long, ChargeItem> chargeById = new HashMap<>();
        for (ChargeItem line : chargeItems) {
            if (line == null || line.getId() == null) {
                // 无主键无法按 id 取父账单产品
                continue;
            }
            chargeById.put(line.getId(), line);
        }
        for (ChargeItem line : chargeItems) {
            if (line == null || line.getParentId() == null) {
                // 无 parent_id 不是子账单
                continue;
            }
            if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(line.getProductTable())) {
                // 组套等其它子账单不按检验展开
                continue;
            }
            ChargeItem parentCharge = chargeById.get(line.getParentId());
            ProductDefinitionDisplayDto parentProduct = this.resolveProductForChargeItem(parentCharge, productMap);
            if (!this.isLabProductCategory(parentProduct)) {
                // 检查/治疗父项仍打父行，不进隐藏集合
                continue;
            }
            parentIds.add(line.getParentId());
        }
        return parentIds;
    }

    /**
     * 产品类目是否为检验（code 22 / 枚举名 PROOF）。
     *
     * @param product 产品目录展示；可空
     * @return 检验为 true
     */
    private boolean isLabProductCategory(ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isEmpty(product.getCategoryCode())) {
            // 无产品或无类目则不是检验
            return false;
        }
        String categoryCode = product.getCategoryCode().trim();
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF.equals(categoryCode)) {
            // 落库类目 code 为检验 22
            return true;
        }
        if (ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_PROOF.equals(categoryCode)) {
            // 兼容枚举名 PROOF
            return true;
        }
        return false;
    }

    /**
     * 判定收费项是否为应合并进小票「中药费」的中草药明细。
     *
     * <p>优先 {@code ChargeItem.tcmFlag=true}；否则以产品目录
     * {@code medication_category_code=4}（{@link DoctorStationTcmConstants#HERBAL_CATEGORY_CODE}）识别。
     * 代煎诊疗项目非中草药分类，不纳入合并。</p>
     *
     * @param line    收费项
     * @param product 产品目录展示（可 null）
     * @return 应合并为「中药费」时 {@code true}
     */
    private boolean isHerbalTcmChargeItem(ChargeItem line, ProductDefinitionDisplayDto product) {
        if (line != null && Boolean.TRUE.equals(line.getTcmFlag())) {
            return true;
        }
        if (product == null || StringUtils.isEmpty(product.getCategoryCode())) {
            return false;
        }
        return DoctorStationTcmConstants.HERBAL_CATEGORY_CODE.equals(product.getCategoryCode().trim());
    }

    /**
     * 批量加载收费项关联产品目录（去重 composite key）。
     *
     * @param chargeItems 收费项列表
     * @return compositeKey → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductMapForChargeItems(List<ChargeItem> chargeItems) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                lookup.getProductTableName(),
                lookup.getProductDefinitionId());
            if (compositeKey == null || !dedupeKeys.add(compositeKey)) {
                continue;
            }
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 从批量产品 Map 解析单条收费项对应目录展示。
     *
     * @param row        收费项
     * @param productMap 预加载产品 Map
     * @return 产品展示 DTO；无关联时 null
     */
    private ProductDefinitionDisplayDto resolveProductForChargeItem(
        ChargeItem row,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        if (productMap == null || productMap.isEmpty() || row == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(row);
        if (lookup == null) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(),
            lookup.getProductDefinitionId());
        if (key == null) {
            return null;
        }
        return productMap.get(key);
    }

    /** 与门诊收银待收费列表口径一致：优先目录 product_name，其次定价 charge_name。 */
    private String resolveChargeLineDisplayName(
        ChargeItem row,
        ChargeItemDefinition definition,
        ProductDefinitionDisplayDto product) {
        if (product != null && StringUtils.isNotEmpty(product.getProductName())) {
            return product.getProductName().trim();
        }
        return resolveChargeItemName(definition);
    }

    /**
     * 数量与单位格式化为小票展示串（如 {@code 2 盒}）。
     *
     * @param row     收费项
     * @param product 产品目录（单位 band 解析用）
     * @return 数量展示；无量时空串
     */
    private String formatQuantityText(ChargeItem row, ProductDefinitionDisplayDto product) {
        if (row == null || row.getQuantityValue() == null) {
            return "";
        }
        String quantityPart = row.getQuantityValue().stripTrailingZeros().toPlainString();
        String unitPart = resolveQuantityUnitDisplay(row.getQuantityUnit(), product);
        if (StringUtils.isEmpty(unitPart)) {
            return quantityPart;
        }
        return quantityPart + " " + unitPart;
    }

    /**
     * 数量单位 code 转展示名（优先产品目录 unit band）。
     *
     * @param quantityUnit 单位 code
     * @param product      产品目录
     * @return 单位展示；空 code 时空串
     */
    private String resolveQuantityUnitDisplay(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (StringUtils.isEmpty(quantityUnit)) {
            return "";
        }
        String normalized = quantityUnit.trim();
        if (product != null) {
            String resolved = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayNameFromProduct(
                product,
                normalized);
            if (StringUtils.isNotEmpty(resolved)) {
                return resolved.trim();
            }
        }
        return normalized;
    }

    /**
     * 价目定义 chargeName 作为收费项名称回落。
     *
     * @param definition 价目定义
     * @return 价目名；无则 null
     */
    private String resolveChargeItemName(ChargeItemDefinition definition) {
        if (definition == null || StringUtils.isEmpty(definition.getChargeName())) {
            return null;
        }
        return definition.getChargeName().trim();
    }

    /**
     * 小票「类别」列：取产品医保收费项目等级展示（术语 CHRGITM_LV → 甲类/乙类/自费等）。
     *
     * <p>勿用价目 {@code type_code}（财务 fin_type，如 1003），否则会把财务码原样打到小票上。</p>
     *
     * @param product 产品目录展示（含已回填的 chrgitmLvText）
     * @return 等级展示；无等级时返回空串
     */
    private String resolveChrgitmLvCategoryText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return "";
        }
        if (StringUtils.isNotEmpty(product.getChrgitmLvText())) {
            return product.getChrgitmLvText().trim();
        }
        // 仅有码无文案时仍不回落财务 type_code，避免再出现 1003 类误显
        if (product.getChrgitmLv() != null) {
            return String.valueOf(product.getChrgitmLv());
        }
        return "";
    }

    /**
     * 解析小票合计：支付 displayAmount 优先，否则明细行求和。
     *
     * @param payment     支付读模型
     * @param chargeLines 已组装明细行
     * @return 合计金额
     */
    private BigDecimal resolveTotalAmount(
        PaymentReconciliationQueryModel payment,
        List<ChargeReceiptChargeLineDto> chargeLines) {
        if (payment != null && payment.getDisplayAmount() != null) {
            return payment.getDisplayAmount();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ChargeReceiptChargeLineDto row : chargeLines) {
            BigDecimal parsed = parseAmountText(row.getAmountText());
            if (parsed != null) {
                sum = sum.add(parsed);
            }
        }
        return sum;
    }

    /**
     * 按支付子明细与医保快照回填小票各渠道金额（含 totalAmount 现金回落）。
     *
     * <p>起付线取 2207 回执 {@code act_pay_dedc}；公务员待遇/补助取回执
     * {@code cvlserv_flag}/{@code cvlserv_pay}；个账余额经
     * {@link InsuranceReceiptAccountBalanceSupport}：回执 {@code balc} 为结算后原样展示，
     * 无回执则回落支付明细 {@code afterBalance}（门诊收费通常不写 afterBalance）。
     * 「现金实收」仅取本笔 {@code CASH} 渠道；仅当本笔无微信/支付宝/银行卡时，
     * 才回落本笔结算 {@code psn_cash_pay} 或挂号医保快照个人现金，避免扫码收费被挂号现金污染。
     * 展示上须与「医保个人账户」分列，禁止把微信实收叫成个人账户。</p>
     *
     * @param slip               收费小票 DTO
     * @param payment            支付读模型
     * @param insuranceSnapshot  医保平台快照
     * @param totalAmount        已算合计（渠道全空时现金回落）
     */
    private void applyPaymentBreakdown(
        ChargeReceiptSlipDto slip,
        PaymentReconciliationQueryModel payment,
        OutpatientRegistrationPlatformQueryExtensionOut insuranceSnapshot,
        BigDecimal totalAmount) {
        Map<String, BigDecimal> channelAmounts = new HashMap<>();
        BigDecimal accountBalanceFromDetail = null;
        if (payment != null && payment.getId() != null) {
            List<PaymentRecDetailQueryModel> details =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(payment.getId());
            for (PaymentRecDetailQueryModel detail : details) {
                if (detail == null || detail.getAmount() == null) {
                    continue;
                }
                String payEnum = StringUtils.trimToEmpty(detail.getPayEnum());
                if (payEnum.isEmpty()) {
                    continue;
                }
                channelAmounts.merge(payEnum, detail.getAmount(), BigDecimal::add);
                if (detail.getAfterBalance() != null
                    && PaymentChannel.INSURANCE_ACCT.getCode().equals(payEnum)) {
                    accountBalanceFromDetail = detail.getAfterBalance();
                }
            }
        }

        BigDecimal settlementActPayDedc = null;
        BigDecimal settlementBalc = null;
        String settlementCvlservFlag = null;
        BigDecimal settlementCvlservPay = null;
        BigDecimal settlementHifpPay = null;
        BigDecimal settlementOthPay = null;
        BigDecimal settlementFundPaySumamt = null;
        BigDecimal settlementPsnCashPay = null;
        BigDecimal settlementHifmiPay = null;
        BigDecimal settlementHifobPay = null;
        if (payment != null && payment.getId() != null) {
            SettlementPrintAmounts settlementAmounts = resolveSettlementPrintAmounts(payment.getId());
            settlementActPayDedc = settlementAmounts.actPayDedc;
            settlementBalc = settlementAmounts.balc;
            settlementCvlservFlag = settlementAmounts.cvlservFlag;
            settlementCvlservPay = settlementAmounts.cvlservPay;
            settlementHifpPay = settlementAmounts.hifpPay;
            settlementOthPay = settlementAmounts.othPay;
            settlementFundPaySumamt = settlementAmounts.fundPaySumamt;
            settlementPsnCashPay = settlementAmounts.psnCashPay;
            settlementHifmiPay = settlementAmounts.hifmiPay;
            settlementHifobPay = settlementAmounts.hifobPay;
        }

        BigDecimal channelFund = channelAmounts.get(PaymentChannel.INSURANCE_FUND.getCode());
        BigDecimal snapshotFund = null;
        if (insuranceSnapshot != null) {
            snapshotFund = insuranceSnapshot.getYbFundPaySumamt();
        }
        // 基金合计：优先正式回执 fund_pay_sumamt，再渠道/快照
        BigDecimal fund = firstNonNull(settlementFundPaySumamt, firstNonNull(channelFund, snapshotFund));
        // 真正统筹：hifp_pay；无回执时不拿基金总额顶替，避免与基金行重复
        BigDecimal pooling = settlementHifpPay;
        BigDecimal othPay = settlementOthPay;
        BigDecimal snapshotAcct = null;
        if (insuranceSnapshot != null) {
            snapshotAcct = insuranceSnapshot.getYbAcctPay();
        }
        BigDecimal acct = firstNonNull(
            channelAmounts.get(PaymentChannel.INSURANCE_ACCT.getCode()),
            snapshotAcct);
        // 个账余额：2207 balc 已是扣后；无 balc 才用 afterBalance
        BigDecimal accountBalance = InsuranceReceiptAccountBalanceSupport.resolveReceiptAccountBalance(
            settlementBalc,
            accountBalanceFromDetail);
        BigDecimal snapshotCash = null;
        if (insuranceSnapshot != null) {
            snapshotCash = insuranceSnapshot.getYbPsnCashPay();
        }
        BigDecimal cash = channelAmounts.get(PaymentChannel.CASH.getCode());
        BigDecimal wechat = channelAmounts.get(PaymentChannel.WECHAT.getCode());
        BigDecimal alipay = channelAmounts.get(PaymentChannel.ALIPAY.getCode());
        BigDecimal bankCard = channelAmounts.get(PaymentChannel.BANK_CARD.getCode());
        // 本笔已有微信/支付宝/银行卡时，禁止用挂号医保快照个人现金污染「现金实收」
        // （典型误显：收费扫码 10 元，小票却带出挂号个人现金 5 元）
        if (cash == null && wechat == null && alipay == null && bankCard == null) {
            cash = firstNonNull(settlementPsnCashPay, snapshotCash);
        }

        if (cash == null && wechat == null && alipay == null && bankCard == null
            && fund == null && acct == null && totalAmount != null) {
            cash = totalAmount;
        }
        if (bankCard != null) {
            if (wechat == null) {
                wechat = bankCard;
            } else {
                wechat = wechat.add(bankCard);
            }
        }

        slip.setPoolingPayText(formatAmountText(pooling));
        slip.setFundPayText(formatAmountText(fund));
        slip.setOthPayText(formatAmountText(othPay));
        slip.setAccountPayText(formatAmountText(acct));
        slip.setDeductibleLineText(formatAmountText(settlementActPayDedc));
        // 公务员待遇/补助：仅小票展示透传，不参与渠道金额合计
        slip.setCvlservFlagText(formatCvlservFlagText(settlementCvlservFlag));
        slip.setCvlservPayText(formatAmountText(settlementCvlservPay));
        slip.setCashPayText(formatAmountText(cash));
        slip.setWechatPayText(formatAmountText(wechat));
        slip.setAlipayPayText(formatAmountText(alipay));
        slip.setAccountBalanceText(formatAmountText(accountBalance));
        // 针式发票：医保支付=基金总额；现金支付=个人自付渠道合计（银行卡已并入微信变量）
        slip.setInsurancePayText(formatInvoiceMoney(fund));
        slip.setInvoiceCashPayText(formatInvoiceMoney(sumSelfPay(cash, wechat, alipay)));
        slip.setEmployeeCriticalFeeText(formatInvoiceMoney(settlementHifobPay));
        slip.setResidentCriticalFeeText(formatInvoiceMoney(settlementHifmiPay));
        BigDecimal originalBalance = null;
        if (accountBalance != null) {
            originalBalance = accountBalance;
            if (acct != null) {
                originalBalance = accountBalance.add(acct);
            }
        }
        slip.setOriginalAccountBalanceText(formatInvoiceMoney(originalBalance));
    }

    /**
     * 从支付单下未撤销的 2207 正式结算回执汇总小票起付线、公务员字段与个账余额原值。
     *
     * <p>多笔时 {@code act_pay_dedc}/{@code cvlserv_pay} 求和；{@code cvlserv_flag}
     * 取首笔非空；{@code balc} 取最后一笔非空值（平台原值/结算后，展示见
     * {@link InsuranceReceiptAccountBalanceSupport}）。</p>
     *
     * @param paymentId 支付主键
     * @return 起付线/公务员/余额；扩展未启用或无回执时字段为 null
     */
    private SettlementPrintAmounts resolveSettlementPrintAmounts(Long paymentId) {
        SettlementPrintAmounts amounts = new SettlementPrintAmounts();
        if (paymentId == null || !outpatientChargeInsuranceExtension.isEnabled()) {
            return amounts;
        }
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            outpatientChargeInsuranceExtension.listSettlementsByPayment(paymentId);
        if (records == null || records.isEmpty()) {
            return amounts;
        }
        for (OutpatientChargeSettlementRecordExtensionOut record : records) {
            if (record == null || "REVOKED".equals(StringUtils.trimToEmpty(record.getStatus()))) {
                continue;
            }
            amounts.actPayDedc = sumNullable(amounts.actPayDedc, record.getActPayDedc());
            amounts.cvlservPay = sumNullable(amounts.cvlservPay, record.getCvlservPay());
            amounts.hifpPay = sumNullable(amounts.hifpPay, record.getHifpPay());
            amounts.othPay = sumNullable(amounts.othPay, record.getOthPay());
            amounts.fundPaySumamt = sumNullable(amounts.fundPaySumamt, record.getFundPaySumamt());
            amounts.psnCashPay = sumNullable(amounts.psnCashPay, record.getPsnCashPay());
            amounts.hifmiPay = sumNullable(amounts.hifmiPay, record.getHifmiPay());
            amounts.hifobPay = sumNullable(amounts.hifobPay, record.getHifobPay());
            // 公务员标志多笔时保留首笔非空
            if (amounts.cvlservFlag == null) {
                String flag = StringUtils.trimToNull(record.getCvlservFlag());
                if (flag != null) {
                    amounts.cvlservFlag = flag;
                }
            }
            if (record.getBalc() != null) {
                amounts.balc = record.getBalc();
            }
        }
        return amounts;
    }

    /**
     * 公务员标志 → 小票展示文案。
     *
     * <p>仅认平台字典：{@code 1}→是 / {@code 0}→否；空或非 0/1 一律空串，禁止臆造。</p>
     *
     * @param flag 平台 {@code cvlserv_flag}
     * @return 展示文案
     */
    private static String formatCvlservFlagText(String flag) {
        String value = StringUtils.trimToEmpty(flag);
        if ("1".equals(value)) {
            return "是";
        }
        if ("0".equals(value)) {
            return "否";
        }
        return "";
    }

    /** 小票用结算回执字段（起付线 + 公务员 + 统筹/其他分项 + 个人现金 + 个账余额）。 */
    private static final class SettlementPrintAmounts {
        private BigDecimal actPayDedc;
        private String cvlservFlag;
        private BigDecimal cvlservPay;
        private BigDecimal hifpPay;
        private BigDecimal othPay;
        private BigDecimal fundPaySumamt;
        private BigDecimal psnCashPay;
        private BigDecimal hifmiPay;
        private BigDecimal hifobPay;
        private BigDecimal balc;
    }

    /**
     * 加载就诊医保平台快照（扩展未启用时 null）。
     *
     * @param encounterId 就诊主键
     * @return 首条快照；无则 null
     */
    private OutpatientRegistrationPlatformQueryExtensionOut resolveInsuranceSnapshot(Long encounterId) {
        if (!outpatientRegistrationInsuranceExtension.isEnabled()) {
            return null;
        }
        List<OutpatientRegistrationPlatformQueryExtensionOut> snapshots =
            outpatientRegistrationInsuranceExtension.findPlatformSnapshots(
                new OutpatientRegistrationPlatformQueryExtensionIn()
                    .setEncounterIds(List.of(encounterId))
                    .setPaymentDraftIdByEncounter(Map.of()));
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        return snapshots.get(0);
    }

    /**
     * 由收费项账户或就诊自费账户解析费用性质（合同名称）。
     *
     * @param chargeItems  收费项（优先取行内 account）
     * @param encounterId  就诊（回落 self account）
     * @return 合同名称；无则 null
     */
    private String resolveFeeNatureText(List<ChargeItem> chargeItems, Long encounterId) {
        if (chargeItems != null && !chargeItems.isEmpty()) {
            LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
            for (ChargeItem line : chargeItems) {
                if (line != null && line.getAccountId() != null) {
                    accountIds.add(line.getAccountId());
                }
            }
            Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
            for (ChargeItem line : chargeItems) {
                if (line == null || line.getAccountId() == null) {
                    continue;
                }
                Account account = accountById.get(line.getAccountId());
                if (account != null && StringUtils.isNotEmpty(account.getContractNo())) {
                    String name = contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
                        .map(Contract::getName)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .orElse(null);
                    if (name != null) {
                        return name;
                    }
                }
            }
        }
        Account account = accountQueryService.getSelfAccount(encounterId);
        if (account == null || StringUtils.isEmpty(account.getContractNo())) {
            return null;
        }
        return contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .map(Contract::getName)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .orElse(null);
    }

    /**
     * 解析收费员（录入人）姓名。
     *
     * @param entererId 支付录入人 practitionerId
     * @return 姓名；无则 null
     */
    private String resolveCashierName(Long entererId) {
        if (entererId == null) {
            return null;
        }
        Practitioner practitioner = practitionerQueryService.findById(entererId);
        if (practitioner == null || StringUtils.isEmpty(practitioner.getName())) {
            return null;
        }
        return practitioner.getName().trim();
    }

    /**
     * 从组织树取首个有效医院机构名作为小票抬头。
     *
     * @return 医院名称；未配置时 null
     */
    private String resolveHospitalName() {
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum())) {
                continue;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(organization.getTypeEnum())) {
                String name = StringUtils.trimToNull(organization.getName());
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * 解析 payment.chargeItemIds 逗号分隔串为 Long 列表。
     *
     * @param rawChargeItemIds 原始 id 串
     * @return id 列表；空串时空列表
     */
    private static List<Long> parseChargeItemIds(String rawChargeItemIds) {
        if (StringUtils.isEmpty(rawChargeItemIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String segment : rawChargeItemIds.split(",")) {
            String trimmed = StringUtils.trimToNull(segment);
            if (trimmed == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法片段
            }
        }
        return ids;
    }

    /**
     * 患者性别枚举转小票展示文案。
     *
     * @param context 门诊就诊上下文
     * @return 性别展示；无上下文时 null
     */
    private static String resolveGenderText(OutpatientEncounterContextQueryModel context) {
        if (context == null) {
            return null;
        }
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, context.getGenderEnum());
        if (gender != null) {
            return gender.getDisplay();
        }
        return StringUtils.trimToNull(context.getGenderEnum());
    }

    /**
     * 收费时间格式化为小票展示串。
     *
     * @param billDate 支付 bill_date
     * @return 格式化时间；null 时空串
     */
    private static String formatChargeTime(LocalDateTime billDate) {
        if (billDate == null) {
            return "";
        }
        return GeneralSharedConstants.DATE_TIME_FORMATTER.format(billDate);
    }

    /**
     * 金额格式化为小票展示串。
     *
     * @param amount 金额
     * @return 展示串；null 时空串
     */
    private static String formatAmountText(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return MonetaryAmountSupport.scaleDisplay(amount).toPlainString();
    }

    /**
     * 解析小票金额展示串为 BigDecimal。
     *
     * @param amountText 金额文本
     * @return 解析结果；空或非法时 null
     */
    private static BigDecimal parseAmountText(String amountText) {
        if (StringUtils.isEmpty(amountText)) {
            return null;
        }
        try {
            return new BigDecimal(amountText.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 两值非 null 者优先（渠道与医保快照回落）。
     *
     * @param first  优先值
     * @param second 回落值
     * @return first 非 null 则 first，否则 second
     */
    private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    /**
     * 回填针式发票抬头、费用分栏与总费用大写。
     *
     * <p>分栏按本笔支付关联收费项（跳过子账单，避免与父行重复）。无金额的分栏留空，
     * 支付类金额无值时打 0.00。未归入固定栏的金额进入「耗材费」。
     * CT/DR/核磁等影像检查归入检查费（与目录类目「检查」一致），不进耗材费。</p>
     *
     * @param slip         小票 DTO
     * @param context      门诊就诊上下文，可空
     * @param payment      支付读模型，可空
     * @param chargeItems  本笔收费项
     * @param totalAmount  支付合计回落
     * @param encounterId  就诊主键
     */
    private void fillInvoiceSheet(
        ChargeReceiptSlipDto slip,
        OutpatientEncounterContextQueryModel context,
        PaymentReconciliationQueryModel payment,
        List<ChargeItem> chargeItems,
        BigDecimal totalAmount,
        Long encounterId) {
        String encounterBusNo = "";
        String departmentName = "";
        String doctorName = "";
        if (context != null) {
            encounterBusNo = firstNonBlank(context.getEncounterBusNo(), "");
            departmentName = firstNonBlank(context.getOrganizationName(), "");
            doctorName = firstNonBlank(context.getDoctorName(), "");
        }
        slip.setEncounterBusNo(encounterBusNo);
        slip.setDepartmentName(departmentName);
        slip.setDoctorName(doctorName);
        // 挂号时间取就诊开始时刻（挂号落库写入 startTime）
        String registerTimeText = "";
        if (context != null && context.getStartTime() != null) {
            registerTimeText = formatChargeTime(context.getStartTime());
        }
        slip.setRegisterTimeText(registerTimeText);
        slip.setPrescriptionNoText(joinPrescriptionNos(chargeItems));
        String paymentNo = "";
        String chargeDate = "";
        if (payment != null) {
            paymentNo = firstNonBlank(payment.getPaymentNo(), "");
            if (payment.getBillDate() != null) {
                chargeDate = INVOICE_DATE_FORMATTER.format(payment.getBillDate());
            }
        }
        slip.setPaymentNoText(paymentNo);
        slip.setChargeDateText(chargeDate);
        slip.setInsuranceNoText(resolveInsuranceNo(encounterId));

        InvoiceFeeBuckets buckets = this.sumInvoiceFeeBuckets(chargeItems);
        slip.setWesternMedicineFeeText(formatInvoiceFeeSlot(buckets.western));
        slip.setExamFeeText(formatInvoiceFeeSlot(buckets.exam));
        slip.setChinesePatentFeeText(formatInvoiceFeeSlot(buckets.patent));
        slip.setSurgeryFeeText(formatInvoiceFeeSlot(buckets.surgery));
        slip.setHerbalFeeText(formatInvoiceFeeSlot(buckets.herbal));
        slip.setOxygenFeeText(formatInvoiceFeeSlot(buckets.oxygen));
        slip.setLabFeeText(formatInvoiceFeeSlot(buckets.lab));
        slip.setBloodFeeText(formatInvoiceFeeSlot(buckets.blood));
        slip.setXrayFeeText(formatInvoiceFeeSlot(buckets.xray));
        slip.setDisposeFeeText(formatInvoiceFeeSlot(buckets.dispose));
        slip.setElectroFeeText(formatInvoiceFeeSlot(buckets.electro));
        slip.setInjectionFeeText(formatInvoiceFeeSlot(buckets.injection));
        slip.setTherapyFeeText(formatInvoiceFeeSlot(buckets.therapy));
        slip.setMaterialFeeText(formatInvoiceFeeSlot(buckets.material));

        BigDecimal feeSum = buckets.sum();
        BigDecimal chineseBase = feeSum;
        if (feeSum.compareTo(BigDecimal.ZERO) == 0 && totalAmount != null) {
            chineseBase = totalAmount;
        }
        slip.setTotalAmountChineseText(formatChineseMoney(chineseBase));
    }

    /**
     * 组装门诊患者费用清单打印字段（A4 横版；慢病切换标题与就诊类型）。
     *
     * <p>明细不合并中草药；子账单跳过。开单医生优先 Request.requester_id，回落接诊医生。</p>
     *
     * @param slip        输出 DTO
     * @param context     门诊就诊上下文，可空
     * @param chargeItems 本笔收费项
     */
    private void fillFeeListSheet(
        ChargeReceiptSlipDto slip,
        OutpatientEncounterContextQueryModel context,
        List<ChargeItem> chargeItems) {
        if (slip == null) {
            return;
        }
        String hospitalName = firstNonBlank(slip.getHospitalName(), "");
        if (StringUtils.isEmpty(hospitalName)) {
            hospitalName = firstNonBlank(resolveHospitalName(), "");
        }
        slip.setOrganizationLabel("机构：" + hospitalName);

        String encounterBusNo = firstNonBlank(slip.getEncounterBusNo(), "");
        String patientName = firstNonBlank(slip.getPatientName(), "");
        String departmentName = firstNonBlank(slip.getDepartmentName(), "");
        String fallbackDoctorName = firstNonBlank(slip.getDoctorName(), "");
        if (context != null) {
            if (StringUtils.isEmpty(encounterBusNo)) {
                encounterBusNo = firstNonBlank(context.getEncounterBusNo(), "");
            }
            if (StringUtils.isEmpty(patientName)) {
                patientName = firstNonBlank(context.getPatientName(), "");
            }
            if (StringUtils.isEmpty(departmentName)) {
                departmentName = firstNonBlank(context.getOrganizationName(), "");
            }
            if (StringUtils.isEmpty(fallbackDoctorName)) {
                fallbackDoctorName = firstNonBlank(context.getDoctorName(), "");
            }
        }

        List<ChargeItem> printableItems = this.filterParentChargeItems(chargeItems);
        Map<Long, String> medTypeByDxId = this.mapMedTypeByChargeItems(printableItems);
        boolean chronicVisit = this.isChronicFeeListVisit(printableItems, medTypeByDxId);
        if (chronicVisit) {
            slip.setDocumentTitle(FEE_LIST_TITLE_CHRONIC);
        } else {
            slip.setDocumentTitle(FEE_LIST_TITLE_GENERAL);
        }
        String defaultVisitTypeText;
        if (chronicVisit) {
            defaultVisitTypeText = VISIT_TYPE_CHRONIC;
        } else {
            defaultVisitTypeText = VISIT_TYPE_GENERAL;
        }

        Map<Long, ChargeItemDefinition> definitionById = this.loadDefinitionMap(printableItems);
        Map<String, ProductDefinitionDisplayDto> productMap = this.loadProductMapForChargeItems(printableItems);
        Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> orderingAnchorByChargeItemId =
            chargeItemOrderingDoctorFromRequestSupport.mapOrderingDoctorAnchorByChargeItemId(printableItems);
        Map<Long, String> doctorNameByPractitionerId =
            this.mapOrderingDoctorNames(orderingAnchorByChargeItemId);

        List<ChargeFeeListRowDto> rows = new ArrayList<>();
        List<BigDecimal> amountParts = new ArrayList<>();
        for (ChargeItem line : printableItems) {
            if (line == null) {
                continue;
            }
            ChargeItemDefinition definition = definitionById.get(line.getDefinitionId());
            ProductDefinitionDisplayDto product = resolveProductForChargeItem(line, productMap);
            ChargeFeeListRowDto row = new ChargeFeeListRowDto();
            row.setEncounterBusNo(encounterBusNo);
            row.setPatientName(patientName);
            row.setDepartmentName(departmentName);
            row.setOrderingDoctorName(
                this.resolveFeeListOrderingDoctorName(
                    line.getId(), orderingAnchorByChargeItemId, doctorNameByPractitionerId, fallbackDoctorName));
            row.setItemName(firstNonBlank(
                resolveChargeLineDisplayName(line, definition, product),
                OutpatientChargeConstants.PENDING_CHARGE_ITEM_DISPLAY_FALLBACK));
            row.setUnitPriceText(formatAmountText(line.getUnitPrice()));
            row.setQuantityText(this.formatFeeListQuantityText(line, product));
            row.setAmountText(formatAmountText(line.getTotalPrice()));
            row.setInsuranceLevelText(this.formatFeeListInsuranceLevelText(product));
            row.setVisitTypeText(
                this.resolveFeeListVisitTypeText(line.getEncounterDiagnosisIds(), medTypeByDxId, defaultVisitTypeText));
            row.setItemTypeText(this.resolveFeeListItemTypeText(product, definition));
            row.setTimeText(this.resolveFeeListTimeText(line, orderingAnchorByChargeItemId));
            rows.add(row);
            if (line.getTotalPrice() != null) {
                amountParts.add(line.getTotalPrice());
            }
        }
        String totalText = formatAmountText(MonetaryAmountSupport.sumThenSettle(amountParts));
        // 末行合计：随表格分页落在末页，避免固定坐标被明细撑破
        if (!rows.isEmpty()) {
            ChargeFeeListRowDto totalRow = new ChargeFeeListRowDto();
            totalRow.setEncounterBusNo("合计");
            totalRow.setPatientName("");
            totalRow.setDepartmentName("");
            totalRow.setOrderingDoctorName("");
            totalRow.setItemName("");
            totalRow.setUnitPriceText("");
            totalRow.setQuantityText("");
            totalRow.setAmountText(totalText);
            totalRow.setInsuranceLevelText("");
            totalRow.setVisitTypeText("");
            totalRow.setItemTypeText("");
            totalRow.setTimeText("");
            rows.add(totalRow);
        }
        slip.setFeeListRows(rows);
        slip.setFeeListTotalAmountText(totalText);
        // 小票抬头：开方科室/开方医生复用本方法已解析的开立人，避免再查一遍 Request
        slip.setOrderingDepartmentName(
            this.resolveOrderingDepartmentText(printableItems, departmentName));
        slip.setOrderingDoctorName(
            this.resolveOrderingDoctorText(
                orderingAnchorByChargeItemId, doctorNameByPractitionerId, fallbackDoctorName));
    }

    /**
     * 小票开方科室：父收费项开立机构去重后顿号连接，没有开立机构时回落就诊科室。
     *
     * @param printableItems         父收费项
     * @param fallbackDepartmentName 就诊科室
     * @return 展示文案
     */
    private String resolveOrderingDepartmentText(
        List<ChargeItem> printableItems,
        String fallbackDepartmentName) {
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        if (printableItems != null) {
            for (ChargeItem line : printableItems) {
                if (line == null) {
                    continue;
                }
                Long orgId = line.getOrgId();
                if (orgId == null) {
                    // 开立机构空时回落申请机构
                    orgId = line.getRequestingOrgId();
                }
                if (orgId != null) {
                    orgIds.add(orgId);
                }
            }
        }
        if (orgIds.isEmpty()) {
            return firstNonBlank(fallbackDepartmentName, "");
        }
        Map<Long, String> nameById = this.organizationQueryService.mapNamesByIds(orgIds);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Long orgId : orgIds) {
            String name = null;
            if (nameById != null) {
                name = nameById.get(orgId);
            }
            if (StringUtils.isNotEmpty(name)) {
                names.add(name.trim());
            }
        }
        if (names.isEmpty()) {
            return firstNonBlank(fallbackDepartmentName, "");
        }
        return joinWithDunhao(names);
    }

    /**
     * 小票开方医生：医生开立 Request 的开立人去重后顿号连接，没有开立人时回落接诊医生。
     *
     * @param orderingAnchorByChargeItemId 开单锚点
     * @param doctorNameByPractitionerId   医生名索引
     * @param fallbackDoctorName           接诊医生
     * @return 展示文案
     */
    private String resolveOrderingDoctorText(
        Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> orderingAnchorByChargeItemId,
        Map<Long, String> doctorNameByPractitionerId,
        String fallbackDoctorName) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (orderingAnchorByChargeItemId != null) {
            for (ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor anchor
                : orderingAnchorByChargeItemId.values()) {
                if (anchor == null || anchor.requesterId() == null) {
                    continue;
                }
                String name = null;
                if (doctorNameByPractitionerId != null) {
                    name = doctorNameByPractitionerId.get(anchor.requesterId());
                }
                if (StringUtils.isNotEmpty(name)) {
                    names.add(name.trim());
                }
            }
        }
        if (names.isEmpty()) {
            return firstNonBlank(fallbackDoctorName, "");
        }
        return joinWithDunhao(names);
    }

    /**
     * 去重后的展示名以顿号连接。
     *
     * @param names 已去重的名称
     * @return 连接文案；空集合时空串
     */
    private static String joinWithDunhao(LinkedHashSet<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(name);
        }
        return builder.toString();
    }

    /**
     * 费用清单仅打父账单（跳过子项）。
     *
     * @param chargeItems 本笔收费项
     * @return 父行列表
     */
    private List<ChargeItem> filterParentChargeItems(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return List.of();
        }
        List<ChargeItem> result = new ArrayList<>();
        for (ChargeItem line : chargeItems) {
            if (line == null) {
                continue;
            }
            if (line.getParentId() != null) {
                continue;
            }
            result.add(line);
        }
        return result;
    }

    /**
     * 批量解析收费行绑定诊断的医保医疗类别。
     *
     * @param chargeItems 收费行
     * @return 就诊诊断主键 → med_type code
     */
    private Map<Long, String> mapMedTypeByChargeItems(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> diagnosisIds = new LinkedHashSet<>();
        for (ChargeItem line : chargeItems) {
            if (line == null) {
                continue;
            }
            Long dxId = EncounterDiagnosisIdsSupport.firstId(line.getEncounterDiagnosisIds());
            if (dxId != null) {
                diagnosisIds.add(dxId);
            }
        }
        if (diagnosisIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> mapped = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(diagnosisIds);
        if (mapped == null) {
            return Map.of();
        }
        return mapped;
    }

    /**
     * 本笔费用清单是否按慢病号标题打印。
     *
     * @param chargeItems   父账单行
     * @param medTypeByDxId 诊断 → med_type
     * @return true=慢病系
     */
    private boolean isChronicFeeListVisit(List<ChargeItem> chargeItems, Map<Long, String> medTypeByDxId) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return false;
        }
        for (ChargeItem line : chargeItems) {
            if (line == null) {
                continue;
            }
            Long dxId = EncounterDiagnosisIdsSupport.firstId(line.getEncounterDiagnosisIds());
            String medTypeCode = null;
            if (dxId != null && medTypeByDxId != null) {
                medTypeCode = medTypeByDxId.get(dxId);
            }
            if (isChronicMedTypeCode(medTypeCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否慢病系医保医疗类别（门诊慢特病 / 门诊慢病 / 门诊特病）。
     *
     * @param medTypeCode YbMedType code
     * @return true=慢病系
     */
    private static boolean isChronicMedTypeCode(String medTypeCode) {
        String code = StringUtils.trimToNull(medTypeCode);
        if (code == null) {
            return false;
        }
        if (YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        if (YbMedType.CHRONIC_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        if (YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        return false;
    }

    /**
     * 费用清单行就诊类型文案。
     *
     * @param encounterDiagnosisIds 绑定诊断 ids 串
     * @param medTypeByDxId         诊断 → med_type
     * @param defaultVisitTypeText  整单回落（普通门诊 / 门诊慢病）
     * @return 展示文案
     */
    private String resolveFeeListVisitTypeText(
        String encounterDiagnosisIds,
        Map<Long, String> medTypeByDxId,
        String defaultVisitTypeText) {
        Long dxId = EncounterDiagnosisIdsSupport.firstId(encounterDiagnosisIds);
        if (dxId == null || medTypeByDxId == null) {
            return firstNonBlank(defaultVisitTypeText, VISIT_TYPE_GENERAL);
        }
        String medTypeCode = medTypeByDxId.get(dxId);
        if (StringUtils.isEmpty(medTypeCode)) {
            return firstNonBlank(defaultVisitTypeText, VISIT_TYPE_GENERAL);
        }
        YbMedType medTypeEnum = EnumUtil.fromCode(YbMedType.class, medTypeCode.trim());
        if (medTypeEnum != null) {
            return medTypeEnum.getDisplay();
        }
        return medTypeCode.trim();
    }

    /**
     * 批量加载价目定义。
     *
     * @param chargeItems 收费行
     * @return definitionId → 定义
     */
    private Map<Long, ChargeItemDefinition> loadDefinitionMap(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem line : chargeItems) {
            if (line != null && line.getDefinitionId() != null) {
                definitionIds.add(line.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        return chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(new ArrayList<>(definitionIds));
    }

    /**
     * 开单医生姓名索引。
     *
     * @param orderingAnchorByChargeItemId chargeItemId → 开单锚点
     * @return practitionerId → 展示名
     */
    private Map<Long, String> mapOrderingDoctorNames(
        Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> orderingAnchorByChargeItemId) {
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        if (orderingAnchorByChargeItemId != null) {
            for (ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor anchor
                : orderingAnchorByChargeItemId.values()) {
                if (anchor != null && anchor.requesterId() != null) {
                    practitionerIds.add(anchor.requesterId());
                }
            }
        }
        if (practitionerIds.isEmpty()) {
            return Map.of();
        }
        return practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
    }

    /**
     * 解析费用清单开单医生名：Request 开立人优先，否则接诊医生。
     *
     * @param chargeItemId                 收费行主键
     * @param orderingAnchorByChargeItemId 开单锚点
     * @param doctorNameByPractitionerId   医生名索引
     * @param fallbackDoctorName           接诊医生
     * @return 展示名
     */
    private String resolveFeeListOrderingDoctorName(
        Long chargeItemId,
        Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> orderingAnchorByChargeItemId,
        Map<Long, String> doctorNameByPractitionerId,
        String fallbackDoctorName) {
        if (chargeItemId != null && orderingAnchorByChargeItemId != null) {
            ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor anchor =
                orderingAnchorByChargeItemId.get(chargeItemId);
            if (anchor != null && anchor.requesterId() != null && doctorNameByPractitionerId != null) {
                String name = doctorNameByPractitionerId.get(anchor.requesterId());
                if (StringUtils.isNotEmpty(name)) {
                    return name.trim();
                }
            }
        }
        return firstNonBlank(fallbackDoctorName, "");
    }

    /**
     * 费用清单数量：数值两位小数 + 单位（无空格，如 1.00盒）。
     *
     * @param row     收费项
     * @param product 产品目录
     * @return 数量展示
     */
    private String formatFeeListQuantityText(ChargeItem row, ProductDefinitionDisplayDto product) {
        if (row == null || row.getQuantityValue() == null) {
            return "";
        }
        String quantityPart = MonetaryAmountSupport.scaleDisplay(row.getQuantityValue()).toPlainString();
        String unitPart = resolveQuantityUnitDisplay(row.getQuantityUnit(), product);
        if (StringUtils.isEmpty(unitPart)) {
            return quantityPart;
        }
        return quantityPart + unitPart;
    }

    /**
     * 费用清单医保等级：仅打字典文案（甲类 / 乙类 / 丙类 / 自费），不拼等级码。
     *
     * @param product 产品目录
     * @return 等级展示
     */
    private String formatFeeListInsuranceLevelText(ProductDefinitionDisplayDto product) {
        return resolveChrgitmLvCategoryText(product);
    }

    /**
     * 费用清单项目类型：目录分类文案，回落价目 type_code / ChargeTypeCategory。
     *
     * @param product    产品目录
     * @param definition 价目定义
     * @return 项目类型展示
     */
    private String resolveFeeListItemTypeText(
        ProductDefinitionDisplayDto product,
        ChargeItemDefinition definition) {
        if (product != null && StringUtils.isNotEmpty(product.getCategoryCodeDictText())) {
            return product.getCategoryCodeDictText().trim();
        }
        if (product != null && StringUtils.isNotEmpty(product.getCategoryCode())) {
            ChargeTypeCategory category = EnumUtil.fromCode(ChargeTypeCategory.class, product.getCategoryCode().trim());
            if (category != null) {
                return category.getDisplay();
            }
            return product.getCategoryCode().trim();
        }
        if (definition != null && StringUtils.isNotEmpty(definition.getTypeCode())) {
            String code = definition.getTypeCode().trim();
            ChargeTypeCategory category = EnumUtil.fromCode(ChargeTypeCategory.class, code);
            if (category != null) {
                return category.getDisplay();
            }
            return code;
        }
        return "";
    }

    /**
     * 费用清单时间：优先 Request 开立时间，否则 occurrence_time / entered_date。
     *
     * @param line                         收费项
     * @param orderingAnchorByChargeItemId 开单锚点
     * @return 时间展示
     */
    private String resolveFeeListTimeText(
        ChargeItem line,
        Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> orderingAnchorByChargeItemId) {
        if (line == null) {
            return "";
        }
        if (line.getId() != null && orderingAnchorByChargeItemId != null) {
            ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor anchor =
                orderingAnchorByChargeItemId.get(line.getId());
            if (anchor != null && anchor.openedTime() != null) {
                return formatChargeTime(anchor.openedTime());
            }
        }
        if (line.getOccurrenceTime() != null) {
            return formatChargeTime(line.getOccurrenceTime());
        }
        if (line.getEnteredDate() != null) {
            return formatChargeTime(line.getEnteredDate());
        }
        return "";
    }

    /**
     * 本笔收费项按发票固定栏汇总。
     *
     * @param chargeItems 收费项
     * @return 各栏金额（未出现的栏为 0）
     */
    private InvoiceFeeBuckets sumInvoiceFeeBuckets(List<ChargeItem> chargeItems) {
        InvoiceFeeBuckets buckets = new InvoiceFeeBuckets();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return buckets;
        }
        Map<String, ProductDefinitionDisplayDto> productMap = this.loadProductMapForChargeItems(chargeItems);
        for (ChargeItem line : chargeItems) {
            if (line == null || line.getParentId() != null) {
                // 子账单金额已含在父行，发票不重复计入
                continue;
            }
            BigDecimal amount = resolveInvoiceLineAmount(line);
            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(line, productMap);
            String slot = resolveInvoiceFeeSlot(line, product);
            buckets.add(slot, amount);
        }
        return buckets;
    }

    /**
     * 发票行金额：有结算折扣快照时取折后应收，否则取原价。
     *
     * @param line 收费项
     * @return 计入发票分栏的金额
     */
    private static BigDecimal resolveInvoiceLineAmount(ChargeItem line) {
        if (line.getSettleDiscountRate() != null && line.getDiscountAmount() != null) {
            return line.getDiscountAmount();
        }
        return line.getTotalPrice();
    }

    /**
     * 将一条收费项归入发票固定栏。
     *
     * <p>药品只按目录分类（避免「注射用」药名误入注射费）。诊疗优先类目码（检查/检验等），
     * 再按项目名称关键字。CT/DR/核磁/磁共振与类目「检查」一致，归入检查费，不进耗材费。
     * 无法识别的金额归入耗材费，避免总费用对不上。</p>
     *
     * @param line    收费项
     * @param product 目录展示，可空
     * @return 栏位码
     */
    private static String resolveInvoiceFeeSlot(ChargeItem line, ProductDefinitionDisplayDto product) {
        String productTable = "";
        if (line.getProductTable() != null) {
            productTable = line.getProductTable().trim();
        }
        String categoryCode = "";
        String name = "";
        if (product != null) {
            if (product.getCategoryCode() != null) {
                categoryCode = product.getCategoryCode().trim();
            }
            name = joinName(product.getProductName(), product.getCategoryCodeDictText());
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            if (MEDICATION_CATEGORY_PATENT.equals(categoryCode) || name.contains("中成药")) {
                return InvoiceFeeBuckets.PATENT;
            }
            if (DoctorStationTcmConstants.HERBAL_CATEGORY_CODE.equals(categoryCode)
                || name.contains("中草药")
                || name.contains("中药饮片")) {
                return InvoiceFeeBuckets.HERBAL;
            }
            return InvoiceFeeBuckets.WESTERN;
        }
        if (name.contains("代煎") || name.contains("中药费")) {
            return InvoiceFeeBuckets.HERBAL;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable) || "11".equals(categoryCode)) {
            return InvoiceFeeBuckets.MATERIAL;
        }
        if (name.contains("输氧") || name.contains("吸氧")) {
            return InvoiceFeeBuckets.OXYGEN;
        }
        if (name.contains("输血")) {
            return InvoiceFeeBuckets.BLOOD;
        }
        if (name.contains("心电") || name.contains("脑电") || name.contains("电诊")) {
            return InvoiceFeeBuckets.ELECTRO;
        }
        if (containsInjectionService(name)) {
            return InvoiceFeeBuckets.INJECTION;
        }
        if (name.contains("处置") || name.contains("换药")) {
            return InvoiceFeeBuckets.DISPOSE;
        }
        if (name.contains("X光") || name.contains("X线") || name.contains("放射") || name.contains("拍片")) {
            return InvoiceFeeBuckets.XRAY;
        }
        // 检验类目优先于名称关键字
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF.equals(categoryCode)
            || ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_PROOF.equals(categoryCode)
            || name.contains("化验")
            || name.contains("检验")) {
            return InvoiceFeeBuckets.LAB;
        }
        // 检查类目（含头CT/关节CT 等）：与票面「检查费」对齐，禁止再进耗材
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST.equals(categoryCode)
            || ServiceRequestCategoryConstants.CATEGORY_ENUM_NAME_TEST.equals(categoryCode)
            || name.contains("检查")) {
            return InvoiceFeeBuckets.EXAM;
        }
        // 无类目时：CT/DR/核磁仍按检查费，避免误入耗材
        if (name.contains("CT") || name.contains("DR") || name.contains("核磁") || name.contains("磁共振")) {
            return InvoiceFeeBuckets.EXAM;
        }
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryCode)
            || ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryCode)
            || name.contains("手术")) {
            return InvoiceFeeBuckets.SURGERY;
        }
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_TREATMENT.equals(categoryCode)
            || ServiceRequestCategoryConstants.SERVICE_CATEGORY_CONSULTATION.equals(categoryCode)
            || name.contains("治疗")
            || name.contains("诊查")) {
            return InvoiceFeeBuckets.THERAPY;
        }
        if (MEDICATION_CATEGORY_WESTERN.equals(categoryCode) || name.contains("西药")) {
            return InvoiceFeeBuckets.WESTERN;
        }
        return InvoiceFeeBuckets.MATERIAL;
    }

    /**
     * 注射费只认诊疗名称，排除「注射用××」药品名。
     *
     * @param name 项目名称与类目文案
     * @return 是注射服务为 true
     */
    private static boolean containsInjectionService(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!name.contains("注射")) {
            return false;
        }
        return !name.contains("注射用");
    }

    /**
     * 拼接名称检索串。
     *
     * @param left  产品名
     * @param right 类目文案
     * @return 非空片段用空格连接
     */
    private static String joinName(String left, String right) {
        StringBuilder builder = new StringBuilder();
        if (left != null && !left.trim().isEmpty()) {
            builder.append(left.trim());
        }
        if (right != null && !right.trim().isEmpty()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(right.trim());
        }
        return builder.toString();
    }

    /**
     * 收集本笔处方号，保持首次出现顺序。
     *
     * @param chargeItems 收费项
     * @return 顿号连接；无则空串
     */
    private static String joinPrescriptionNos(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> nos = new LinkedHashSet<>();
        for (ChargeItem line : chargeItems) {
            if (line == null || line.getParentId() != null) {
                continue;
            }
            String prescriptionNo = StringUtils.trimToNull(line.getPrescriptionNo());
            if (prescriptionNo != null) {
                nos.add(prescriptionNo);
            }
        }
        if (nos.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String prescriptionNo : nos) {
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(prescriptionNo);
        }
        return builder.toString();
    }

    /**
     * 就诊医保人员编号（本地平台映射，无映射时空串）。
     *
     * @param encounterId 就诊主键
     * @return 人员编号
     */
    private String resolveInsuranceNo(Long encounterId) {
        if (encounterId == null) {
            return "";
        }
        Map<Long, String> psnByEncounter =
            insuranceRegisterBenefitQueryExtension.mapPsnNoByEncounterIds(List.of(encounterId));
        if (psnByEncounter == null) {
            return "";
        }
        String psnNo = psnByEncounter.get(encounterId);
        if (psnNo == null) {
            return "";
        }
        return psnNo.trim();
    }

    /**
     * 费用分栏展示：0 或空不打金额，避免票面铺满 ¥0.00。
     *
     * @param amount 栏金额
     * @return 展示串
     */
    private static String formatInvoiceFeeSlot(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return formatAmountText(amount);
    }

    /**
     * 发票支付栏：无值打 0.00。
     *
     * @param amount 金额
     * @return 展示串
     */
    private static String formatInvoiceMoney(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return formatAmountText(amount);
    }

    /**
     * 个人自付渠道合计（空渠道不参与；全空返回 null，由调用方打 0.00）。
     *
     * @param cash   现金
     * @param wechat 微信（含已并入的银行卡）
     * @param alipay 支付宝
     * @return 合计；全空时 null
     */
    private static BigDecimal sumSelfPay(BigDecimal cash, BigDecimal wechat, BigDecimal alipay) {
        BigDecimal sum = null;
        sum = sumNullable(sum, cash);
        sum = sumNullable(sum, wechat);
        sum = sumNullable(sum, alipay);
        return sum;
    }

    /**
     * 人民币大写（到分）。零为「零元整」；无角分尾缀「整」。
     *
     * @param amount 金额
     * @return 大写；null 时空串
     */
    private static String formatChineseMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal scaled = MonetaryAmountSupport.scaleSettle(amount);
        if (scaled.compareTo(BigDecimal.ZERO) == 0) {
            return "零元整";
        }
        StringBuilder builder = new StringBuilder();
        if (scaled.signum() < 0) {
            builder.append('负');
            scaled = scaled.abs();
        }
        long fenTotal = scaled.movePointRight(2).longValueExact();
        long yuan = fenTotal / 100;
        int jiao = (int) ((fenTotal / 10) % 10);
        int fen = (int) (fenTotal % 10);
        builder.append(formatChineseInteger(yuan));
        builder.append('元');
        if (jiao == 0 && fen == 0) {
            builder.append('整');
            return builder.toString();
        }
        if (jiao == 0) {
            builder.append('零');
        } else {
            builder.append(chineseDigit(jiao));
            builder.append('角');
        }
        if (fen == 0) {
            builder.append('整');
        } else {
            builder.append(chineseDigit(fen));
            builder.append('分');
        }
        return builder.toString();
    }

    /**
     * 整数部分大写（不含「元」）。
     *
     * @param yuan 元整数
     * @return 大写
     */
    private static String formatChineseInteger(long yuan) {
        if (yuan == 0) {
            return "零";
        }
        StringBuilder result = new StringBuilder();
        int groupIndex = 0;
        long remain = yuan;
        while (remain > 0 && groupIndex < 3) {
            int group = (int) (remain % 10000);
            if (group != 0) {
                String groupText = formatChineseGroup(group);
                if (group < 1000 && result.length() > 0) {
                    result.insert(0, "零");
                }
                String groupUnit = "";
                if (groupIndex == 1) {
                    groupUnit = "万";
                } else if (groupIndex == 2) {
                    groupUnit = "亿";
                }
                result.insert(0, groupText + groupUnit);
            }
            remain = remain / 10000;
            groupIndex++;
        }
        return result.toString();
    }

    /**
     * 四位以内分组大写。
     *
     * @param group 0–9999
     * @return 大写
     */
    private static String formatChineseGroup(int group) {
        StringBuilder low = new StringBuilder();
        boolean zeroPending = false;
        int unit = 0;
        int remain = group;
        while (remain > 0) {
            int digit = remain % 10;
            if (digit == 0) {
                if (low.length() > 0) {
                    zeroPending = true;
                }
            } else {
                if (zeroPending) {
                    low.insert(0, "零");
                    zeroPending = false;
                }
                low.insert(0, chineseDigit(digit) + chineseUnit(unit));
            }
            remain = remain / 10;
            unit++;
        }
        return low.toString();
    }

    /**
     * 大写数字。
     *
     * @param digit 0–9
     * @return 零壹贰…
     */
    private static String chineseDigit(int digit) {
        if (digit == 1) {
            return "壹";
        }
        if (digit == 2) {
            return "贰";
        }
        if (digit == 3) {
            return "叁";
        }
        if (digit == 4) {
            return "肆";
        }
        if (digit == 5) {
            return "伍";
        }
        if (digit == 6) {
            return "陆";
        }
        if (digit == 7) {
            return "柒";
        }
        if (digit == 8) {
            return "捌";
        }
        if (digit == 9) {
            return "玖";
        }
        return "零";
    }

    /**
     * 个拾佰仟。
     *
     * @param unit 0 个、1 拾、2 佰、3 仟
     * @return 单位；个位为空串
     */
    private static String chineseUnit(int unit) {
        if (unit == 1) {
            return "拾";
        }
        if (unit == 2) {
            return "佰";
        }
        if (unit == 3) {
            return "仟";
        }
        return "";
    }

    /** 针式发票费用分栏累加器。 */
    private static final class InvoiceFeeBuckets {

        private static final String WESTERN = "western";
        private static final String EXAM = "exam";
        private static final String PATENT = "patent";
        private static final String SURGERY = "surgery";
        private static final String HERBAL = "herbal";
        private static final String OXYGEN = "oxygen";
        private static final String LAB = "lab";
        private static final String BLOOD = "blood";
        private static final String XRAY = "xray";
        private static final String DISPOSE = "dispose";
        private static final String ELECTRO = "electro";
        private static final String INJECTION = "injection";
        private static final String THERAPY = "therapy";
        private static final String MATERIAL = "material";

        private BigDecimal western = BigDecimal.ZERO;
        private BigDecimal exam = BigDecimal.ZERO;
        private BigDecimal patent = BigDecimal.ZERO;
        private BigDecimal surgery = BigDecimal.ZERO;
        private BigDecimal herbal = BigDecimal.ZERO;
        private BigDecimal oxygen = BigDecimal.ZERO;
        private BigDecimal lab = BigDecimal.ZERO;
        private BigDecimal blood = BigDecimal.ZERO;
        private BigDecimal xray = BigDecimal.ZERO;
        private BigDecimal dispose = BigDecimal.ZERO;
        private BigDecimal electro = BigDecimal.ZERO;
        private BigDecimal injection = BigDecimal.ZERO;
        private BigDecimal therapy = BigDecimal.ZERO;
        private BigDecimal material = BigDecimal.ZERO;

        /**
         * 累加到指定栏。
         *
         * @param slot   栏位码
         * @param amount 金额
         */
        private void add(String slot, BigDecimal amount) {
            if (WESTERN.equals(slot)) {
                western = western.add(amount);
                return;
            }
            if (EXAM.equals(slot)) {
                exam = exam.add(amount);
                return;
            }
            if (PATENT.equals(slot)) {
                patent = patent.add(amount);
                return;
            }
            if (SURGERY.equals(slot)) {
                surgery = surgery.add(amount);
                return;
            }
            if (HERBAL.equals(slot)) {
                herbal = herbal.add(amount);
                return;
            }
            if (OXYGEN.equals(slot)) {
                oxygen = oxygen.add(amount);
                return;
            }
            if (LAB.equals(slot)) {
                lab = lab.add(amount);
                return;
            }
            if (BLOOD.equals(slot)) {
                blood = blood.add(amount);
                return;
            }
            if (XRAY.equals(slot)) {
                xray = xray.add(amount);
                return;
            }
            if (DISPOSE.equals(slot)) {
                dispose = dispose.add(amount);
                return;
            }
            if (ELECTRO.equals(slot)) {
                electro = electro.add(amount);
                return;
            }
            if (INJECTION.equals(slot)) {
                injection = injection.add(amount);
                return;
            }
            if (THERAPY.equals(slot)) {
                therapy = therapy.add(amount);
                return;
            }
            material = material.add(amount);
        }

        /**
         * 各栏合计。
         *
         * @return 合计
         */
        private BigDecimal sum() {
            return western
                .add(exam)
                .add(patent)
                .add(surgery)
                .add(herbal)
                .add(oxygen)
                .add(lab)
                .add(blood)
                .add(xray)
                .add(dispose)
                .add(electro)
                .add(injection)
                .add(therapy)
                .add(material);
        }
    }

    /**
     * 两字符串非 blank 者优先，否则回落 fallback。
     *
     * @param first    优先串
     * @param fallback 回落串
     * @return trim 后非空串或 fallback 字符串形式
     */
    private static String firstNonBlank(String first, String fallback) {
        String trimmed = StringUtils.trimToNull(first);
        if (trimmed != null) {
            return trimmed;
        }
        return Objects.toString(fallback, "");
    }
}
