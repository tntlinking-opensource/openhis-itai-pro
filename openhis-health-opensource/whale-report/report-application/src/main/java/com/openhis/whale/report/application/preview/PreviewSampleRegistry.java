package com.openhis.whale.report.application.preview;

import com.openhis.whale.report.application.preview.sample.ChargeReceiptSampleData;
import com.openhis.whale.report.application.preview.sample.DepositReceiptSampleData;
import com.openhis.whale.report.application.preview.sample.ExamApplyFormSampleData;
import com.openhis.whale.report.application.preview.sample.InfusionLabelSampleData;
import com.openhis.whale.report.application.preview.sample.InpatientCertificateSampleData;
import com.openhis.whale.report.application.preview.sample.InpatientChargeSummarySampleData;
import com.openhis.whale.report.application.preview.sample.InpatientDailyChargeSampleData;
import com.openhis.whale.report.application.preview.sample.InpatientInvoiceSampleData;
import com.openhis.whale.report.application.preview.sample.InsuranceSettlementSampleData;
import com.openhis.whale.report.application.preview.sample.LabApplyFormSampleData;
import com.openhis.whale.report.application.preview.sample.OrderExecuteSampleData;
import com.openhis.whale.report.application.preview.sample.OutpatientInvoiceSampleData;
import com.openhis.whale.report.application.preview.sample.OutpatientRefundReceiptSampleData;
import com.openhis.whale.report.application.preview.sample.PrescriptionSampleData;
import com.openhis.whale.report.application.preview.sample.PrintStubSampleData;
import com.openhis.whale.report.application.preview.sample.RegistrationReceiptSampleData;
import com.openhis.whale.report.application.preview.sample.SpecimenBarcodeSampleData;
import com.openhis.whale.report.application.preview.sample.TreatmentExecuteSampleData;
import com.openhis.whale.report.application.preview.sample.WardDispenseSummarySampleData;
import com.openhis.whale.report.application.preview.sample.WristbandSampleData;
import com.openhis.whale.report.domain.constants.ReportPackConstants;

import java.util.Map;

/**
 * 设计器预览样例数据集（packCode → SampleData）。
 */
public final class PreviewSampleRegistry {

    private PreviewSampleRegistry() {
    }

    /**
     * @param packCode 元素包编码
     * @return 样例 root Map；未知 pack 回退处方样例
     */
    public static Map<String, Object> sampleForPack(String packCode) {
        if (ReportPackConstants.OUTPATIENT_PRESCRIPTION.equals(packCode)) {
            return PrescriptionSampleData.build();
        }
        if (ReportPackConstants.CHARGE_RECEIPT.equals(packCode)) {
            return ChargeReceiptSampleData.build();
        }
        if (ReportPackConstants.IV_INFUSION_LABEL.equals(packCode)) {
            return InfusionLabelSampleData.build();
        }
        if (ReportPackConstants.BEDSIDE_SPECIMEN_BARCODE.equals(packCode)) {
            return SpecimenBarcodeSampleData.build();
        }
        if (ReportPackConstants.TREATMENT_EXECUTE_SHEET.equals(packCode)) {
            return TreatmentExecuteSampleData.build();
        }
        if (ReportPackConstants.PATIENT_WRISTBAND.equals(packCode)) {
            return WristbandSampleData.build();
        }
        if (ReportPackConstants.INPATIENT_DAILY_CHARGE_LIST.equals(packCode)) {
            return InpatientDailyChargeSampleData.build();
        }
        if (ReportPackConstants.DEPOSIT_RECEIPT.equals(packCode)) {
            return DepositReceiptSampleData.build();
        }
        if (ReportPackConstants.ORDER_EXECUTE_SHEET.equals(packCode)) {
            return OrderExecuteSampleData.build();
        }
        if (ReportPackConstants.IV_INFUSION_EXECUTE_SHEET.equals(packCode)) {
            return OrderExecuteSampleData.build();
        }
        if (ReportPackConstants.ADMISSION_CERTIFICATE.equals(packCode)
                || ReportPackConstants.DISCHARGE_CERTIFICATE.equals(packCode)) {
            return InpatientCertificateSampleData.build();
        }
        if (ReportPackConstants.LAB_APPLY_FORM.equals(packCode)) {
            return LabApplyFormSampleData.build();
        }
        if (ReportPackConstants.EXAM_APPLY_FORM.equals(packCode)) {
            return ExamApplyFormSampleData.build();
        }
        if (ReportPackConstants.PROCEDURE_APPLY_FORM.equals(packCode)) {
            return LabApplyFormSampleData.build();
        }
        if (ReportPackConstants.INPATIENT_CHARGE_SUMMARY.equals(packCode)) {
            return InpatientChargeSummarySampleData.build();
        }
        if (ReportPackConstants.INPATIENT_INVOICE_OVERLAY.equals(packCode)) {
            return InpatientInvoiceSampleData.build();
        }
        if (ReportPackConstants.INSURANCE_SETTLEMENT.equals(packCode)) {
            return InsuranceSettlementSampleData.build();
        }
        if (ReportPackConstants.OUTPATIENT_INVOICE_OVERLAY.equals(packCode)) {
            return OutpatientInvoiceSampleData.build();
        }
        if (ReportPackConstants.OUTPATIENT_CHARGE_LIST.equals(packCode)) {
            return ChargeReceiptSampleData.build();
        }
        if (ReportPackConstants.OUTPATIENT_REFUND_RECEIPT.equals(packCode)) {
            return OutpatientRefundReceiptSampleData.build();
        }
        if (ReportPackConstants.REGISTRATION_RECEIPT.equals(packCode)) {
            return RegistrationReceiptSampleData.build();
        }
        if (ReportPackConstants.WARD_DISPENSE_SUMMARY.equals(packCode)) {
            return WardDispenseSummarySampleData.build();
        }
        if (ReportPackConstants.PRINT_PAGE_STUB.equals(packCode)) {
            return PrintStubSampleData.buildPageStub();
        }
        if (ReportPackConstants.PRINT_ROLL_STUB.equals(packCode)) {
            return PrintStubSampleData.buildRollStub();
        }
        if (ReportPackConstants.PRINT_PAGE_OVERLAY_STUB.equals(packCode)) {
            return PrintStubSampleData.buildOverlayStub();
        }
        return PrescriptionSampleData.build();
    }
}
