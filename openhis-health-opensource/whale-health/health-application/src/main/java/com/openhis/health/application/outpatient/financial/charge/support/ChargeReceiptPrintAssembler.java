/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.support;

import com.openhis.health.application.outpatient.financial.charge.dto.ChargeReceiptChargeLineDto;
import com.openhis.health.application.outpatient.financial.charge.dto.ChargeReceiptSlipDto;
import com.openhis.health.application.outpatient.financial.charge.dto.ChargeSettlementInsuranceSummaryDto;
import com.openhis.health.application.outpatient.financial.charge.dto.ChargeSettlementPaymentLineDto;
import com.openhis.health.application.outpatient.financial.charge.dto.ChargeSettlementSetlinfoDto;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 门诊收费小票打印字段组装（与 op-charge-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class ChargeReceiptPrintAssembler {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterContextReader encounterContextReader;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;
    private final OutpatientChargeInsuranceExtension outpatientChargeInsuranceExtension;
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
        }
        ChargeSettlementInsuranceSummaryDto summary = new ChargeSettlementInsuranceSummaryDto();
        summary.setPaymentId(resolvedPaymentId);
        summary.setInsuranceSettled(hasInsurance);
        summary.setMedfeeSumamt(MonetaryAmountSupport.scaleSettle(medfee));
        summary.setFundPaySumamt(MonetaryAmountSupport.scaleSettle(fundPay));
        summary.setAcctPay(MonetaryAmountSupport.scaleSettle(acctPay));
        summary.setPsnCashPay(MonetaryAmountSupport.scaleSettle(selfPay));
        this.fillSettlementSummaryPaymentHeader(summary, resolvedPaymentId);
        this.fillSettlementSetlinfo(summary, resolvedPaymentId);
        return summary;
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
     * 从支付主单回填业务号、账单时间与展示金额。
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
                    .setBalc(record.getBalc());
            } else if (record.getBalc() != null) {
                // 多笔时余额取最后一笔非空值
                setlinfo.setBalc(record.getBalc());
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
        List<ChargeReceiptChargeLineDto> chargeLines = buildChargeItemLines(chargeItems);
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
     * 收费项 → 小票明细行（含目录名、数量单位与收费类别展示）。
     *
     * @param chargeItems 收费项列表
     * @return 打印明细行
     */
    private List<ChargeReceiptChargeLineDto> buildChargeItemLines(List<ChargeItem> chargeItems) {
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

        List<ChargeReceiptChargeLineDto> result = new ArrayList<>();
        for (ChargeItem line : chargeItems) {
            if (line == null) {
                continue;
            }
            ChargeItemDefinition definition = definitionById.get(line.getDefinitionId());
            ProductDefinitionDisplayDto product = resolveProductForChargeItem(line, productMap);
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
        return result;
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
     * <p>起付线取 2207 回执 {@code act_pay_dedc}；个账余额优先取回执 {@code balc}，
     * 再回落支付明细 {@code afterBalance}（门诊收费通常不写 afterBalance）。</p>
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
        if (payment != null && payment.getId() != null) {
            SettlementPrintAmounts settlementAmounts = resolveSettlementPrintAmounts(payment.getId());
            settlementActPayDedc = settlementAmounts.actPayDedc;
            settlementBalc = settlementAmounts.balc;
        }
        BigDecimal accountBalance = firstNonNull(settlementBalc, accountBalanceFromDetail);

        BigDecimal pooling = channelAmounts.get(PaymentChannel.INSURANCE_FUND.getCode());
        BigDecimal snapshotFund = null;
        if (insuranceSnapshot != null) {
            snapshotFund = insuranceSnapshot.getYbFundPaySumamt();
        }
        BigDecimal fund = firstNonNull(pooling, snapshotFund);
        BigDecimal snapshotAcct = null;
        if (insuranceSnapshot != null) {
            snapshotAcct = insuranceSnapshot.getYbAcctPay();
        }
        BigDecimal acct = firstNonNull(
            channelAmounts.get(PaymentChannel.INSURANCE_ACCT.getCode()),
            snapshotAcct);
        BigDecimal snapshotCash = null;
        if (insuranceSnapshot != null) {
            snapshotCash = insuranceSnapshot.getYbPsnCashPay();
        }
        BigDecimal cash = firstNonNull(
            channelAmounts.get(PaymentChannel.CASH.getCode()),
            snapshotCash);
        BigDecimal wechat = channelAmounts.get(PaymentChannel.WECHAT.getCode());
        BigDecimal alipay = channelAmounts.get(PaymentChannel.ALIPAY.getCode());
        BigDecimal bankCard = channelAmounts.get(PaymentChannel.BANK_CARD.getCode());

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
        slip.setAccountPayText(formatAmountText(acct));
        slip.setDeductibleLineText(formatAmountText(settlementActPayDedc));
        slip.setCashPayText(formatAmountText(cash));
        slip.setWechatPayText(formatAmountText(wechat));
        slip.setAlipayPayText(formatAmountText(alipay));
        slip.setAccountBalanceText(formatAmountText(accountBalance));
    }

    /**
     * 从支付单下未撤销的 2207 正式结算回执汇总小票起付线与个账余额。
     *
     * <p>多笔时 {@code act_pay_dedc} 求和；{@code balc} 取最后一笔非空值（与
     * {@link #fillSettlementSetlinfo} 口径一致）。</p>
     *
     * @param paymentId 支付主键
     * @return 起付线/余额；扩展未启用或无回执时字段为 null
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
            if (record.getBalc() != null) {
                amounts.balc = record.getBalc();
            }
        }
        return amounts;
    }

    /** 小票用结算回执金额（起付线 + 个账余额）。 */
    private static final class SettlementPrintAmounts {
        private BigDecimal actPayDedc;
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
