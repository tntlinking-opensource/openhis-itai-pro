import type { PrintScenario } from './print-scenario';

import { PRINT_SCENARIO } from './print-scenario';

export const PRINT_SCENARIO_LABELS: Record<PrintScenario, string> = {
  [PRINT_SCENARIO.outpatientRx]: '门诊处方',
  [PRINT_SCENARIO.outpatientTcmRx]: '门诊中药处方',
  [PRINT_SCENARIO.chargeReceipt]: '门诊收费小票',
  [PRINT_SCENARIO.opRegPrint]: '门诊挂号小票',
  [PRINT_SCENARIO.opTreatmentPrint]: '门诊处置单',
  [PRINT_SCENARIO.opInfusionLabelPrint]: '输液瓶签',
  [PRINT_SCENARIO.opSpecimenBarcodePrint]: '采血贴',
  [PRINT_SCENARIO.opDailySettlementPrint]: '门诊费用日结单',
  [PRINT_SCENARIO.pharmacyDailySettlementPrint]: '药房日结单',
  [PRINT_SCENARIO.examinationPrint]: '检查申请单',
  [PRINT_SCENARIO.testPrint]: '检验申请单',
  [PRINT_SCENARIO.ipNursingRecordPrint]: '护理记录单',
  [PRINT_SCENARIO.ipFeeListPrint]: '住院费用清单',
  [PRINT_SCENARIO.ipFeeDetailPrint]: '住院费用明细单',
  [PRINT_SCENARIO.ipTempOrderPrint]: '临时医嘱',
  [PRINT_SCENARIO.ipLongOrderPrint]: '长期医嘱',
  [PRINT_SCENARIO.ipDispenseSummaryPrint]: '汇总领药单',
  [PRINT_SCENARIO.ipDispenseSheetPrint]: '摆药单',
  [PRINT_SCENARIO.ipOrderDispenseSheetPrint]: '医嘱执行摆药单',
  [PRINT_SCENARIO.ipOralMedSheetPrint]: '住院患者用药单',
  [PRINT_SCENARIO.ipPatrolCardPrint]: '输液巡视卡',
  [PRINT_SCENARIO.ipWristbandPrint]: '住院腕带',
  [PRINT_SCENARIO.ipBedCardPrint]: '床头卡',
  [PRINT_SCENARIO.ipPrepayReceiptPrint]: '住院预交金收据',
  [PRINT_SCENARIO.ipOralMedLabelPrint]: '住院口服药贴',
  [PRINT_SCENARIO.ipInfusionLabelPrint]: '住院瓶签',
  [PRINT_SCENARIO.ipExamApplyPrint]: '住院检查单',
  [PRINT_SCENARIO.ipLabApplyPrint]: '住院检验单',
  [PRINT_SCENARIO.invMovementPrint]: '库存计价流转单',
  [PRINT_SCENARIO.invStocktakePrint]: '库存盘点盈亏单',
  [PRINT_SCENARIO.invReturnPrint]: '库存退货单',
  [PRINT_SCENARIO.peisReportPrint]: '体检总检报告',
  [PRINT_SCENARIO.peisGuidePrint]: '体检指引单',
  [PRINT_SCENARIO.finPriceAdjustmentPrint]: '调价单',
  [PRINT_SCENARIO.reportListPrint]: '报表列表',
};

export const PRINT_SCENARIO_ITEMS: Array<{ label: string; scenarioCode: PrintScenario }> = [
  {
    scenarioCode: PRINT_SCENARIO.outpatientRx,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.outpatientRx],
  },
  {
    scenarioCode: PRINT_SCENARIO.outpatientTcmRx,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.outpatientTcmRx],
  },
  {
    scenarioCode: PRINT_SCENARIO.chargeReceipt,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.chargeReceipt],
  },
  {
    scenarioCode: PRINT_SCENARIO.opRegPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.opRegPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.opTreatmentPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.opTreatmentPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.opInfusionLabelPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.opInfusionLabelPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.opSpecimenBarcodePrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.opSpecimenBarcodePrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.opDailySettlementPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.opDailySettlementPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.pharmacyDailySettlementPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.pharmacyDailySettlementPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.examinationPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.examinationPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.testPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.testPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipNursingRecordPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipNursingRecordPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipFeeListPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipFeeListPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipFeeDetailPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipFeeDetailPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipTempOrderPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipTempOrderPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipLongOrderPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipLongOrderPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipDispenseSummaryPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipDispenseSummaryPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipDispenseSheetPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipDispenseSheetPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipOrderDispenseSheetPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipOrderDispenseSheetPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipOralMedSheetPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipOralMedSheetPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipPatrolCardPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipPatrolCardPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipWristbandPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipWristbandPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipBedCardPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipBedCardPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipPrepayReceiptPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipPrepayReceiptPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipOralMedLabelPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipOralMedLabelPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipInfusionLabelPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipInfusionLabelPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipExamApplyPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipExamApplyPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.ipLabApplyPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.ipLabApplyPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.invMovementPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.invMovementPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.invStocktakePrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.invStocktakePrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.invReturnPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.invReturnPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.peisReportPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.peisReportPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.peisGuidePrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.peisGuidePrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.finPriceAdjustmentPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.finPriceAdjustmentPrint],
  },
  {
    scenarioCode: PRINT_SCENARIO.reportListPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.reportListPrint],
  },
];

/** 解析场景码展示名，未知码回退为原值 */
export function resolvePrintScenarioLabel(scenarioCode: string): string {
  const trimmed = scenarioCode.trim();
  if (!trimmed) {
    return trimmed;
  }
  return PRINT_SCENARIO_LABELS[trimmed as PrintScenario] ?? trimmed;
}
