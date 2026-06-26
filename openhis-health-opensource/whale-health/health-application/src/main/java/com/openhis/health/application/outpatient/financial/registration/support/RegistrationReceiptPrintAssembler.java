/*
 * Copyright © 2026 OpenHIS Contributors
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
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientRegistrationPlatformSnapshot;
import com.openhis.health.module.querysupport.encounter.OutpatientEncounterContextLoader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 挂号小票打印字段组装（与 op-reg-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class RegistrationReceiptPrintAssembler {

    private static final DateTimeFormatter CHARGE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrganizationQueryService organizationQueryService;
    private final OutpatientEncounterContextLoader outpatientEncounterContextLoader;
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
            outpatientEncounterContextLoader.getOutpatientContextByEncounterId(encounterId).orElse(null);

        slip.setHospitalName(firstNonBlank(resolveHospitalName(), ""));
        slip.setGenderEnumText(firstNonBlank(resolveGenderText(context), ""));
        slip.setAgeText(firstNonBlank(context != null ? context.getAgeText() : null, ""));
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

        OutpatientRegistrationPlatformSnapshot insuranceSnapshot = resolveInsuranceSnapshot(encounterId);
        applyPaymentBreakdown(slip, payment, insuranceSnapshot);

        if (payment != null) {
            slip.setCashierName(firstNonBlank(resolveCashierName(payment.getEntererId()), ""));
            slip.setChargeTimeText(formatChargeTime(payment.getBillDate()));
        } else {
            slip.setCashierName("");
            slip.setChargeTimeText("");
        }
    }

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

    private String resolveChargeItemName(ChargeItem line) {
        if (line.getDefinitionId() == null) {
            return null;
        }
        Map<Long, ChargeItemDefinition> definitionById =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(List.of(line.getDefinitionId()));
        ChargeItemDefinition definition = definitionById.get(line.getDefinitionId());
        if (definition != null && StringUtils.isNotBlank(definition.getChargeName())) {
            return definition.getChargeName().trim();
        }
        return null;
    }

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

    private void applyPaymentBreakdown(
        RegistrationSlipDto slip,
        PaymentReconciliationQueryModel payment,
        OutpatientRegistrationPlatformSnapshot insuranceSnapshot) {
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
        BigDecimal fund = firstNonNull(pooling, insuranceSnapshot != null ? insuranceSnapshot.getYbFundPaySumamt() : null);
        BigDecimal acct = firstNonNull(
            channelAmounts.get(PaymentChannel.INSURANCE_ACCT.getCode()),
            insuranceSnapshot != null ? insuranceSnapshot.getYbAcctPay() : null);
        BigDecimal cash = firstNonNull(
            channelAmounts.get(PaymentChannel.CASH.getCode()),
            insuranceSnapshot != null ? insuranceSnapshot.getYbPsnCashPay() : null);
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

    private OutpatientRegistrationPlatformSnapshot resolveInsuranceSnapshot(Long encounterId) {
        if (!outpatientRegistrationInsuranceExtension.isEnabled()) {
            return null;
        }
        List<OutpatientRegistrationPlatformSnapshot> snapshots =
            outpatientRegistrationInsuranceExtension.findPlatformSnapshots(List.of(encounterId), Map.of());
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        return snapshots.get(0);
    }

    private String resolveFeeNatureText(Long encounterId) {
        Account account = accountQueryService.getSelfAccount(encounterId);
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        return contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .map(Contract::getName)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .orElse(null);
    }

    private String resolveCashierName(Long entererId) {
        if (entererId == null) {
            return null;
        }
        Practitioner practitioner = practitionerQueryService.findById(entererId);
        if (practitioner == null || StringUtils.isBlank(practitioner.getName())) {
            return null;
        }
        return practitioner.getName().trim();
    }

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

    private static String formatChargeTime(LocalDateTime billDate) {
        if (billDate == null) {
            return "";
        }
        return CHARGE_TIME_FMT.format(billDate);
    }

    private static String formatAmountText(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal parseAmountText(String amountText) {
        if (StringUtils.isBlank(amountText)) {
            return null;
        }
        try {
            return new BigDecimal(amountText.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    private static String firstNonBlank(String first, String fallback) {
        String trimmed = StringUtils.trimToNull(first);
        if (trimmed != null) {
            return trimmed;
        }
        return Objects.toString(fallback, "");
    }
}
