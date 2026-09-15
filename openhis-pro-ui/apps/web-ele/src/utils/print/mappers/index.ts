import { PRINT_SCENARIO } from '../constants/print-scenario';
import { buildExaminationPrintData } from './examination-print';
import { buildFinPriceAdjustmentPrintData } from './fin-price-adjustment';
import { buildInpatientDispenseSheetPrintData } from './inpatient-dispense-sheet';
import { buildInpatientDispenseSummaryPrintData } from './inpatient-dispense-summary';
import { buildInpatientOrderDispenseSheetPrintData } from './inpatient-order-dispense-sheet';
import { buildInpatientExamApplyPrintData } from './inpatient-exam-apply';
import { buildInpatientFeeDetailPrintData } from './inpatient-fee-detail';
import { buildInpatientFeeListPrintData } from './inpatient-fee-list';
import { buildInpatientLabApplyPrintData } from './inpatient-lab-apply';
import {
  buildInpatientHomepagePrintData,
  buildInpatientHomepageWmPrintData,
} from './inpatient-homepage';
import { buildInpatientNursingRecordPrintData } from './inpatient-nursing-record';
import { buildInpatientOralMedLabelPrintData } from './inpatient-oral-med-label';
import { buildInpatientSpecimenBarcodePrintData } from './inpatient-specimen-barcode';
import { buildInpatientOralMedSheetPrintData } from './inpatient-oral-med-sheet';
import { buildInpatientPatrolCardPrintData } from './inpatient-patrol-card';
import { buildInpatientTempOrderPrintData } from './inpatient-temp-order';
import { buildInpatientBedCardPrintData } from './inpatient-bed-card';
import { buildInpatientPrepayReceiptPrintData } from './inpatient-prepay-receipt';
import { buildInpatientWristbandPrintData } from './inpatient-wristband';
import { buildInvMovementPrintData } from './inv-movement';
import { buildInvReturnPrintData } from './inv-return';
import { buildInvStocktakePrintData } from './inv-stocktake';
import { buildOutpatientChargeReceiptPrintData } from './outpatient-charge-receipt';
import { buildOutpatientDailySettlementPrintData } from './outpatient-daily-settlement';
import { buildOutpatientInfusionLabelPrintData } from './outpatient-infusion-label';
import { buildOutpatientPrescriptionPrintData } from './outpatient-prescription';
import { buildOutpatientRegisterReceiptPrintData } from './outpatient-register-receipt';
import { buildOutpatientSpecimenBarcodePrintData } from './outpatient-specimen-barcode';
import { buildOutpatientTcmPrescriptionPrintData } from './outpatient-tcm-prescription';
import { buildOutpatientTreatmentSheetPrintData } from './outpatient-treatment-sheet';
import { buildPeisGuidePrintData } from './peis-guide-print';
import { buildPeisReportPrintData } from './peis-report-print';
import { buildPharmacyDailySettlementPrintData } from './pharmacy-daily-settlement';
import { registerPrintMapper } from './registry';
import { buildTestPrintData } from './test-print';

