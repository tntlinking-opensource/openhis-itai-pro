/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationReceiptChargeLineDto;
import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationSlipDto;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
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
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
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
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;

    /**
     * 在基础挂号单 DTO 上回补小票打印字段。
     *
     * @param slip 已映射的基础挂号单（非 null）
     * @param slipReadModel 挂号单读模型
     * @param paymentId 支付流水主键；可空时按就诊取最近成功支付
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
            Map<Long, Long> paymentByEncounter =
                paymentReconciliationQueryService.findLatestSuccessPaymentIdsByEncounterIds(List.of(encounterId));
            resolvedPaymentId = paymentByEncounter.get(encounterId);
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
     * @param slip               待填充挂号单 DTO
     * @param payment            支付读模型（可 null）
     * @param insuranceSnapshot  医保平台快照（可 null，作渠道回落）
     */
    private void applyPaymentBreakdown(
        RegistrationSlipDto slip,
        PaymentReconciliationQueryModel payment,
        OutpatientRegistrationPlatformQueryExtensionOut insuranceSnapshot) {
        Map<String, BigDecimal> channelAmounts = new HashMap<>();
        BigDecimal accountBalance = null;
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
                    accountBalance = detail.getAfterBalance();
                }
            }
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
        slip.setDeductibleLineText("");
        slip.setCashPayText(formatAmountText(cash));
        slip.setWechatPayText(formatAmountText(wechat));
        slip.setAlipayPayText(formatAmountText(alipay));
        slip.setAccountBalanceText(formatAmountText(accountBalance));
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
     * 由就诊自费账户合同号解析费用性质展示名（合同名称）。
     *
     * @param encounterId 就诊主键
     * @return 合同名称；无账户或合同时为 null
     */
    private String resolveFeeNatureText(Long encounterId) {
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
