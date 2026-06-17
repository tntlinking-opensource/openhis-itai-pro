/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.port;

import com.openhis.health.application.report.print.dataset.ChargeReceiptPrintDatasetService;
import com.openhis.health.application.report.print.dataset.DepositReceiptPrintDatasetService;
import com.openhis.health.application.report.print.dataset.EncounterStubPrintDatasetService;
import com.openhis.health.application.report.print.dataset.InfusionLabelPrintDatasetService;
import com.openhis.health.application.report.print.dataset.InpatientCertificatePrintDatasetService;
import com.openhis.health.application.report.print.dataset.InpatientChargeSummaryPrintDatasetService;
import com.openhis.health.application.report.print.dataset.InpatientDailyChargePrintDatasetService;
import com.openhis.health.application.report.print.dataset.InpatientInvoicePrintDatasetService;
import com.openhis.health.application.report.print.dataset.InsuranceSettlementPrintDatasetService;
import com.openhis.health.application.report.print.dataset.OrderExecutePrintDatasetService;
import com.openhis.health.application.report.print.dataset.OutpatientChargeListPrintDatasetService;
import com.openhis.health.application.report.print.dataset.OutpatientInvoicePrintDatasetService;
import com.openhis.health.application.report.print.dataset.OutpatientPrescriptionPrintDatasetService;
import com.openhis.health.application.report.print.dataset.OutpatientRefundReceiptPrintDatasetService;
import com.openhis.health.application.report.print.dataset.RegistrationReceiptPrintDatasetService;
import com.openhis.health.application.report.print.dataset.ServiceApplyFormPrintDatasetService;
import com.openhis.health.application.report.print.dataset.SpecimenBarcodePrintDatasetService;
import com.openhis.health.application.report.print.dataset.TreatmentExecutePrintDatasetService;
import com.openhis.health.application.report.print.dataset.WardDispenseSummaryPrintDatasetService;
import com.openhis.health.application.report.print.dataset.WristbandPrintDatasetService;
import com.openhis.health.application.report.support.ReportDatasetParamSupport;
import com.openhis.whale.report.domain.port.print.PrintDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * B 类单证 {@link PrintDataPort} 实现：按 {@code packCode} 路由至各 PrintDatasetService。
 */
@Component
@ConditionalOnClass(PrintDataPort.class)
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
public class PrintDataPortAdapter implements PrintDataPort {

    private static final String ROW_FILTER_INFUSION = "infusion";

    private static final String PACK_OUTPATIENT_PRESCRIPTION = "outpatient_prescription";
    private static final String PACK_CHARGE_RECEIPT = "charge_receipt";
    private static final String PACK_REGISTRATION_RECEIPT = "registration_receipt";
    private static final String PACK_OUTPATIENT_REFUND_RECEIPT = "outpatient_refund_receipt";
    private static final String PACK_DEPOSIT_RECEIPT = "deposit_receipt";
    private static final String PACK_IV_INFUSION_LABEL = "iv_infusion_label";
    private static final String PACK_BEDSIDE_SPECIMEN_BARCODE = "bedside_specimen_barcode";
    private static final String PACK_TREATMENT_EXECUTE_SHEET = "treatment_execute_sheet";
    private static final String PACK_PATIENT_WRISTBAND = "patient_wristband";
    private static final String PACK_INPATIENT_DAILY_CHARGE_LIST = "inpatient_daily_charge_list";
    private static final String PACK_ORDER_EXECUTE_SHEET = "order_execute_sheet";
    private static final String PACK_IV_INFUSION_EXECUTE_SHEET = "iv_infusion_execute_sheet";
    private static final String PACK_INPATIENT_CHARGE_SUMMARY = "inpatient_charge_summary";
    private static final String PACK_INPATIENT_INVOICE_OVERLAY = "inpatient_invoice_overlay";
    private static final String PACK_INSURANCE_SETTLEMENT = "insurance_settlement";
    private static final String PACK_OUTPATIENT_INVOICE_OVERLAY = "outpatient_invoice_overlay";
    private static final String PACK_OUTPATIENT_CHARGE_LIST = "outpatient_charge_list";
    private static final String PACK_WARD_DISPENSE_SUMMARY = "ward_dispense_summary";
    private static final String PACK_ADMISSION_CERTIFICATE = "admission_certificate";
    private static final String PACK_DISCHARGE_CERTIFICATE = "discharge_certificate";
    private static final String PACK_LAB_APPLY_FORM = "lab_apply_form";
    private static final String PACK_EXAM_APPLY_FORM = "exam_apply_form";
    private static final String PACK_PROCEDURE_APPLY_FORM = "procedure_apply_form";
    private static final String PACK_PRINT_PAGE_STUB = "print_page_stub";
    private static final String PACK_PRINT_ROLL_STUB = "print_roll_stub";
    private static final String PACK_PRINT_PAGE_OVERLAY_STUB = "print_page_overlay_stub";

