/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.charge.support;

import com.openhis.health.application.outpatient.financial.charge.dto.ChargeReceiptChargeLineDto;
import com.openhis.health.application.outpatient.financial.charge.dto.ChargeReceiptSlipDto;
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
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientRegistrationPlatformSnapshot;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.querysupport.encounter.OutpatientEncounterContextLoader;
import com.openhis.health.module.querysupport.product.ChargeItemProductLookupSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 门诊收费小票打印字段组装（与 op-charge-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class ChargeReceiptPrintAssembler {

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
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

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
            outpatientEncounterContextLoader.getOutpatientContextByEncounterId(encounterId).orElse(null);
        slip.setPatientName(firstNonBlank(context != null ? context.getPatientName() : null, ""));
        slip.setHospitalName(firstNonBlank(resolveHospitalName(), ""));
        slip.setGenderEnumText(firstNonBlank(resolveGenderText(context), ""));
        slip.setAgeText(firstNonBlank(context != null ? context.getAgeText() : null, ""));

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

        OutpatientRegistrationPlatformSnapshot insuranceSnapshot = resolveInsuranceSnapshot(encounterId);
        applyPaymentBreakdown(slip, payment, insuranceSnapshot, totalAmount);

        if (payment != null) {
            slip.setCashierName(firstNonBlank(resolveCashierName(payment.getEntererId()), ""));
            slip.setChargeTimeText(formatChargeTime(payment.getBillDate()));
        } else {
            slip.setCashierName("");
            slip.setChargeTimeText("");
        }
    }

    private List<ChargeItem> loadChargeItemsForPayment(
        PaymentReconciliationQueryModel payment,
        Long encounterId) {
        List<Long> chargeItemIds = parseChargeItemIds(payment != null ? payment.getChargeItemIds() : null);
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
        Map<Long, ChargeItemDefinition> definitionById = definitionIds.isEmpty()
            ? Map.of()
            : chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(new ArrayList<>(definitionIds));
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
            row.setChargeCategoryText(firstNonBlank(resolveChargeCategoryText(definition), "收费"));
            result.add(row);
        }
        return result;
    }

    private Map<String, ProductDefinitionDisplayDto> loadProductMapForChargeItems(List<ChargeItem> chargeItems) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = ChargeItemProductLookupSupport.buildLookupFromChargeItem(chargeItem);
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
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    private ProductDefinitionDisplayDto resolveProductForChargeItem(
        ChargeItem row,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        if (productMap == null || productMap.isEmpty() || row == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = ChargeItemProductLookupSupport.buildLookupFromChargeItem(row);
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
        if (product != null && StringUtils.isNotBlank(product.getProductName())) {
            return product.getProductName().trim();
        }
        return resolveChargeItemName(definition);
    }

    private String formatQuantityText(ChargeItem row, ProductDefinitionDisplayDto product) {
        if (row == null || row.getQuantityValue() == null) {
            return "";
        }
        String quantityPart = row.getQuantityValue().stripTrailingZeros().toPlainString();
        String unitPart = resolveQuantityUnitDisplay(row.getQuantityUnit(), product);
        if (StringUtils.isBlank(unitPart)) {
            return quantityPart;
        }
        return quantityPart + " " + unitPart;
    }

    private String resolveQuantityUnitDisplay(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (StringUtils.isBlank(quantityUnit)) {
            return "";
        }
        String normalized = quantityUnit.trim();
        if (product != null) {
            String resolved = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayNameFromProduct(
                product,
                normalized);
            if (StringUtils.isNotBlank(resolved)) {
                return resolved.trim();
            }
        }
        return normalized;
    }

    private String resolveChargeItemName(ChargeItemDefinition definition) {
        if (definition == null || StringUtils.isBlank(definition.getChargeName())) {
            return null;
        }
        return definition.getChargeName().trim();
    }

    private String resolveChargeCategoryText(ChargeItemDefinition definition) {
        if (definition == null || StringUtils.isBlank(definition.getTypeCode())) {
            return null;
        }
        ChargeTypeCategory category = EnumUtil.fromCode(ChargeTypeCategory.class, definition.getTypeCode().trim());
        if (category != null) {
            return category.getDisplay();
        }
        return definition.getTypeCode().trim();
    }

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

    private void applyPaymentBreakdown(
        ChargeReceiptSlipDto slip,
        PaymentReconciliationQueryModel payment,
        OutpatientRegistrationPlatformSnapshot insuranceSnapshot,
        BigDecimal totalAmount) {
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
                if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
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

    private static List<Long> parseChargeItemIds(String rawChargeItemIds) {
        if (StringUtils.isBlank(rawChargeItemIds)) {
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
