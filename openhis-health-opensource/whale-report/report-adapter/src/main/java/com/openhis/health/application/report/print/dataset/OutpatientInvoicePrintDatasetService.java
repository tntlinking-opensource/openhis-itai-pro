/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.OrganizationType;
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
 * 门诊票据（套打 overlay）打印数据集（对齐 {@code outpatient_invoice_overlay} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class OutpatientInvoicePrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final char[] CN_DIGITS = {'零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'};
    private static final char[] CN_UNITS = {'分', '角', '元', '拾', '佰', '仟', '万', '拾', '佰', '仟', '亿'};

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;    /**
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

        OutpatientEncounterContextQueryModel encounter = encounterId != null
                ? encounterContextFacade.getOutpatientContextByEncounterId(encounterId).orElse(null)
                : null;

        InvoiceQueryCommand invoiceQuery = new InvoiceQueryCommand();
        invoiceQuery.setPaymentId(paymentId);
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(invoiceQuery);

        List<PaymentRecDetailQueryModel> payDetails =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);

        return toDataset(payment, invoice, chargeItems, encounter, payDetails);
    }

    private Map<String, Object> toDataset(
            PaymentReconciliationQueryModel payment,
            Invoice invoice,
            List<ChargeItem> chargeItems,
            OutpatientEncounterContextQueryModel encounter,
            List<PaymentRecDetailQueryModel> payDetails) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));

        PaymentRecDetailQueryModel primaryDetail = payDetails != null && !payDetails.isEmpty()
                ? payDetails.get(0)
                : null;
        BigDecimal total = resolveTotalAmount(payment, chargeItems, primaryDetail);
        LocalDateTime invoiceTime = payment.getBillDate();
        if (invoiceTime == null) {
            invoiceTime = chargeItems.stream()
                    .map(ChargeItem::getOccurrenceTime)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        Map<String, Object> invoiceMap = new LinkedHashMap<>();
        invoiceMap.put("code", resolveInvoiceCode(invoice));
        invoiceMap.put("no", resolveInvoiceNo(invoice, payment));
        invoiceMap.put("amount", formatMoney(total));
        invoiceMap.put("amountChinese", toChineseMoney(total));
        invoiceMap.put("date", ReportDatasetTextSupport.formatDateTime(DATE_FMT, invoiceTime, LocalDateTime.now()));
        invoiceMap.put("verifyUrl", "");
        root.put("invoice", invoiceMap);

        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", nullToEmpty(encounter.getPatientName()));
            patient.put("busNo", nullToEmpty(encounter.getIdCard()));
            patient.put("payerInfo", StringUtils.firstNonBlank(
                    encounter.getIdCard(),
                    encounter.getEncounterBusNo(),
                    ""));
        } else {
            patient.put("name", "");
            patient.put("busNo", "");
            patient.put("payerInfo", "");
        }
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        if (encounter != null) {
            encounterMap.put("busNo", nullToEmpty(encounter.getEncounterBusNo()));
        } else {
            encounterMap.put("busNo", "");
        }
        root.put("encounter", encounterMap);

        Map<String, Object> paymentMap = new LinkedHashMap<>();
        paymentMap.put("methodText", resolvePayMethodText(primaryDetail));
        paymentMap.put("paymentNo", StringUtils.defaultIfBlank(payment.getPaymentNo(), ""));
        root.put("payment", paymentMap);

        root.put("operator", Map.of("name", resolveOperatorName(payment.getEntererId())));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static BigDecimal resolveTotalAmount(
            PaymentReconciliationQueryModel payment,
            List<ChargeItem> chargeItems,
            PaymentRecDetailQueryModel primaryDetail) {
        if (payment.getDisplayAmount() != null) {
            return payment.getDisplayAmount();
        }
        if (primaryDetail != null && primaryDetail.getAmount() != null) {
            return primaryDetail.getAmount();
        }
        if (payment.getTenderedAmount() != null) {
            return payment.getTenderedAmount();
        }
        return chargeItems.stream()
                .map(ChargeItem::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String resolveInvoiceCode(Invoice invoice) {
        if (invoice == null) {
            return "";
        }
        return StringUtils.firstNonBlank(invoice.getTypeCode(), invoice.getBillNo(), "");
    }

    private static String resolveInvoiceNo(Invoice invoice, PaymentReconciliationQueryModel payment) {
        if (invoice != null && StringUtils.isNotBlank(invoice.getBusNo())) {
            return invoice.getBusNo().trim();
        }
        return StringUtils.defaultIfBlank(payment.getPaymentNo(), "");
    }

    private String resolvePayMethodText(PaymentRecDetailQueryModel detail) {
        if (detail == null || StringUtils.isBlank(detail.getPayEnum())) {
            return "现金";
        }
        String display = paymentChannelDisplayAssembler.resolvePayChannelDisplay(detail.getPayEnum(), null);
        return ReportDatasetTextSupport.displayOrFallback(display, detail.getPayEnum());
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

    private String resolveHospitalDisplayName(OutpatientEncounterContextQueryModel encounter) {
        if (encounter == null || encounter.getOrganizationId() == null) {
            return "";
        }
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
        return "";
    }

    private static String toChineseMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        long cents = normalized.movePointRight(2).longValue();
        if (cents == 0) {
            return "零元整";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CN_UNITS.length && cents > 0; i++) {
            int digit = (int) (cents % 10);
            if (digit == 0) {
                if (i == 2 || i == 6 || i == 10) {
                    if (sb.length() > 0 && sb.charAt(0) != CN_UNITS[i]) {
                        sb.insert(0, CN_UNITS[i]);
                    }
                } else if (sb.length() > 0 && sb.charAt(0) != '零') {
                    sb.insert(0, '零');
                }
            } else {
                sb.insert(0, CN_UNITS[i]);
                sb.insert(0, CN_DIGITS[digit]);
            }
            cents /= 10;
        }
        String result = sb.toString();
        if (!result.endsWith("分") && !result.endsWith("角")) {
            result = result + "整";
        }
        return result;
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToEmpty(String value) {
        return ReportDatasetTextSupport.trimToEmpty(value);
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
                // skip
            }
        }
        return ids;
    }

    private static Long extractLong(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number number) {
                return number.longValue();
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return Long.parseLong(text.trim());
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
                    if (item instanceof Number number) {
                        out.add(number.longValue());
                    } else if (item instanceof String text && StringUtils.isNotBlank(text)) {
                        try {
                            out.add(Long.parseLong(text.trim()));
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
