/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientPrepayConstants;
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
 * 住院预交金收据打印数据集（对齐 {@code deposit_receipt} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class DepositReceiptPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;
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
        if (encounterId == null) {
            return Map.of();
        }

        InpatientEncounterContextQueryModel encounter = encounterContextFacade
                .getInpatientContextByEncounterId(encounterId)
                .orElse(null);
        List<PaymentRecDetailQueryModel> payDetails =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);
        return toDataset(payment, encounter, payDetails);
    }

    private Map<String, Object> toDataset(
            PaymentReconciliationQueryModel payment,
            InpatientEncounterContextQueryModel encounter,
            List<PaymentRecDetailQueryModel> payDetails) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> hospital = new LinkedHashMap<>();
        hospital.put("name", resolveHospitalDisplayName(encounter));
        hospital.put("miniProgramImageUrl", "");
        root.put("hospital", hospital);

        PaymentRecDetailQueryModel primaryDetail = payDetails != null && !payDetails.isEmpty()
                ? payDetails.get(0)
                : null;
        BigDecimal amount = primaryDetail != null && primaryDetail.getAmount() != null
                ? primaryDetail.getAmount()
                : payment.getDisplayAmount();
        if (amount == null) {
            amount = payment.getTenderedAmount();
        }

        Map<String, Object> deposit = new LinkedHashMap<>();
        deposit.put("ticketNo", StringUtils.defaultIfBlank(payment.getPaymentNo(), ""));
        deposit.put("tradeTime", formatTime(payment.getBillDate()));
        deposit.put("bizTypeText", resolveBizTypeText(payment.getPaymentEnum()));
        deposit.put("amount", formatMoney(amount));
        root.put("deposit", deposit);

        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", StringUtils.defaultString(encounter.getPatientName(), ""));
            patient.put("busNo", encounter.getPatientId() != null
                    ? String.valueOf(encounter.getPatientId())
                    : StringUtils.defaultString(encounter.getEncounterBusNo(), ""));
            patient.put("encounterBusNo", StringUtils.defaultString(encounter.getEncounterBusNo(), ""));
            patient.put("wardBed", buildWardBed(
                    encounter.getWardName(),
                    encounter.getOrganizationName(),
                    encounter.getBedName()));
        } else {
            patient.put("name", "");
            patient.put("busNo", "");
            patient.put("encounterBusNo", "");
            patient.put("wardBed", "");
        }
        root.put("patient", patient);

        Map<String, Object> encounterMap = new LinkedHashMap<>();
        if (encounter != null) {
            encounterMap.put("busNo", StringUtils.defaultString(encounter.getEncounterBusNo(), ""));
            encounterMap.put("wardBed", buildWardBed(
                    encounter.getWardName(),
                    encounter.getOrganizationName(),
                    encounter.getBedName()));
        } else {
            encounterMap.put("busNo", "");
            encounterMap.put("wardBed", "");
        }
        root.put("encounter", encounterMap);

        Map<String, Object> paymentMap = new LinkedHashMap<>();
        paymentMap.put("methodText", resolvePayMethodText(primaryDetail));
        root.put("payment", paymentMap);

        BigDecimal balance = primaryDetail != null && primaryDetail.getAfterBalance() != null
                ? primaryDetail.getAfterBalance()
                : null;
        root.put("account", Map.of("balance", formatMoney(balance)));

        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("name", resolveOperatorName(payment.getEntererId()));
        root.put("operator", operator);

        root.put("invoice", Map.of(
                "verifyUrl", "",
                "statusText", ""));

        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static String resolveBizTypeText(String paymentEnum) {
        if ("UN_PAY".equalsIgnoreCase(StringUtils.trimToEmpty(paymentEnum))) {
            return InpatientPrepayConstants.BIZ_TYPE_TEXT_REFUND;
        }
        return InpatientPrepayConstants.BIZ_TYPE_TEXT_COLLECT;
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

    private static String buildWardBed(String wardName, String organizationName, String bedName) {
        String ward = StringUtils.firstNonBlank(wardName, organizationName, "");
        if (StringUtils.isNotBlank(bedName)) {
            return StringUtils.isNotBlank(ward)
                    ? ward.trim() + " " + bedName.trim()
                    : bedName.trim();
        }
        return ward;
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
