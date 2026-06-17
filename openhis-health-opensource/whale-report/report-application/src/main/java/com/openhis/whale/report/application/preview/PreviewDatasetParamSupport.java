package com.openhis.whale.report.application.preview;

import com.openhis.whale.report.domain.constants.ReportPackConstants;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 设计器预览实参校验（packCode → 是否具备 Port 加载条件）。
 */
public final class PreviewDatasetParamSupport {

    private PreviewDatasetParamSupport() {
    }

    public static boolean hasRealParams(String packCode, Map<String, Object> params) {
        if (ReportPackConstants.OUTPATIENT_PRESCRIPTION.equals(packCode)) {
            return hasRealPrescriptionParams(params);
        }
        if (ReportPackConstants.CHARGE_RECEIPT.equals(packCode)
                || ReportPackConstants.OUTPATIENT_CHARGE_LIST.equals(packCode)
                || ReportPackConstants.OUTPATIENT_REFUND_RECEIPT.equals(packCode)
                || ReportPackConstants.REGISTRATION_RECEIPT.equals(packCode)) {
            return hasRealChargeReceiptParams(params);
        }
        if (ReportPackConstants.IV_INFUSION_LABEL.equals(packCode)) {
            return hasRealInfusionLabelParams(params);
        }
        if (ReportPackConstants.BEDSIDE_SPECIMEN_BARCODE.equals(packCode)) {
            return hasRealSpecimenBarcodeParams(params);
        }
        if (ReportPackConstants.TREATMENT_EXECUTE_SHEET.equals(packCode)
                || ReportPackConstants.LAB_APPLY_FORM.equals(packCode)
                || ReportPackConstants.EXAM_APPLY_FORM.equals(packCode)
                || ReportPackConstants.PROCEDURE_APPLY_FORM.equals(packCode)) {
            return hasRealTreatmentExecuteParams(params);
        }
        if (ReportPackConstants.PATIENT_WRISTBAND.equals(packCode)) {
            return hasRealWristbandParams(params);
        }
        if (ReportPackConstants.INPATIENT_DAILY_CHARGE_LIST.equals(packCode)) {
            return hasRealInpatientDailyChargeParams(params);
        }
        if (ReportPackConstants.DEPOSIT_RECEIPT.equals(packCode)) {
            return hasRealDepositReceiptParams(params);
        }
        if (ReportPackConstants.ORDER_EXECUTE_SHEET.equals(packCode)
                || ReportPackConstants.IV_INFUSION_EXECUTE_SHEET.equals(packCode)) {
            return hasRealOrderExecuteParams(params);
        }
        if (ReportPackConstants.INPATIENT_CHARGE_SUMMARY.equals(packCode)) {
            return hasRealInpatientChargeSummaryParams(params);
        }
        if (ReportPackConstants.INPATIENT_INVOICE_OVERLAY.equals(packCode)) {
            return hasRealInpatientInvoiceParams(params);
        }
        if (ReportPackConstants.INSURANCE_SETTLEMENT.equals(packCode)) {
            return hasRealInsuranceSettlementParams(params);
        }
        if (ReportPackConstants.OUTPATIENT_INVOICE_OVERLAY.equals(packCode)) {
            return hasRealOutpatientInvoiceParams(params);
        }
        if (ReportPackConstants.WARD_DISPENSE_SUMMARY.equals(packCode)) {
            return hasRealWardDispenseSummaryParams(params);
        }
        if (ReportPackConstants.ADMISSION_CERTIFICATE.equals(packCode)
                || ReportPackConstants.DISCHARGE_CERTIFICATE.equals(packCode)) {
            return hasEncounterIdParam(params);
        }
        if (ReportPackConstants.PRINT_PAGE_STUB.equals(packCode)
                || ReportPackConstants.PRINT_ROLL_STUB.equals(packCode)
                || ReportPackConstants.PRINT_PAGE_OVERLAY_STUB.equals(packCode)) {
            return hasEncounterIdParam(params);
        }
        return false;
    }

    public static boolean hasEncounterIdParam(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null;
    }

    private static boolean hasRealInfusionLabelParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null
                && (extractLongParam(params, "groupId", "group_id") != null
                || hasMedicationRequestIds(params));
    }

    private static boolean hasRealSpecimenBarcodeParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null
                && (extractLongParam(params, "serviceRequestId", "service_request_id") != null
                || hasServiceRequestIds(params));
    }

    private static boolean hasRealTreatmentExecuteParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null;
    }

    private static boolean hasRealWristbandParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null;
    }

    private static boolean hasRealInpatientDailyChargeParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null;
    }

    private static boolean hasServiceRequestIds(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        Object value = params.get("serviceRequestIds");
        if (value == null) {
            value = params.get("service_request_ids");
        }
        return value instanceof List<?> list && !list.isEmpty();
    }

    private static boolean hasMedicationRequestIds(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        Object value = params.get("medicationRequestIds");
        if (value == null) {
            value = params.get("medication_request_ids");
        }
        return value instanceof List<?> list && !list.isEmpty();
    }

    private static boolean hasRealOutpatientInvoiceParams(Map<String, Object> params) {
        return extractLongParam(params, "paymentId", "payment_id") != null;
    }

    private static boolean hasRealInsuranceSettlementParams(Map<String, Object> params) {
        return extractLongParam(params, "paymentId", "payment_id") != null;
    }

    private static boolean hasRealInpatientInvoiceParams(Map<String, Object> params) {
        return extractLongParam(params, "paymentId", "payment_id") != null;
    }

    private static boolean hasRealInpatientChargeSummaryParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null
                && extractLongParam(params, "patientId", "patient_id") != null;
    }

    private static boolean hasRealOrderExecuteParams(Map<String, Object> params) {
        return extractLongParam(params, "encounterId", "encounter_id") != null
                && hasExecutionDeadline(params);
    }

    private static boolean hasExecutionDeadline(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        Object value = params.get("executionDeadline");
        if (value == null) {
            value = params.get("execution_deadline");
        }
        return value != null && StringUtils.hasText(String.valueOf(value));
    }

    private static boolean hasRealDepositReceiptParams(Map<String, Object> params) {
        return extractLongParam(params, "paymentId", "payment_id") != null;
    }

    private static boolean hasRealWardDispenseSummaryParams(Map<String, Object> params) {
        return hasNonBlankStringParam(params, "summaryNo", "summary_no");
    }

    private static boolean hasRealChargeReceiptParams(Map<String, Object> params) {
        return extractLongParam(params, "paymentId", "payment_id") != null;
    }

    private static boolean hasRealPrescriptionParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        if (extractLongParam(params, "encounterId", "encounter_id") == null) {
            return false;
        }
        return extractLongParam(params, "prescriptionId", "prescription_id") != null
                || hasNonBlankStringParam(params, "prescriptionNo", "prescription_no")
                || extractLongParam(params, "prescriptionGroupId", "prescription_group_id") != null
                || hasMedicationRequestIds(params);
    }

    private static boolean hasNonBlankStringParam(Map<String, Object> params, String... keys) {
        if (params == null) {
            return false;
        }
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof String s && StringUtils.hasText(s)) {
                return true;
            }
        }
        return false;
    }

    private static Long extractLongParam(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            Object id = params.get(key);
            if (id instanceof Number n) {
                return n.longValue();
            }
            if (id instanceof String s && StringUtils.hasText(s)) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }
}
