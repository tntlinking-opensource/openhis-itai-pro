import { PRINT_SCENARIO } from '../constants/print-scenario';
import { buildExaminationPrintData } from './examination-print';
import { buildOutpatientChargeReceiptPrintData } from './outpatient-charge-receipt';
import { buildOutpatientDailySettlementPrintData } from './outpatient-daily-settlement';
import { buildPharmacyDailySettlementPrintData } from './pharmacy-daily-settlement';
import { buildOutpatientInfusionLabelPrintData } from './outpatient-infusion-label';
import { buildOutpatientSpecimenBarcodePrintData } from './outpatient-specimen-barcode';
import { buildOutpatientPrescriptionPrintData } from './outpatient-prescription';
import { buildOutpatientTcmPrescriptionPrintData } from './outpatient-tcm-prescription';
import { buildOutpatientRegisterReceiptPrintData } from './outpatient-register-receipt';
import { buildInpatientDispenseSheetPrintData } from './inpatient-dispense-sheet';
import { buildInpatientDispenseSummaryPrintData } from './inpatient-dispense-summary';
import { buildInpatientFeeListPrintData } from './inpatient-fee-list';
import { buildInpatientNursingRecordPrintData } from './inpatient-nursing-record';
import { buildInpatientPatrolCardPrintData } from './inpatient-patrol-card';
import { buildInpatientOralMedLabelPrintData } from './inpatient-oral-med-label';
import { buildInpatientExamApplyPrintData } from './inpatient-exam-apply';
import { buildInpatientLabApplyPrintData } from './inpatient-lab-apply';
import { buildInpatientTempOrderPrintData } from './inpatient-temp-order';
import { buildInpatientWristbandPrintData } from './inpatient-wristband';
import { buildOutpatientTreatmentSheetPrintData } from './outpatient-treatment-sheet';
import { buildTestPrintData } from './test-print';
import { buildInvMovementPrintData } from './inv-movement';
import { buildInvReturnPrintData } from './inv-return';
import { buildInvStocktakePrintData } from './inv-stocktake';
import { buildPeisReportPrintData } from './peis-report-print';
import { buildFinPriceAdjustmentPrintData } from './fin-price-adjustment';
import { registerPrintMapper } from './registry';

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
registerPrintMapper(PRINT_SCENARIO.opDailySettlementPrint, buildOutpatientDailySettlementPrintData);
registerPrintMapper(
  PRINT_SCENARIO.pharmacyDailySettlementPrint,
  buildPharmacyDailySettlementPrintData,
);
registerPrintMapper(PRINT_SCENARIO.ipNursingRecordPrint, buildInpatientNursingRecordPrintData);
registerPrintMapper(PRINT_SCENARIO.ipFeeListPrint, buildInpatientFeeListPrintData);
registerPrintMapper(PRINT_SCENARIO.ipTempOrderPrint, buildInpatientTempOrderPrintData);
registerPrintMapper(PRINT_SCENARIO.ipLongOrderPrint, buildInpatientTempOrderPrintData);
registerPrintMapper(PRINT_SCENARIO.ipDispenseSummaryPrint, buildInpatientDispenseSummaryPrintData);
registerPrintMapper(PRINT_SCENARIO.ipDispenseSheetPrint, buildInpatientDispenseSheetPrintData);
registerPrintMapper(PRINT_SCENARIO.ipPatrolCardPrint, buildInpatientPatrolCardPrintData);
registerPrintMapper(PRINT_SCENARIO.ipWristbandPrint, buildInpatientWristbandPrintData);
registerPrintMapper(PRINT_SCENARIO.ipOralMedLabelPrint, buildInpatientOralMedLabelPrintData);
registerPrintMapper(PRINT_SCENARIO.ipExamApplyPrint, buildInpatientExamApplyPrintData);
registerPrintMapper(PRINT_SCENARIO.ipLabApplyPrint, buildInpatientLabApplyPrintData);
registerPrintMapper(PRINT_SCENARIO.invMovementPrint, buildInvMovementPrintData);
registerPrintMapper(PRINT_SCENARIO.invStocktakePrint, buildInvStocktakePrintData);
registerPrintMapper(PRINT_SCENARIO.invReturnPrint, buildInvReturnPrintData);
registerPrintMapper(PRINT_SCENARIO.peisReportPrint, buildPeisReportPrintData);
registerPrintMapper(PRINT_SCENARIO.finPriceAdjustmentPrint, buildFinPriceAdjustmentPrintData);

