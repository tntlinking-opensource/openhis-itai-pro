/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.querysupport.financial.SettledInpatientPaymentViewQuery;
import com.openhis.health.module.querysupport.financial.SettledInpatientPaymentViewResult;
import com.openhis.health.module.querysupport.financial.InpatientSettleReadLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
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
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.OrganizationType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院收费票据（套打 overlay）打印数据集（对齐 {@code inpatient_invoice_overlay} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class InpatientInvoicePrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final char[] CN_DIGITS = {'零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'};
    private static final char[] CN_UNITS = {'分', '角', '元', '拾', '佰', '仟', '万', '拾', '佰', '仟', '亿'};

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final InpatientSettleReadLoader inpatientSettleReadLoader;
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
        if (encounterId == null) {
            return Map.of();
        }

        Long patientId = extractLong(params, "patientId", "patient_id");
        InpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getInpatientContextByEncounterId(encounterId)
                .orElse(null);
        if (patientId != null && encounter != null && encounter.getPatientId() != null
                && !patientId.equals(encounter.getPatientId())) {
            return Map.of();
        }

        InvoiceQueryCommand invoiceQuery = new InvoiceQueryCommand();
        invoiceQuery.setPaymentId(paymentId);
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(invoiceQuery);

        SettledInpatientPaymentViewResult settledView = loadSettledView(encounterId, patientId, paymentId);
        List<PaymentRecDetailQueryModel> payDetails =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);

        return toDataset(payment, invoice, encounter, settledView, payDetails);
    }

    private SettledInpatientPaymentViewResult loadSettledView(
            Long encounterId,
            Long patientId,
            Long paymentId) {
        if (encounterId == null || patientId == null) {
            return null;
        }
        try {
            SettledInpatientPaymentViewQuery query = new SettledInpatientPaymentViewQuery();
            query.setEncounterId(encounterId);
            query.setPatientId(patientId);
            query.setPaymentId(paymentId);
            query.setPresentationMode(InpatientSettleConstants.PRESENTATION_MODE_DETAIL);
            return inpatientSettleReadLoader.loadSettledInpatientPaymentView(query);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Object> toDataset(
            PaymentReconciliationQueryModel payment,
            Invoice invoice,
            InpatientEncounterContextQueryModel encounter,
            SettledInpatientPaymentViewResult settledView,
            List<PaymentRecDetailQueryModel> payDetails) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));

        PaymentRecDetailQueryModel primaryDetail = payDetails != null && !payDetails.isEmpty()
                ? payDetails.get(0)
                : null;
        BigDecimal amount = resolveAmount(payment, settledView, primaryDetail);
        LocalDateTime settleTime = resolveSettleTime(payment, settledView);

        Map<String, Object> inpatientInvoice = new LinkedHashMap<>();
        inpatientInvoice.put("code", resolveInvoiceCode(invoice));
        inpatientInvoice.put("no", resolveInvoiceNo(invoice, payment));
        inpatientInvoice.put("amount", formatMoney(amount));
        inpatientInvoice.put("date", ReportDatasetTextSupport.formatDateTime(DATE_FMT, settleTime, LocalDateTime.now()));
        inpatientInvoice.put("verifyUrl", "");
        root.put("inpatientInvoice", inpatientInvoice);

        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", nullToEmpty(encounter.getPatientName()));
            patient.put("inpatientNo", nullToEmpty(encounter.getEncounterBusNo()));
            String dept = StringUtils.firstNonBlank(encounter.getOrganizationName(), encounter.getWardName(), "");
            patient.put("deptName", dept.trim());
            patient.put("bedNo", StringUtils.isNotBlank(encounter.getBedName())
                    ? encounter.getBedName().trim() + "床"
                    : "");
            patient.put("payerInfo", buildPayerInfo(encounter));
        } else {
            patient.put("name", "");
            patient.put("inpatientNo", "");
            patient.put("deptName", "");
            patient.put("bedNo", "");
            patient.put("payerInfo", "");
        }
        root.put("patient", patient);

        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("amount", formatMoney(amount));
        settlement.put("amountChinese", toChineseMoney(amount));
        settlement.put("settleTime", ReportDatasetTextSupport.formatDateTimeOrEmpty(TIME_FMT, settleTime));
        settlement.put("paymentNo", StringUtils.defaultIfBlank(payment.getPaymentNo(), ""));
        settlement.put("payMethodText", resolvePayMethodText(settledView, primaryDetail));
        root.put("settlement", settlement);

        Map<String, Object> deposit = new LinkedHashMap<>();
        deposit.put("prepayAmount", "");
        deposit.put("balanceHint", "");
        root.put("deposit", deposit);

        String operatorName = settledView != null && StringUtils.isNotBlank(settledView.getOperatorName())
                ? settledView.getOperatorName().trim()
                : resolveOperatorName(payment.getEntererId());
        root.put("operator", Map.of("name", operatorName));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static BigDecimal resolveAmount(
            PaymentReconciliationQueryModel payment,
            SettledInpatientPaymentViewResult settledView,
            PaymentRecDetailQueryModel primaryDetail) {
        if (settledView != null && settledView.getReceivableTotal() != null) {
            return settledView.getReceivableTotal();
        }
        if (primaryDetail != null && primaryDetail.getAmount() != null) {
            return primaryDetail.getAmount();
        }
        if (payment.getDisplayAmount() != null) {
            return payment.getDisplayAmount();
        }
        return payment.getTenderedAmount();
    }

    private static LocalDateTime resolveSettleTime(
            PaymentReconciliationQueryModel payment,
            SettledInpatientPaymentViewResult settledView) {
        if (settledView != null && settledView.getSettleTime() != null) {
            return settledView.getSettleTime();
        }
        return payment.getBillDate();
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

    private String resolvePayMethodText(
            SettledInpatientPaymentViewResult settledView,
            PaymentRecDetailQueryModel primaryDetail) {
        if (settledView != null && StringUtils.isNotBlank(settledView.getPayMethodText())) {
            return settledView.getPayMethodText().trim();
        }
        if (primaryDetail == null || StringUtils.isBlank(primaryDetail.getPayEnum())) {
            return "现金";
        }
        String display = paymentChannelDisplayAssembler.resolvePayChannelDisplay(
                primaryDetail.getPayEnum(), null);
        return ReportDatasetTextSupport.displayOrFallback(display, primaryDetail.getPayEnum());
    }

    private static String buildPayerInfo(InpatientEncounterContextQueryModel encounter) {
        String dept = StringUtils.firstNonBlank(encounter.getOrganizationName(), encounter.getWardName(), "");
        String bed = StringUtils.defaultString(encounter.getBedName(), "");
        if (StringUtils.isNotBlank(dept) && StringUtils.isNotBlank(bed)) {
            return dept.trim() + " " + bed.trim();
        }
        return StringUtils.firstNonBlank(dept, bed, "");
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

    private String resolveHospitalDisplayName(InpatientEncounterContextQueryModel encounter) {
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
        boolean lastZero = false;
        for (int i = 0; i < CN_UNITS.length && cents > 0; i++) {
            int digit = (int) (cents % 10);
            if (digit == 0) {
                if (i == 2 || i == 6 || i == 10) {
                    if (sb.length() > 0 && sb.charAt(0) != CN_UNITS[i]) {
                        sb.insert(0, CN_UNITS[i]);
                    }
                    lastZero = false;
                } else {
                    lastZero = sb.length() > 0 && sb.charAt(0) != '零';
                    if (lastZero) {
                        sb.insert(0, '零');
                    }
                }
            } else {
                sb.insert(0, CN_UNITS[i]);
                sb.insert(0, CN_DIGITS[digit]);
                lastZero = false;
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
}
