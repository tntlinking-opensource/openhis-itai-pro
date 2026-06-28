import { PRINT_SCENARIO } from '../constants/print-scenario';
import { buildExaminationPrintData } from './examination-print';
import { buildOutpatientChargeReceiptPrintData } from './outpatient-charge-receipt';
import { buildOutpatientDailySettlementPrintData } from './outpatient-daily-settlement';
import { buildOutpatientInfusionLabelPrintData } from './outpatient-infusion-label';
import { buildOutpatientPrescriptionPrintData } from './outpatient-prescription';
import { buildOutpatientRegisterReceiptPrintData } from './outpatient-register-receipt';
import { buildOutpatientTreatmentSheetPrintData } from './outpatient-treatment-sheet';
import { buildTestPrintData } from './test-print';
import { registerPrintMapper } from './registry';

registerPrintMapper(PRINT_SCENARIO.outpatientRx, buildOutpatientPrescriptionPrintData);
registerPrintMapper(PRINT_SCENARIO.examinationPrint, buildExaminationPrintData);
registerPrintMapper(PRINT_SCENARIO.testPrint, buildTestPrintData);
registerPrintMapper(PRINT_SCENARIO.opRegPrint, buildOutpatientRegisterReceiptPrintData);
registerPrintMapper(PRINT_SCENARIO.chargeReceipt, buildOutpatientChargeReceiptPrintData);
registerPrintMapper(PRINT_SCENARIO.opTreatmentPrint, buildOutpatientTreatmentSheetPrintData);
registerPrintMapper(PRINT_SCENARIO.opInfusionLabelPrint, buildOutpatientInfusionLabelPrintData);
registerPrintMapper(PRINT_SCENARIO.opDailySettlementPrint, buildOutpatientDailySettlementPrintData);

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
  OutpatientDailySettlementPrintSource,
} from './outpatient-daily-settlement';
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
export { buildTestPrintData } from './test-print';
export type { TestPrintData, TestPrintSource } from './test-print';
export { buildOutpatientTreatmentSheetPrintData } from './outpatient-treatment-sheet';
export type {
  OutpatientTreatmentSheetLine,
  OutpatientTreatmentSheetPrintData,
  OutpatientTreatmentSheetPrintSource,
} from './outpatient-treatment-sheet';
export { hasPrintMapper, registerPrintMapper, resolvePrintData } from './registry';
export type { PrintData, PrintMapper } from './types';