export { buildExaminationPrintData } from './examination-print';
export type { ExaminationPrintData, ExaminationPrintSource } from './examination-print';
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
  OutpatientDailySettlementPrintMetric,
  OutpatientDailySettlementPrintNatureBlock,
  OutpatientDailySettlementPrintNatureType,
  OutpatientDailySettlementPrintSource,
} from './outpatient-daily-settlement';
export { buildPharmacyDailySettlementPrintData } from './pharmacy-daily-settlement';
export type {
  PharmacyDailySettlementCategoryPrintRow,
  PharmacyDailySettlementPrintData,
  PharmacyDailySettlementPrintSource,
  PharmacyDailySettlementProductPrintRow,
} from './pharmacy-daily-settlement';
export {
  buildExecuteTimeTextForInfusionDay,
  buildOutpatientInfusionLabelPrintData,
  groupInfusionMedicationLinesForPrint,
  isInfusionMedicationLine,
  resolveInfusionLabelPrintDayCount,
} from './outpatient-infusion-label';
export type {
  InfusionLabelPrintGroup,
  OutpatientInfusionLabelMedicationLine,
  OutpatientInfusionLabelPrintData,
  OutpatientInfusionLabelPrintSource,
} from './outpatient-infusion-label';
export { buildOutpatientSpecimenBarcodePrintData } from './outpatient-specimen-barcode';
export type {
  OutpatientSpecimenBarcodePrintData,
  OutpatientSpecimenBarcodePrintSource,
} from './outpatient-specimen-barcode';
export {
  buildOutpatientPrescriptionPrintData,
  groupSlipRowsByPrescriptionNo,
} from './outpatient-prescription';
export type {
  OutpatientPrescriptionMedicationLine,
  OutpatientPrescriptionPrintData,
} from './outpatient-prescription';
export {
  buildOutpatientTcmPrescriptionPrintData,
  isTcmPrescriptionSlipGroup,
} from './outpatient-tcm-prescription';
export type {
  OutpatientTcmPrescriptionHerbGridRow,
  OutpatientTcmPrescriptionPrintData,
} from './outpatient-tcm-prescription';
export { buildOutpatientRegisterReceiptPrintData } from './outpatient-register-receipt';
export type {
  OutpatientRegisterReceiptChargeLine,
  OutpatientRegisterReceiptPrintData,
  RegistrationSlipPrintSource,
} from './outpatient-register-receipt';
export { buildTestPrintData } from './test-print';
export type { TestPrintData, TestPrintSource } from './test-print';
export { buildOutpatientTreatmentSheetPrintData } from './outpatient-treatment-sheet';
export type {
  OutpatientTreatmentSheetLine,
  OutpatientTreatmentSheetPrintData,
  OutpatientTreatmentSheetPrintSource,
} from './outpatient-treatment-sheet';
export { buildInpatientFeeListPrintData } from './inpatient-fee-list';
export type {
  InpatientFeeListFeeRow,
  InpatientFeeListPrintData,
  InpatientFeeListPrintSource,
} from './inpatient-fee-list';
export { buildInpatientTempOrderPrintData } from './inpatient-temp-order';
export type {
  InpatientTempOrderPrintData,
  InpatientTempOrderPrintSource,
  InpatientTempOrderRow,
} from './inpatient-temp-order';
export { buildInpatientDispenseSummaryPrintData } from './inpatient-dispense-summary';
export type {
  InpatientDispenseSummaryDrugRow,
  InpatientDispenseSummaryPrintData,
  InpatientDispenseSummaryPrintSource,
} from './inpatient-dispense-summary';
export { buildInpatientDispenseSheetPrintData } from './inpatient-dispense-sheet';
export type {
  InpatientDispenseSheetDrugRow,
  InpatientDispenseSheetPrintData,
  InpatientDispenseSheetPrintSource,
} from './inpatient-dispense-sheet';
export { buildInpatientPatrolCardPrintData } from './inpatient-patrol-card';
export type {
  InpatientPatrolCardPrintData,
  InpatientPatrolCardPrintSource,
  InpatientPatrolCardRow,
} from './inpatient-patrol-card';
export { buildInpatientWristbandPrintData } from './inpatient-wristband';
export type {
  InpatientWristbandPrintData,
  InpatientWristbandPrintSource,
} from './inpatient-wristband';
export { buildInpatientOralMedLabelPrintData } from './inpatient-oral-med-label';
export type {
  InpatientOralMedLabelPrintData,
  InpatientOralMedLabelPrintSource,
} from './inpatient-oral-med-label';
export { buildInpatientExamApplyPrintData } from './inpatient-exam-apply';
export type {
  InpatientExamApplyItemRow,
  InpatientExamApplyPrintData,
  InpatientExamApplyPrintSource,
} from './inpatient-exam-apply';
export { buildInpatientLabApplyPrintData } from './inpatient-lab-apply';
export type {
  InpatientLabApplyItemRow,
  InpatientLabApplyPrintData,
  InpatientLabApplyPrintSource,
} from './inpatient-lab-apply';
export { buildInpatientNursingRecordPrintData } from './inpatient-nursing-record';
export type {
  InpatientNursingRecordPrintData,
  InpatientNursingRecordPrintSource,
  InpatientNursingRecordRow,
} from './inpatient-nursing-record';
export { buildInvMovementPrintData } from './inv-movement';
export type {
  InvMovementPrintData,
  InvMovementPrintLineRow,
  InvMovementPrintSource,
} from './inv-movement';
export { buildInvStocktakePrintData } from './inv-stocktake';
export type {
  InvStocktakePrintData,
  InvStocktakePrintLineRow,
  InvStocktakePrintSource,
} from './inv-stocktake';
export { buildInvReturnPrintData } from './inv-return';
export type { InvReturnPrintData, InvReturnPrintLineRow, InvReturnPrintSource } from './inv-return';
export { buildPeisReportPrintData } from './peis-report-print';
export type {
  PeisReportObservationLine,
  PeisReportPrintData,
  PeisReportPrintSource,
} from './peis-report-print';
export { buildFinPriceAdjustmentPrintData } from './fin-price-adjustment';
export type {
  FinPriceAdjustmentPrintData,
  FinPriceAdjustmentPrintLineRow,
  FinPriceAdjustmentPrintSource,
} from './fin-price-adjustment';
export { buildInvDocumentTitle } from './inv-common';
export { hasPrintMapper, registerPrintMapper, resolvePrintData } from './registry';
export type { PrintData, PrintMapper } from './types';
