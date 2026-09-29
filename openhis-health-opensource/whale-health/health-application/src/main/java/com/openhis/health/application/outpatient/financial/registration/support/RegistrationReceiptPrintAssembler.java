/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationReceiptChargeLineDto;
import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationSlipDto;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.financial.InsuranceReceiptAccountBalanceSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import com.openhis.whale.core.application.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 挂号小票打印字段组装（与 op-reg-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class RegistrationReceiptPrintAssembler {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterContextReader encounterContextReader;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 就诊费用性质展示（有医保户优先医保合同名，与挂号列表/医生站卡片同口径）。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;
    private final PractitionerQueryService practitionerQueryService;
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;
    private final OutpatientChargeInsuranceExtension outpatientChargeInsuranceExtension;

    /**
     * 在基础挂号单 DTO 上回补小票打印字段。
     *
     * @param slip 已映射的基础挂号单（非 null）
     * @param slipReadModel 挂号单读模型
     * @param paymentId 支付流水主键；可空时按挂号费 chargeItem 锚定对应 SUCCESS 支付单
     */
    public void enrichPrintFields(
        RegistrationSlipDto slip,
        RegistrationSlipQueryModel slipReadModel,
        Long paymentId) {
        if (slip == null || slipReadModel == null || slipReadModel.getEncounterId() == null) {
            return;
        }
        Long encounterId = slipReadModel.getEncounterId();
        OutpatientEncounterContextQueryModel context =
            encounterContextReader.getOutpatientContextByEncounterId(encounterId).orElse(null);

        slip.setHospitalName(firstNonBlank(resolveHospitalName(), ""));
        slip.setGenderEnumText(firstNonBlank(resolveGenderText(context), ""));
        String ageText = null;
        if (context != null) {
            ageText = context.getAgeText();
        }
        slip.setAgeText(firstNonBlank(ageText, ""));
        slip.setFeeNatureText(firstNonBlank(resolveFeeNatureText(encounterId), ""));
        slip.setRegisterDepartmentName(firstNonBlank(slipReadModel.getOrganizationName(), ""));

        Long resolvedPaymentId = paymentId;
        if (resolvedPaymentId == null) {
            resolvedPaymentId = this.resolveRegistrationFeePaymentId(encounterId);
        }

        PaymentReconciliationQueryModel payment = null;
        if (resolvedPaymentId != null) {
            payment = paymentReconciliationQueryService.mustGetPayment(resolvedPaymentId);
        }

        List<RegistrationReceiptChargeLineDto> chargeLines = buildChargeItemLines(encounterId, slipReadModel);
        slip.setChargeItemLines(chargeLines);

        BigDecimal totalAmount = resolveTotalAmount(slipReadModel, payment, chargeLines);
        slip.setAmount(totalAmount);
        slip.setTotalAmountText(formatAmountText(totalAmount));

        OutpatientRegistrationPlatformQueryExtensionOut insuranceSnapshot = resolveInsuranceSnapshot(encounterId);
        applyPaymentBreakdown(slip, payment, insuranceSnapshot);

        if (payment != null) {
            slip.setCashierName(firstNonBlank(resolveCashierName(payment.getEntererId()), ""));
            slip.setChargeTimeText(formatChargeTime(payment.getBillDate()));
        } else {
            slip.setCashierName("");
            slip.setChargeTimeText("");
        }
    }

    /**
     * 按挂号费 chargeItem（service_table = adm_healthcare_service）锚定 SUCCESS 支付单。
     *
     * @param encounterId 就诊主键
     * @return 挂号费支付流水主键；无命中时 null
     */
    private Long resolveRegistrationFeePaymentId(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setEncounterIds(List.of(encounterId));
        List<ChargeItem> lines = chargeItemQueryService.listChargeItemsByEncounterIds(query);
        LinkedHashSet<Long> registrationChargeItemIds = new LinkedHashSet<>();
        if (lines != null) {
            for (ChargeItem line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                if (!TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(line.getServiceTable())) {
                    continue;
                }
                registrationChargeItemIds.add(line.getId());
            }
        }
        if (registrationChargeItemIds.isEmpty()) {
            return null;
        }
        Map<Long, LinkedHashSet<Long>> byEncounter = new HashMap<>();
        byEncounter.put(encounterId, registrationChargeItemIds);
        Map<Long, Long> paymentByEncounter =
            paymentReconciliationQueryService.findSuccessPaymentIdsOverlappingChargeItemsByEncounter(byEncounter);
        return paymentByEncounter.get(encounterId);
    }

    /**
     * 组装挂号小票收费明细行：优先 HealthcareService 收费项，无则回落 slip 挂号费。
     *
     * @param encounterId     就诊主键
     * @param slipReadModel   挂号单读模型（回落金额与类目名）
     * @return 打印用收费行列表（至少一行）
     */
    private List<RegistrationReceiptChargeLineDto> buildChargeItemLines(
        Long encounterId,
        RegistrationSlipQueryModel slipReadModel) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setEncounterIds(List.of(encounterId));
        List<ChargeItem> lines = chargeItemQueryService.listChargeItemsByEncounterIds(query);
        if (lines == null || lines.isEmpty()) {
            RegistrationReceiptChargeLineDto fallback = new RegistrationReceiptChargeLineDto();
            fallback.setItemName(firstNonBlank(slipReadModel.getServiceTypeName(), "挂号费"));
            fallback.setAmountText(formatAmountText(slipReadModel.getAmount()));
            fallback.setChargeCategoryText("挂号费");
            return List.of(fallback);
        }
        String defaultCategory = firstNonBlank(slipReadModel.getServiceTypeName(), "挂号费");
        List<RegistrationReceiptChargeLineDto> result = new ArrayList<>();
        for (ChargeItem line : lines) {
            if (line == null) {
                continue;
            }
            if (!TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(line.getServiceTable())) {
                continue;
            }
            RegistrationReceiptChargeLineDto row = new RegistrationReceiptChargeLineDto();
            row.setItemName(firstNonBlank(resolveChargeItemName(line), defaultCategory));
            row.setAmountText(formatAmountText(line.getTotalPrice()));
            row.setChargeCategoryText(defaultCategory);
            result.add(row);
        }
        if (result.isEmpty()) {
            RegistrationReceiptChargeLineDto fallback = new RegistrationReceiptChargeLineDto();
            fallback.setItemName(defaultCategory);
            fallback.setAmountText(formatAmountText(slipReadModel.getAmount()));
            fallback.setChargeCategoryText("挂号费");
            result.add(fallback);
        }
        return result;
    }

    /**
     * 由收费项 definitionId 解析价目名称（ChargeItemDefinition.chargeName）。
     *
     * @param line 收费项
     * @return 价目名；无 definition 或无名称时 null
     */
    private String resolveChargeItemName(ChargeItem line) {
        if (line.getDefinitionId() == null) {
            return null;
        }
        Map<Long, ChargeItemDefinition> definitionById =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(List.of(line.getDefinitionId()));
        ChargeItemDefinition definition = definitionById.get(line.getDefinitionId());
        if (definition != null && StringUtils.isNotEmpty(definition.getChargeName())) {
            return definition.getChargeName().trim();
        }
        return null;
    }

    /**
     * 解析小票合计金额：支付 displayAmount 优先，其次 slip 金额，最后明细行求和。
     *
     * @param slipReadModel 挂号单读模型
     * @param payment       支付读模型（可 null）
     * @param chargeLines   已组装的收费行
     * @return 合计金额
     */
    private BigDecimal resolveTotalAmount(
        RegistrationSlipQueryModel slipReadModel,
        PaymentReconciliationQueryModel payment,
        List<RegistrationReceiptChargeLineDto> chargeLines) {
        if (payment != null && payment.getDisplayAmount() != null) {
            return payment.getDisplayAmount();
        }
        if (slipReadModel.getAmount() != null) {
            return slipReadModel.getAmount();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (RegistrationReceiptChargeLineDto row : chargeLines) {
            BigDecimal parsed = parseAmountText(row.getAmountText());
            if (parsed != null) {
                sum = sum.add(parsed);
            }
        }
        return sum;
    }

    /**
     * 按支付子明细与医保平台快照回填小票各渠道金额字段（统筹/个账/现金/微信/支付宝等）。
     *
     * <p>起付线取 2207 回执 {@code act_pay_dedc}；公务员待遇/补助取回执
     * {@code cvlserv_flag}/{@code cvlserv_pay}；个账余额经
     * {@link InsuranceReceiptAccountBalanceSupport}：回执 {@code balc} 为结算前时减本笔个账支出后展示，
     * 无回执则回落支付明细 {@code afterBalance}（门诊挂号通常不写 afterBalance）。</p>
     *
     * @param slip               待填充挂号单 DTO
     * @param payment            支付读模型（可 null）
     * @param insuranceSnapshot  医保平台快照（可 null，作渠道回落）
     */
    private void applyPaymentBreakdown(
        RegistrationSlipDto slip,
        PaymentReconciliationQueryModel payment,
        OutpatientRegistrationPlatformQueryExtensionOut insuranceSnapshot) {
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
        if (payment != null && payment.getId() != null) {
            SettlementPrintAmounts settlementAmounts = resolveSettlementPrintAmounts(payment.getId());
            settlementActPayDedc = settlementAmounts.actPayDedc;
            settlementBalc = settlementAmounts.balc;
            settlementCvlservFlag = settlementAmounts.cvlservFlag;
            settlementCvlservPay = settlementAmounts.cvlservPay;
        }

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
        // 个账余额：扣款后剩余（balc 扣前 − 本笔个账；无 balc 才用 afterBalance）
        BigDecimal accountBalance = InsuranceReceiptAccountBalanceSupport.resolveReceiptAccountBalance(
            settlementBalc,
            accountBalanceFromDetail,
            acct);
        BigDecimal snapshotCash = null;
        if (insuranceSnapshot != null) {
            snapshotCash = insuranceSnapshot.getYbPsnCashPay();
        }
        BigDecimal cash = channelAmounts.get(PaymentChannel.CASH.getCode());
        BigDecimal wechat = channelAmounts.get(PaymentChannel.WECHAT.getCode());
        BigDecimal alipay = channelAmounts.get(PaymentChannel.ALIPAY.getCode());
        BigDecimal bankCard = channelAmounts.get(PaymentChannel.BANK_CARD.getCode());
        // 本笔已有微信/支付宝/银行卡时，禁止用医保快照个人现金污染「现金支付」
        if (cash == null && wechat == null && alipay == null && bankCard == null) {
            cash = snapshotCash;
        }

        if (cash == null && wechat == null && alipay == null && bankCard == null
            && fund == null && acct == null && slip.getAmount() != null) {
            cash = slip.getAmount();
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
        // 公务员待遇/补助：仅小票展示透传，不参与渠道金额合计
        slip.setCvlservFlagText(formatCvlservFlagText(settlementCvlservFlag));
        slip.setCvlservPayText(formatAmountText(settlementCvlservPay));
        slip.setCashPayText(formatAmountText(cash));
        slip.setWechatPayText(formatAmountText(wechat));
        slip.setAlipayPayText(formatAmountText(alipay));
        slip.setAccountBalanceText(formatAmountText(accountBalance));
    }

    /**
     * 从支付单下未撤销的 2207 正式结算回执汇总小票起付线、公务员字段与个账余额原值。
     *
     * <p>多笔时 {@code act_pay_dedc}/{@code cvlserv_pay} 求和；{@code cvlserv_flag}
     * 取首笔非空；{@code balc} 取最后一笔非空值（平台原值/结算前，展示扣后见
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
     * 可空金额相加（任一侧 null 则返回另一侧）。
     *
     * @param left  左侧
     * @param right 右侧
     * @return 求和或非 null 侧
     */
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

    /** 小票用结算回执字段（起付线 + 公务员 + 个账余额）。 */
    private static final class SettlementPrintAmounts {
        private BigDecimal actPayDedc;
        private String cvlservFlag;
        private BigDecimal cvlservPay;
        private BigDecimal balc;
    }

    /**
     * 加载就诊对应医保平台快照（扩展未启用或无数据时 null）。
     *
     * @param encounterId 就诊主键
     * @return 首条平台快照；无则 null
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
     * 解析挂号小票费用性质展示名（挂号身份合同名）。
     *
     * <p>与挂号列表 {@code contractName}、医生站卡片同口径：有医保账户且合同号非空时优先医保合同名，
     * 否则回落登记个人现金户合同名。禁止仅取自费账户，否则市医保挂号也会打成「自费」。</p>
     *
     * @param encounterId 就诊主键
     * @return 合同名称；无可用合同时为 null
     */
    private String resolveFeeNatureText(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        // Pre：优先医保账户合同，与 ClinicRegister 列表费用性质列一致
        Map<Long, String> displayByEncounter =
            encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(List.of(encounterId));
        return displayByEncounter.get(encounterId);
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
     * 从组织树取首个有效医院类型机构名称作为小票抬头。
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
     * 金额格式化为小票展示串（两位小数 plain string）。
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
     * 两值中非 null 者优先（支付渠道与医保快照回落用）。
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
     * 两字符串中非 blank 者优先，否则回落 fallback。
     *
     * @param first    优先串
     * @param fallback 回落串
     * @return trim 后非空串或 fallback 的字符串形式
     */
    private static String firstNonBlank(String first, String fallback) {
        String trimmed = StringUtils.trimToNull(first);
        if (trimmed != null) {
            return trimmed;
        }
        return Objects.toString(fallback, "");
    }
}