registerPrintMapper(PRINT_SCENARIO.outpatientRx, buildOutpatientPrescriptionPrintData);
registerPrintMapper(PRINT_SCENARIO.outpatientTcmRx, buildOutpatientTcmPrescriptionPrintData);
registerPrintMapper(PRINT_SCENARIO.examinationPrint, buildExaminationPrintData);
registerPrintMapper(PRINT_SCENARIO.testPrint, buildTestPrintData);
registerPrintMapper(PRINT_SCENARIO.opRegPrint, buildOutpatientRegisterReceiptPrintData);
registerPrintMapper(PRINT_SCENARIO.chargeReceipt, buildOutpatientChargeReceiptPrintData);
registerPrintMapper(PRINT_SCENARIO.opTreatmentPrint, buildOutpatientTreatmentSheetPrintData);
registerPrintMapper(PRINT_SCENARIO.opInfusionLabelPrint, buildOutpatientInfusionLabelPrintData);
registerPrintMapper(PRINT_SCENARIO.ipInfusionLabelPrint, buildOutpatientInfusionLabelPrintData);
registerPrintMapper(PRINT_SCENARIO.opSpecimenBarcodePrint, buildOutpatientSpecimenBarcodePrintData);
registerPrintMapper(PRINT_SCENARIO.ipSpecimenBarcodePrint, buildInpatientSpecimenBarcodePrintData);
registerPrintMapper(PRINT_SCENARIO.opDailySettlementPrint, buildOutpatientDailySettlementPrintData);
registerPrintMapper(
  PRINT_SCENARIO.ipDailySettlementPatientPrint,
  buildOutpatientDailySettlementPrintData,
);
registerPrintMapper(
  PRINT_SCENARIO.pharmacyDailySettlementPrint,
  buildPharmacyDailySettlementPrintData,
);
registerPrintMapper(PRINT_SCENARIO.ipNursingRecordPrint, buildInpatientNursingRecordPrintData);
registerPrintMapper(PRINT_SCENARIO.ipHomepageWmPrint, buildInpatientHomepageWmPrintData);
registerPrintMapper(PRINT_SCENARIO.ipHomepageTcmPrint, buildInpatientHomepagePrintData);
registerPrintMapper(PRINT_SCENARIO.ipFeeListPrint, buildInpatientFeeListPrintData);
registerPrintMapper(PRINT_SCENARIO.ipFeeDetailPrint, buildInpatientFeeDetailPrintData);
registerPrintMapper(PRINT_SCENARIO.ipTempOrderPrint, buildInpatientTempOrderPrintData);
registerPrintMapper(PRINT_SCENARIO.ipLongOrderPrint, buildInpatientTempOrderPrintData);
registerPrintMapper(PRINT_SCENARIO.ipDispenseSummaryPrint, buildInpatientDispenseSummaryPrintData);
registerPrintMapper(PRINT_SCENARIO.ipDispenseSheetPrint, buildInpatientDispenseSheetPrintData);
registerPrintMapper(
  PRINT_SCENARIO.ipOrderDispenseSheetPrint,
  buildInpatientOrderDispenseSheetPrintData,
);
registerPrintMapper(PRINT_SCENARIO.ipOralMedSheetPrint, buildInpatientOralMedSheetPrintData);
registerPrintMapper(PRINT_SCENARIO.ipPatrolCardPrint, buildInpatientPatrolCardPrintData);
registerPrintMapper(PRINT_SCENARIO.ipWristbandPrint, buildInpatientWristbandPrintData);
registerPrintMapper(PRINT_SCENARIO.ipBedCardPrint, buildInpatientBedCardPrintData);
registerPrintMapper(PRINT_SCENARIO.ipPrepayReceiptPrint, buildInpatientPrepayReceiptPrintData);
registerPrintMapper(PRINT_SCENARIO.ipOralMedLabelPrint, buildInpatientOralMedLabelPrintData);
registerPrintMapper(PRINT_SCENARIO.ipExamApplyPrint, buildInpatientExamApplyPrintData);
registerPrintMapper(PRINT_SCENARIO.ipLabApplyPrint, buildInpatientLabApplyPrintData);
registerPrintMapper(PRINT_SCENARIO.invMovementPrint, buildInvMovementPrintData);
registerPrintMapper(PRINT_SCENARIO.invStocktakePrint, buildInvStocktakePrintData);
registerPrintMapper(PRINT_SCENARIO.invReturnPrint, buildInvReturnPrintData);
registerPrintMapper(PRINT_SCENARIO.peisReportPrint, buildPeisReportPrintData);
registerPrintMapper(PRINT_SCENARIO.peisGuidePrint, buildPeisGuidePrintData);
registerPrintMapper(PRINT_SCENARIO.finPriceAdjustmentPrint, buildFinPriceAdjustmentPrintData);

