/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
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
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 门诊收费小票打印数据集（对齐 {@code charge_receipt} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class ChargeReceiptPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    /**
     * 组装打印数据集 root Map（供 Port / 预览 bindPath 绑定）。
     *
     * @param params 业务参数（如 encounterId、paymentId、dateFrom 等）
     * @return 数据集 root；必填参数缺失时返回空 Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Long paymentId = extractLong(params, "paymentId", "payment_id");
        if (paymentId == null) {
            return Map.of();
        }

        PaymentReconciliationQueryModel payment = paymentReconciliationQueryService.mustGetPayment(paymentId);
        Long encounterId = extractLong(params, "encounterId", "encounter_id");
        if (encounterId != null && payment.getEncounterId() != null
                && !encounterId.equals(payment.getEncounterId())) {
            return Map.of();
        }
        if (encounterId == null) {
            encounterId = payment.getEncounterId();
        }

        List<Long> chargeItemIds = extractLongList(params, "chargeItemIds", "charge_item_ids");
        if (chargeItemIds.isEmpty()) {
            chargeItemIds = parseIds(payment.getChargeItemIds());
        }
        if (chargeItemIds.isEmpty()) {
            return Map.of();
        }

        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(queryCommand);
        if (chargeItems == null || chargeItems.isEmpty()) {
            return Map.of();
        }

        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getDefinitionId() != null) {
                definitionIds.add(item.getDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> definitionMap =
                chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);

        OutpatientEncounterContextQueryModel encounter = encounterId != null
                ? encounterContextFacade.getOutpatientContextByEncounterId(encounterId).orElse(null)
                : null;

        List<PaymentRecDetailQueryModel> payDetails =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);

        return toDataset(payment, chargeItems, definitionMap, encounter, payDetails);
    }

    /**
     * 挂号小票打印数据集：在收费小票基础上追加 {@code registration} 块（bindPath {@code registration_receipt}）。
     *
     * @param params 业务参数（如 paymentId、encounterId 等）
     * @return 含 registration 块的打印数据集 root
     */
    public Map<String, Object> buildRegistrationReceipt(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Long paymentId = extractLong(params, "paymentId", "payment_id");
        if (paymentId == null) {
            return Map.of();
        }

        Map<String, Object> root = build(params);
        if (root.isEmpty()) {
            return Map.of();
        }

        PaymentReconciliationQueryModel payment = paymentReconciliationQueryService.mustGetPayment(paymentId);
        Long encounterId = extractLong(params, "encounterId", "encounter_id");
        if (encounterId == null) {
            encounterId = payment.getEncounterId();
        }
        OutpatientEncounterContextQueryModel encounter = encounterId != null
                ? encounterContextFacade.getOutpatientContextByEncounterId(encounterId).orElse(null)
                : null;
        root.put("registration", buildRegistrationBlock(encounter));
        return root;
    }

    private static Map<String, Object> buildRegistrationBlock(OutpatientEncounterContextQueryModel encounter) {
        Map<String, Object> registration = new LinkedHashMap<>();
        if (encounter != null) {
            registration.put("departmentName", StringUtils.defaultString(encounter.getOrganizationName(), ""));
            registration.put("doctorName", StringUtils.defaultString(encounter.getDoctorName(), ""));
            registration.put("serviceTypeName", StringUtils.defaultString(encounter.getServiceTypeName(), ""));
            registration.put("queueNo", encounter.getDisplayOrder() != null
                    ? String.valueOf(encounter.getDisplayOrder())
                    : "");
            registration.put("registerTime", formatTime(encounter.getStartTime()));
            registration.put("encounterBusNo", StringUtils.defaultString(encounter.getEncounterBusNo(), ""));
        } else {
            registration.put("departmentName", "");
            registration.put("doctorName", "");
            registration.put("serviceTypeName", "");
            registration.put("queueNo", "");
            registration.put("registerTime", "");
            registration.put("encounterBusNo", "");
        }
        return registration;
    }

    private Map<String, Object> toDataset(
            PaymentReconciliationQueryModel payment,
            List<ChargeItem> chargeItems,
            Map<Long, ChargeItemDefinition> definitionMap,
            OutpatientEncounterContextQueryModel encounter,
            List<PaymentRecDetailQueryModel> payDetails) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", resolveHospitalDisplayName(encounter));
        hospital.put("miniProgramImageUrl", "");
        root.put("hospital", hospital);

        BigDecimal subtotal = chargeItems.stream()
                .map(ChargeItem::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = chargeItems.stream()
                .map(ChargeItem::getDiscountAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal;
        if (payment.getDisplayAmount() != null) {
            total = payment.getDisplayAmount();
        } else if (payment.getTenderedAmount() != null) {
            total = payment.getTenderedAmount();
        }

        LocalDateTime occurrenceTime = payment.getBillDate();
        if (occurrenceTime == null) {
            occurrenceTime = chargeItems.stream()
                    .map(ChargeItem::getOccurrenceTime)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("busNo", StringUtils.defaultIfBlank(payment.getPaymentNo(), ""));
        charge.put("occurrenceTime", formatTime(occurrenceTime));
        charge.put("subtotalAmount", formatMoney(subtotal));
        charge.put("discountAmount", formatMoney(discount));
        charge.put("totalAmount", formatMoney(total));
        charge.put("lines", buildLines(chargeItems, definitionMap));
        root.put("charge", charge);

        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", StringUtils.defaultString(encounter.getPatientName(), ""));
            patient.put("genderText", toGenderText(encounter.getGenderEnum()));
            patient.put("ageText", StringUtils.defaultString(encounter.getAgeText(), ""));
            patient.put("patientTypeText", resolvePatientTypeText(payment));
            patient.put("outpatientNo", StringUtils.defaultString(encounter.getEncounterBusNo(), ""));
            patient.put("internalOutpatientNo", "");
            patient.put("busNo", StringUtils.defaultString(encounter.getIdCard(), ""));
        } else {
            patient.put("name", "");
            patient.put("genderText", "");
            patient.put("ageText", "");
            patient.put("patientTypeText", "");
            patient.put("outpatientNo", "");
            patient.put("internalOutpatientNo", "");
            patient.put("busNo", "");
        }
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        if (encounter != null) {
            encounterMap.put("busNo", StringUtils.defaultString(encounter.getEncounterBusNo(), ""));
        } else {
            encounterMap.put("busNo", "");
        }
        root.put("encounter", encounterMap);

        PaymentRecDetailQueryModel primaryDetail = payDetails != null && !payDetails.isEmpty()
                ? payDetails.get(0)
                : null;
        BigDecimal paidAmount = primaryDetail != null && primaryDetail.getAmount() != null
                ? primaryDetail.getAmount()
                : total;
        Map<String, Object> paymentMap = new LinkedHashMap<>();
        paymentMap.put("methodText", resolvePayMethodText(primaryDetail));
        paymentMap.put("paidAmount", formatMoney(paidAmount));
        paymentMap.put("changeAmount", "0.00");
        root.put("payment", paymentMap);

        root.put("paymentSummary", buildPaymentSummary(payment, total, paidAmount, primaryDetail));

        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("operatorName", resolveOperatorName(payment.getEntererId()));
        footer.put("chargeTime", formatTime(occurrenceTime));
        root.put("footer", footer);

        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("statusText", resolveInvoiceStatusText(payment.getInvoiceId()));
        invoice.put("verifyUrl", resolveInvoiceVerifyUrl(payment.getInvoiceId()));
        root.put("invoice", invoice);

        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("name", resolveOperatorName(payment.getEntererId()));
        root.put("operator", operator);

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static List<Map<String, Object>> buildLines(
            List<ChargeItem> chargeItems,
            Map<Long, ChargeItemDefinition> definitionMap) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            ChargeItemDefinition definition = definitionMap.get(item.getDefinitionId());
            String itemName = definition != null && StringUtils.isNotBlank(definition.getChargeName())
                    ? definition.getChargeName().trim()
                    : "收费项目";
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemName", itemName);
            line.put("specText", resolveLineSpecText(definition));
            line.put("quantity", formatQuantity(item.getQuantityValue()));
            line.put("unitText", StringUtils.defaultIfBlank(item.getQuantityUnit(), ""));
            line.put("unitPrice", formatMoney(item.getUnitPrice()));
            line.put("lineAmount", formatMoney(item.getTotalPrice()));
            if (definition != null && StringUtils.isNotBlank(definition.getTypeCode())) {
                line.put("definitionCode", definition.getTypeCode().trim());
            }
            line.put("categoryCode", resolveLineCategoryCode(definition));
            lines.add(line);
        }
        return lines;
    }

    private static Map<String, Object> buildPaymentSummary(
            PaymentReconciliationQueryModel payment,
            BigDecimal total,
            BigDecimal paidAmount,
            PaymentRecDetailQueryModel primaryDetail) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmount", formatMoney(total));
        summary.put("actualReceivedAmount", formatMoney(paidAmount));
        summary.put("cardPayAmount", "");
        summary.put("accountBalanceBefore", "");
        summary.put("seriousDiseaseFund", "");
        summary.put("accountBalanceAfter", "");
        summary.put("medicalAssistance", "");
        summary.put("civilServantSubsidy", "");
        summary.put("retiredOfficialFund", "");
        summary.put("povertyAlleviation", "");
        summary.put("mutualAidPay", "");
        summary.put("workInjuryFund", "");
        summary.put("insuranceFundTotal", "");
        summary.put("wechatCardPay", resolveWechatCardPayText(primaryDetail));
        summary.put("fundPayAmount", "");
        if (StringUtils.isNotBlank(payment.getYbSettleIds())) {
            summary.put("insuranceFundTotal", formatMoney(total.subtract(ReportDatasetTextSupport.zeroIfNull(paidAmount))));
        }
        return summary;
    }

    private static String resolveWechatCardPayText(PaymentRecDetailQueryModel detail) {
        if (detail == null || StringUtils.isBlank(detail.getPayEnum())) {
            return "";
        }
        String payEnum = detail.getPayEnum().trim().toUpperCase();
        if ("WECHAT".equals(payEnum) || "ALIPAY".equals(payEnum)) {
            return formatMoney(detail.getAmount());
        }
        return "";
    }

    private static String resolvePatientTypeText(PaymentReconciliationQueryModel payment) {
        if (payment == null || StringUtils.isBlank(payment.getContractNo())) {
            return "";
        }
        return payment.getContractNo().trim();
    }

    private static String resolveInvoiceStatusText(Long invoiceId) {
        if (invoiceId == null) {
            return "未开票，无电子票据";
        }
        return "已开票";
    }

    private static String resolveInvoiceVerifyUrl(Long invoiceId) {
        if (invoiceId == null) {
            return "";
        }
        return "";
    }

    private static String resolveLineSpecText(ChargeItemDefinition definition) {
        if (definition == null || StringUtils.isBlank(definition.getYbType())) {
            return "";
        }
        return definition.getYbType().trim();
    }

    private static String resolveLineCategoryCode(ChargeItemDefinition definition) {
        if (definition == null) {
            return "";
        }
        if (StringUtils.isNotBlank(definition.getYbType())) {
            return definition.getYbType().trim();
        }
        if (StringUtils.isNotBlank(definition.getTypeCode())) {
            return definition.getTypeCode().trim();
        }
        return "";
    }

    private static String toGenderText(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g != null) {
            return g.getDisplay();
        }
        return ReportDatasetTextSupport.genderCodeOrEmpty(genderEnum);
    }

    private String resolveOperatorName(Long entererId) {
        if (entererId == null) {
            return "";
        }
        Practitioner practitioner = practitionerQueryService.findByUserId(entererId);
        if (practitioner != null && StringUtils.isNotBlank(practitioner.getName())) {
            return practitioner.getName().trim();
        }
        return "";
    }

    private static String resolvePayMethodText(PaymentRecDetailQueryModel detail) {
        if (detail == null || StringUtils.isBlank(detail.getPayEnum())) {
            return "现金";
        }
        return switch (detail.getPayEnum().trim().toUpperCase()) {
            case "WECHAT" -> "微信";
            case "ALIPAY" -> "支付宝";
            case "BANK_CARD" -> "银行卡";
            case "CASH" -> "现金";
            default -> detail.getPayEnum().trim();
        };
    }

    private String resolveHospitalDisplayName(OutpatientEncounterContextQueryModel encounter) {
        if (encounter != null && encounter.getOrganizationId() != null) {
            Long orgId = encounter.getOrganizationId();
            for (int depth = 0; depth < 12; depth++) {
                Organization org = organizationQueryService.loadOrganizationByIdForDisplay(orgId);
                if (org == null) {
                    break;
                }
                if (OrganizationType.HOSPITAL.getCode().equals(org.getTypeEnum())
                        && StringUtils.isNotBlank(org.getName())) {
                    return org.getName().trim();
                }
                if (org.getParentId() == null) {
                    if (StringUtils.isNotBlank(org.getName())) {
                        return org.getName().trim();
                    }
                    break;
                }
                orgId = org.getParentId();
            }
        }
        return "";
    }

    private static String formatTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return TIME_FMT.format(time);
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        for (String part : raw.split(",")) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip invalid token
            }
        }
        return ids;
    }

    private static Long extractLong(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number n) {
                return n.longValue();
            }
            if (raw instanceof String s && StringUtils.isNotBlank(s)) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // next key
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> extractLongList(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Collection<?> collection) {
                List<Long> out = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof Number n) {
                        out.add(n.longValue());
                    } else if (item instanceof String s && StringUtils.isNotBlank(s)) {
                        try {
                            out.add(Long.parseLong(s.trim()));
                        } catch (NumberFormatException ignored) {
                            // skip
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
