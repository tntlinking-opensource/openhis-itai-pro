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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医保结算单打印数据集（对齐 {@code insurance_settlement} 元素包 bindPath）。
 *
 * <p>CHS 2207 等未全量接入时，优先输出 HIS 侧支付/就诊字段；医保分项金额待 bridge 回填 {@code ybSettleIds} 后扩展。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceSettlementPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
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

        String contractDisplay = extractString(params, "contractDisplay", "contract_display");
        SettledInpatientPaymentViewResult settledView = loadSettledView(encounterId, patientId, paymentId);
        List<PaymentRecDetailQueryModel> payDetails =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);

        return toDataset(payment, encounter, settledView, payDetails, contractDisplay);
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
            InpatientEncounterContextQueryModel encounter,
            SettledInpatientPaymentViewResult settledView,
            List<PaymentRecDetailQueryModel> payDetails,
            String contractDisplay) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounter)));

        Map<String, Object> patient = new LinkedHashMap<>();
        if (encounter != null) {
            patient.put("name", nullToDash(encounter.getPatientName()));
            patient.put("inpatientNo", nullToDash(encounter.getEncounterBusNo()));
            patient.put("gender", resolveGenderText(encounter));
            patient.put("age", nullToDash(encounter.getAgeText()));
            patient.put("insuranceType", StringUtils.isNotBlank(contractDisplay)
                    ? contractDisplay.trim()
                    : nullToDash(payment.getContractNo()));
        } else {
            patient.put("name", "—");
            patient.put("inpatientNo", "—");
            patient.put("gender", "—");
            patient.put("age", "—");
            patient.put("insuranceType", nullToDash(contractDisplay));
        }
        root.put("patient", patient);

        PaymentRecDetailQueryModel primaryDetail = null;
        if (payDetails != null && !payDetails.isEmpty()) {
            primaryDetail = payDetails.get(0);
        }
        BigDecimal totalAmount = resolveTotalAmount(payment, settledView, primaryDetail);
        LocalDateTime settleTime = resolveSettleTime(payment, settledView);
        List<String> ybSettleIds = parseYbSettleIds(payment.getYbSettleIds());

        Map<String, Object> insurance = new LinkedHashMap<>();
        insurance.put("settleNo", ybSettleIds.isEmpty()
                ? nullToDash(payment.getPaymentNo())
                : String.join("、", ybSettleIds));
        insurance.put("mdtrtId", ReportDatasetTextSupport.firstOrDash(ybSettleIds));
        insurance.put("contractNo", nullToDash(payment.getContractNo()));
        insurance.put("settleTime", ReportDatasetTextSupport.formatDateTimeOrDash(TIME_FMT, settleTime));
        insurance.put("clearedText", ReportDatasetTextSupport.ybClearText(payment.getYbClearFlag()));
        root.put("insurance", insurance);

        Map<String, Object> insuranceFee = new LinkedHashMap<>();
        insuranceFee.put("totalAmount", formatMoney(totalAmount));
        insuranceFee.put("fundPayAmount", "—");
        insuranceFee.put("personalPayAmount", "—");
        insuranceFee.put("cashPayAmount", formatMoney(resolveCashPayAmount(primaryDetail, totalAmount)));
        insuranceFee.put("insuranceInFee", "—");
        insuranceFee.put("insuranceOutFee", "—");
        root.put("insuranceFee", insuranceFee);

        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("payMethodText", resolvePayMethodText(settledView, primaryDetail));
        settlement.put("paymentNo", nullToDash(payment.getPaymentNo()));
        root.put("settlement", settlement);

        String operatorName = resolveOperatorName(payment.getEntererId());
        if (settledView != null && StringUtils.isNotBlank(settledView.getOperatorName())) {
            operatorName = settledView.getOperatorName().trim();
        }
        root.put("operator", Map.of("name", operatorName));
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private static BigDecimal resolveTotalAmount(
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

    private static BigDecimal resolveCashPayAmount(
            PaymentRecDetailQueryModel primaryDetail,
            BigDecimal totalAmount) {
        if (primaryDetail != null && primaryDetail.getAmount() != null) {
            return primaryDetail.getAmount();
        }
        return totalAmount;
    }

    private static LocalDateTime resolveSettleTime(
            PaymentReconciliationQueryModel payment,
            SettledInpatientPaymentViewResult settledView) {
        if (settledView != null && settledView.getSettleTime() != null) {
            return settledView.getSettleTime();
        }
        return payment.getBillDate();
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

    private static String resolveGenderText(InpatientEncounterContextQueryModel encounter) {
        if (encounter == null || StringUtils.isBlank(encounter.getGenderEnum())) {
            return "—";
        }
        return switch (encounter.getGenderEnum().trim().toUpperCase()) {
            case "MALE", "M" -> "男";
            case "FEMALE", "F" -> "女";
            default -> encounter.getGenderEnum().trim();
        };
    }

    private String resolveOperatorName(Long entererId) {
        if (entererId == null) {
            return "—";
        }
        Practitioner practitioner = practitionerQueryService.findByUserId(entererId);
        if (practitioner != null && StringUtils.isNotBlank(practitioner.getName())) {
            return practitioner.getName().trim();
        }
        return "—";
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

    private static List<String> parseYbSettleIds(String raw) {
        List<String> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        for (String part : trimmed.split("[,，、]")) {
            String token = part.trim().replace("\"", "");
            if (StringUtils.isNotBlank(token)) {
                ids.add(token);
            }
        }
        return ids;
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToDash(String value) {
        return ReportDatasetTextSupport.trimToDash(value);
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

    private static String extractString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                return text.trim();
            }
        }
        return null;
    }
}