export { buildExaminationPrintData } from './examination-print';
export type { ExaminationPrintData, ExaminationPrintSource } from './examination-print';
export { buildFinPriceAdjustmentPrintData } from './fin-price-adjustment';
export type {
  FinPriceAdjustmentPrintData,
  FinPriceAdjustmentPrintLineRow,
  FinPriceAdjustmentPrintSource,
} from './fin-price-adjustment';
export { buildInpatientDispenseSheetPrintData } from './inpatient-dispense-sheet';
export type {
  InpatientDispenseSheetDrugRow,
  InpatientDispenseSheetPrintData,
  InpatientDispenseSheetPrintSource,
} from './inpatient-dispense-sheet';
export { buildInpatientOrderDispenseSheetPrintData } from './inpatient-order-dispense-sheet';
export {
  formatOrderDispensePatientHeadText,
  formatOrderDispenseTotalQuantityText,
  shouldUsePackageBatchDispenseQuantity,
} from './inpatient-order-dispense-sheet';
export type {
  InpatientOrderDispenseSheetDrugRow,
  InpatientOrderDispenseSheetPatientBlock,
  InpatientOrderDispenseSheetPrintData,
  InpatientOrderDispenseSheetPrintSource,
  InpatientOrderDispenseSheetSheetRow,
} from './inpatient-order-dispense-sheet';
export { buildInpatientDispenseSummaryPrintData } from './inpatient-dispense-summary';
export type {
  InpatientDispenseSummaryDrugRow,
  InpatientDispenseSummaryPrintData,
  InpatientDispenseSummaryPrintSource,
} from './inpatient-dispense-summary';
export { buildInpatientExamApplyPrintData } from './inpatient-exam-apply';
export type {
  InpatientExamApplyItemRow,
  InpatientExamApplyPrintData,
  InpatientExamApplyPrintSource,
} from './inpatient-exam-apply';
export { buildInpatientFeeDetailPrintData } from './inpatient-fee-detail';
export type {
  InpatientFeeDetailFeeRow,
  InpatientFeeDetailPrintData,
  InpatientFeeDetailPrintSource,
} from './inpatient-fee-detail';
export { buildInpatientFeeListPrintData } from './inpatient-fee-list';
export type {
  InpatientFeeListFeeRow,
  InpatientFeeListPrintData,
  InpatientFeeListPrintSource,
} from './inpatient-fee-list';
export { buildInpatientLabApplyPrintData } from './inpatient-lab-apply';
export type {
  InpatientLabApplyItemRow,
  InpatientLabApplyPrintData,
  InpatientLabApplyPrintSource,
} from './inpatient-lab-apply';
export {
  buildInpatientHomepagePrintData,
  buildInpatientHomepageWmPrintData,
} from './inpatient-homepage';
export type {
  InpatientHomepagePrintData,
  InpatientHomepagePrintSource,
} from './inpatient-homepage';
export { buildInpatientNursingRecordPrintData } from './inpatient-nursing-record';
export type {
  InpatientNursingRecordPrintData,
  InpatientNursingRecordPrintSource,
  InpatientNursingRecordRow,
} from './inpatient-nursing-record';
export { buildInpatientOralMedLabelPrintData } from './inpatient-oral-med-label';
export type {
  InpatientOralMedLabelPrintData,
  InpatientOralMedLabelPrintSource,
} from './inpatient-oral-med-label';
export { buildInpatientSpecimenBarcodePrintData } from './inpatient-specimen-barcode';
export type {
  InpatientSpecimenBarcodePrintData,
  InpatientSpecimenBarcodePrintSource,
} from './inpatient-specimen-barcode';
export { buildInpatientOralMedSheetPrintData } from './inpatient-oral-med-sheet';
export type {
  InpatientOralMedSheetDrugRow,
  InpatientOralMedSheetPatientBlock,
  InpatientOralMedSheetPrintData,
  InpatientOralMedSheetPrintSource,
} from './inpatient-oral-med-sheet';
export { buildInpatientPatrolCardPrintData } from './inpatient-patrol-card';
export type {
  InpatientPatrolCardPrintData,
  InpatientPatrolCardPrintSource,
  InpatientPatrolCardRow,
} from './inpatient-patrol-card';
export { buildInpatientTempOrderPrintData } from './inpatient-temp-order';
export type {
  InpatientTempOrderPrintData,
  InpatientTempOrderPrintSource,
  InpatientTempOrderRow,
} from './inpatient-temp-order';
export { buildInpatientBedCardPrintData } from './inpatient-bed-card';
export type { InpatientBedCardPrintData, InpatientBedCardPrintSource } from './inpatient-bed-card';
export { buildInpatientPrepayReceiptPrintData } from './inpatient-prepay-receipt';
export type {
  InpatientPrepayReceiptPrintData,
  InpatientPrepayReceiptPrintSource,
} from './inpatient-prepay-receipt';
export {
  formatAmountInChineseWords,
  resolvePrepayReceiptChannelBucket,
} from './inpatient-prepay-receipt';
export { buildInpatientWristbandPrintData } from './inpatient-wristband';
export type {
  InpatientWristbandPrintData,
  InpatientWristbandPrintSource,
} from './inpatient-wristband';
export { buildInvDocumentTitle } from './inv-common';
export { buildInvMovementPrintData } from './inv-movement';
export type {
  InvMovementPrintData,
  InvMovementPrintLineRow,
  InvMovementPrintSource,
} from './inv-movement';
export { buildInvReturnPrintData } from './inv-return';
export type { InvReturnPrintData, InvReturnPrintLineRow, InvReturnPrintSource } from './inv-return';
export { buildInvStocktakePrintData } from './inv-stocktake';
export type {
  InvStocktakePrintData,
  InvStocktakePrintLineRow,
  InvStocktakePrintSource,
} from './inv-stocktake';
export { buildOutpatientChargeReceiptPrintData } from './outpatient-charge-receipt';
export type {
  ChargeReceiptSlipPrintSource,
  OutpatientChargeReceiptChargeLine,
  OutpatientChargeReceiptPrintData,
} from './outpatient-charge-receipt';
export { buildOutpatientDailySettlementPrintData } from './outpatient-daily-settlement';
export type {
  OutpatientDailySettlementPrintData,
  OutpatientDailySettlementPrintGridRow,
  OutpatientDailySettlementPrintNatureRow,
  OutpatientDailySettlementPrintSource,
} from './outpatient-daily-settlement';
export {
  buildExecuteTimeTextForInfusionDay,
  buildOutpatientInfusionLabelPrintData,
  groupInfusionMedicationLinesForPrint,
  isInfusionMedicationLine,
  resolveInfusionLabelPrintDayCount,
  resolveInfusionLabelPrintTimesPerDay,
} from './outpatient-infusion-label';
export type {
  InfusionLabelPrintGroup,
  OutpatientInfusionLabelMedicationLine,
  OutpatientInfusionLabelPrintData,
  OutpatientInfusionLabelPrintSource,
} from './outpatient-infusion-label';
export {
  buildOutpatientPrescriptionPrintData,
  groupSlipRowsByPrescriptionNo,
} from './outpatient-prescription';
export type {
  OutpatientPrescriptionMedicationLine,
  OutpatientPrescriptionPrintData,
} from './outpatient-prescription';
export { buildOutpatientRegisterReceiptPrintData } from './outpatient-register-receipt';
export type {
  OutpatientRegisterReceiptChargeLine,
  OutpatientRegisterReceiptPrintData,
  RegistrationSlipPrintSource,
} from './outpatient-register-receipt';
export { buildOutpatientSpecimenBarcodePrintData } from './outpatient-specimen-barcode';
export type {
  OutpatientSpecimenBarcodePrintData,
  OutpatientSpecimenBarcodePrintSource,
} from './outpatient-specimen-barcode';
export {
  buildOutpatientTcmPrescriptionPrintData,
  isTcmPrescriptionSlipGroup,
} from './outpatient-tcm-prescription';
export type {
  OutpatientTcmPrescriptionHerbGridRow,
  OutpatientTcmPrescriptionPrintData,
} from './outpatient-tcm-prescription';
export { buildOutpatientTreatmentSheetPrintData } from './outpatient-treatment-sheet';
export type {
  OutpatientTreatmentSheetLine,
  OutpatientTreatmentSheetPrintData,
  OutpatientTreatmentSheetPrintSource,
} from './outpatient-treatment-sheet';
export { buildPeisGuidePrintData } from './peis-guide-print';
export type { PeisGuideLine, PeisGuidePrintData, PeisGuidePrintSource } from './peis-guide-print';
export { buildPeisReportPrintData } from './peis-report-print';
export type {
  PeisReportObservationLine,
  PeisReportPrintData,
  PeisReportPrintSource,
} from './peis-report-print';
export { buildPharmacyDailySettlementPrintData } from './pharmacy-daily-settlement';
export type {
  PharmacyDailySettlementCategoryPrintRow,
  PharmacyDailySettlementPrintData,
  PharmacyDailySettlementPrintSource,
  PharmacyDailySettlementProductPrintRow,
} from './pharmacy-daily-settlement';
export { hasPrintMapper, registerPrintMapper, resolvePrintData } from './registry';
export { buildTestPrintData } from './test-print';
export type { TestPrintData, TestPrintSource } from './test-print';
export type { PrintData, PrintMapper } from './types';