    private final OutpatientPrescriptionPrintDatasetService prescriptionDatasetService;
    private final ChargeReceiptPrintDatasetService chargeReceiptDatasetService;
    private final RegistrationReceiptPrintDatasetService registrationReceiptDatasetService;
    private final OutpatientRefundReceiptPrintDatasetService outpatientRefundReceiptDatasetService;
    private final DepositReceiptPrintDatasetService depositReceiptDatasetService;
    private final InfusionLabelPrintDatasetService infusionLabelDatasetService;
    private final SpecimenBarcodePrintDatasetService specimenBarcodeDatasetService;
    private final TreatmentExecutePrintDatasetService treatmentExecuteDatasetService;
    private final WristbandPrintDatasetService wristbandDatasetService;
    private final InpatientDailyChargePrintDatasetService inpatientDailyChargeDatasetService;
    private final OrderExecutePrintDatasetService orderExecuteDatasetService;
    private final InpatientChargeSummaryPrintDatasetService inpatientChargeSummaryDatasetService;
    private final InpatientInvoicePrintDatasetService inpatientInvoiceDatasetService;
    private final InsuranceSettlementPrintDatasetService insuranceSettlementDatasetService;
    private final OutpatientInvoicePrintDatasetService outpatientInvoiceDatasetService;
    private final OutpatientChargeListPrintDatasetService outpatientChargeListDatasetService;
    private final WardDispenseSummaryPrintDatasetService wardDispenseSummaryDatasetService;
    private final InpatientCertificatePrintDatasetService inpatientCertificateDatasetService;
    private final ServiceApplyFormPrintDatasetService serviceApplyFormDatasetService;
    private final EncounterStubPrintDatasetService encounterStubDatasetService;

    public PrintDataPortAdapter(
            OutpatientPrescriptionPrintDatasetService prescriptionDatasetService,
            ChargeReceiptPrintDatasetService chargeReceiptDatasetService,
            RegistrationReceiptPrintDatasetService registrationReceiptDatasetService,
            OutpatientRefundReceiptPrintDatasetService outpatientRefundReceiptDatasetService,
            DepositReceiptPrintDatasetService depositReceiptDatasetService,
            InfusionLabelPrintDatasetService infusionLabelDatasetService,
            SpecimenBarcodePrintDatasetService specimenBarcodeDatasetService,
            TreatmentExecutePrintDatasetService treatmentExecuteDatasetService,
            WristbandPrintDatasetService wristbandDatasetService,
            InpatientDailyChargePrintDatasetService inpatientDailyChargeDatasetService,
            OrderExecutePrintDatasetService orderExecuteDatasetService,
            InpatientChargeSummaryPrintDatasetService inpatientChargeSummaryDatasetService,
            InpatientInvoicePrintDatasetService inpatientInvoiceDatasetService,
            InsuranceSettlementPrintDatasetService insuranceSettlementDatasetService,
            OutpatientInvoicePrintDatasetService outpatientInvoiceDatasetService,
            OutpatientChargeListPrintDatasetService outpatientChargeListDatasetService,
            WardDispenseSummaryPrintDatasetService wardDispenseSummaryDatasetService,
            InpatientCertificatePrintDatasetService inpatientCertificateDatasetService,
            ServiceApplyFormPrintDatasetService serviceApplyFormDatasetService,
            EncounterStubPrintDatasetService encounterStubDatasetService) {
        this.prescriptionDatasetService = prescriptionDatasetService;
        this.chargeReceiptDatasetService = chargeReceiptDatasetService;
        this.registrationReceiptDatasetService = registrationReceiptDatasetService;
        this.outpatientRefundReceiptDatasetService = outpatientRefundReceiptDatasetService;
        this.depositReceiptDatasetService = depositReceiptDatasetService;
        this.infusionLabelDatasetService = infusionLabelDatasetService;
        this.specimenBarcodeDatasetService = specimenBarcodeDatasetService;
        this.treatmentExecuteDatasetService = treatmentExecuteDatasetService;
        this.wristbandDatasetService = wristbandDatasetService;
        this.inpatientDailyChargeDatasetService = inpatientDailyChargeDatasetService;
        this.orderExecuteDatasetService = orderExecuteDatasetService;
        this.inpatientChargeSummaryDatasetService = inpatientChargeSummaryDatasetService;
        this.inpatientInvoiceDatasetService = inpatientInvoiceDatasetService;
        this.insuranceSettlementDatasetService = insuranceSettlementDatasetService;
        this.outpatientInvoiceDatasetService = outpatientInvoiceDatasetService;
        this.outpatientChargeListDatasetService = outpatientChargeListDatasetService;
        this.wardDispenseSummaryDatasetService = wardDispenseSummaryDatasetService;
        this.inpatientCertificateDatasetService = inpatientCertificateDatasetService;
        this.serviceApplyFormDatasetService = serviceApplyFormDatasetService;
        this.encounterStubDatasetService = encounterStubDatasetService;
    }

