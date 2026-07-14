/** 打印业务场景码（稳定，写死在业务页；对应 prt_template.scenario_code） */
export const PRINT_SCENARIO = {
  /** 门诊处方（西药/中成药） */
  outpatientRx: 'op-rx-print',
  /** 门诊中药处方 */
  outpatientTcmRx: 'op-tcm-rx-print',
  /** 门诊收费小票 */
  chargeReceipt: 'op-charge-print',
  /** 门诊挂号小票 */
  opRegPrint: 'op-reg-print',
  /** 门诊处置单（护士站） */
  opTreatmentPrint: 'op-treatment-print',
  /** 输液瓶签（护士站） */
  opInfusionLabelPrint: 'op-infusionLabel-print',
  /** 门诊费用日结单 */
  opDailySettlementPrint: 'op-dailySettlement-print',
  /** 检查申请单（门诊医生站） */
  examinationPrint: 'examination-print',
  /** 检验申请单（门诊医生站） */
  testPrint: 'test-print',
  /** 住院护理记录单（护士站） */
  ipNursingRecordPrint: 'ip-nursingRecord-print',
} as const;

export type PrintScenario = (typeof PRINT_SCENARIO)[keyof typeof PRINT_SCENARIO];
