import type { PrintScenario } from './print-scenario';

import { PRINT_SCENARIO } from './print-scenario';

export const PRINT_SCENARIO_LABELS: Record<PrintScenario, string> = {
  [PRINT_SCENARIO.outpatientRx]: '门诊处方',
  [PRINT_SCENARIO.outpatientTcmRx]: '门诊中药处方',
  [PRINT_SCENARIO.chargeReceipt]: '门诊收费小票',
  [PRINT_SCENARIO.opRegPrint]: '门诊挂号小票',
  [PRINT_SCENARIO.opTreatmentPrint]: '门诊处置单',
  [PRINT_SCENARIO.opInfusionLabelPrint]: '输液瓶签',
  [PRINT_SCENARIO.opDailySettlementPrint]: '门诊费用日结单',
  [PRINT_SCENARIO.examinationPrint]: '检查申请单',
  [PRINT_SCENARIO.testPrint]: '检验申请单',
  [PRINT_SCENARIO.ipNursingRecordPrint]: '护理记录单',
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
    scenarioCode: PRINT_SCENARIO.opDailySettlementPrint,
    label: PRINT_SCENARIO_LABELS[PRINT_SCENARIO.opDailySettlementPrint],
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
];

/** 解析场景码展示名，未知码回退为原值 */
export function resolvePrintScenarioLabel(scenarioCode: string): string {
  const trimmed = scenarioCode.trim();
  if (!trimmed) {
    return trimmed;
  }
  return PRINT_SCENARIO_LABELS[trimmed as PrintScenario] ?? trimmed;
}