    @Override
    public Map<String, Object> load(String packCode, Map<String, Object> params) {
        String code = ReportDatasetParamSupport.trimReportCode(packCode);
        if (!StringUtils.hasText(code)) {
            return Map.of();
        }
        Map<String, Object> scoped = ReportDatasetParamSupport.copyParams(params);
        if (PACK_OUTPATIENT_PRESCRIPTION.equals(code)) {
            return prescriptionDatasetService.build(scoped);
        }
        if (PACK_CHARGE_RECEIPT.equals(code)) {
            return chargeReceiptDatasetService.build(scoped);
        }
        if (PACK_REGISTRATION_RECEIPT.equals(code)) {
            return registrationReceiptDatasetService.build(scoped);
        }
        if (PACK_OUTPATIENT_REFUND_RECEIPT.equals(code)) {
            return outpatientRefundReceiptDatasetService.build(scoped);
        }
        if (PACK_DEPOSIT_RECEIPT.equals(code)) {
            return depositReceiptDatasetService.build(scoped);
        }
        if (PACK_IV_INFUSION_LABEL.equals(code)) {
            return infusionLabelDatasetService.build(scoped);
        }
        if (PACK_BEDSIDE_SPECIMEN_BARCODE.equals(code)) {
            return specimenBarcodeDatasetService.build(scoped);
        }
        if (PACK_TREATMENT_EXECUTE_SHEET.equals(code)) {
            return treatmentExecuteDatasetService.build(scoped);
        }
        if (PACK_PATIENT_WRISTBAND.equals(code)) {
            return wristbandDatasetService.build(scoped);
        }
        if (PACK_INPATIENT_DAILY_CHARGE_LIST.equals(code)) {
            return inpatientDailyChargeDatasetService.build(scoped);
        }
        if (PACK_ORDER_EXECUTE_SHEET.equals(code)) {
            return orderExecuteDatasetService.build(scoped);
        }
        if (PACK_IV_INFUSION_EXECUTE_SHEET.equals(code)) {
            scoped.put("rowFilter", ROW_FILTER_INFUSION);
            return orderExecuteDatasetService.build(scoped);
        }
        if (PACK_INPATIENT_CHARGE_SUMMARY.equals(code)) {
            return inpatientChargeSummaryDatasetService.build(scoped);
        }
        if (PACK_INPATIENT_INVOICE_OVERLAY.equals(code)) {
            return inpatientInvoiceDatasetService.build(scoped);
        }
        if (PACK_INSURANCE_SETTLEMENT.equals(code)) {
            return insuranceSettlementDatasetService.build(scoped);
        }
        if (PACK_OUTPATIENT_INVOICE_OVERLAY.equals(code)) {
            return outpatientInvoiceDatasetService.build(scoped);
        }
        if (PACK_OUTPATIENT_CHARGE_LIST.equals(code)) {
            return outpatientChargeListDatasetService.build(scoped);
        }
        if (PACK_WARD_DISPENSE_SUMMARY.equals(code)) {
            return wardDispenseSummaryDatasetService.build(scoped);
        }
        if (PACK_ADMISSION_CERTIFICATE.equals(code)) {
            scoped.putIfAbsent("certificateType", "admission");
            return inpatientCertificateDatasetService.build(scoped);
        }
        if (PACK_DISCHARGE_CERTIFICATE.equals(code)) {
            scoped.putIfAbsent("certificateType", "discharge");
            return inpatientCertificateDatasetService.build(scoped);
        }
        if (PACK_LAB_APPLY_FORM.equals(code)) {
            scoped.putIfAbsent("applyScope", "lab");
            return serviceApplyFormDatasetService.build(scoped);
        }
        if (PACK_EXAM_APPLY_FORM.equals(code)) {
            scoped.put("applyScope", "exam");
            return serviceApplyFormDatasetService.build(scoped);
        }
        if (PACK_PROCEDURE_APPLY_FORM.equals(code)) {
            scoped.putIfAbsent("applyScope", "procedure");
            return serviceApplyFormDatasetService.build(scoped);
        }
        if (PACK_PRINT_PAGE_STUB.equals(code)
                || PACK_PRINT_ROLL_STUB.equals(code)
                || PACK_PRINT_PAGE_OVERLAY_STUB.equals(code)) {
            return encounterStubDatasetService.build(scoped);
        }
        return Map.of();
    }
}
