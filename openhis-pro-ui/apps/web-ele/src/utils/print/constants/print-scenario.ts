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
  /** 采血贴（护士站；50×30mm 条码） */
  opSpecimenBarcodePrint: 'op-specimenBarcode-print',
  /** 门诊费用日结单 */
  opDailySettlementPrint: 'op-dailySettlement-print',
  /** 药房日结单（门诊/住院共用） */
  pharmacyDailySettlementPrint: 'pharmacy-dailySettlement-print',
  /** 检查申请单（门诊医生站） */
  examinationPrint: 'examination-print',
  /** 检验申请单（门诊医生站） */
  testPrint: 'test-print',
  /** 住院护理记录单（护士站） */
  ipNursingRecordPrint: 'ip-nursingRecord-print',
  /** 住院费用清单（护士站打印中心） */
  ipFeeListPrint: 'ip-feeList-print',
  /** 住院费用明细单（护士站费用明细；分类流水、无分类合计） */
  ipFeeDetailPrint: 'ip-feeDetail-print',
  /** 住院临时医嘱（护士站打印中心） */
  ipTempOrderPrint: 'ip-tempOrder-print',
  /** 住院长期医嘱（护士站打印中心） */
  ipLongOrderPrint: 'ip-longOrder-print',
  /** 住院汇总领药单（护士站打印中心） */
  ipDispenseSummaryPrint: 'ip-dispenseSummary-print',
  /** 住院摆药单（护士站打印中心·汇总领药） */
  ipDispenseSheetPrint: 'ip-dispenseSheet-print',
  /** 住院医嘱执行摆药单（按日已执行全部药品，一单多人，无患者签字） */
  ipOrderDispenseSheetPrint: 'ip-orderDispenseSheet-print',
  /** 住院患者用药单（按日已执行药品，一单多人，每患者签字；院点一盒一点口服长嘱按包装单位计总量） */
  ipOralMedSheetPrint: 'ip-oralMedSheet-print',
  /** 住院输液巡视卡（护士站打印中心） */
  ipPatrolCardPrint: 'ip-patrolCard-print',
  /** 住院腕带（护士站打印中心） */
  ipWristbandPrint: 'ip-wristband-print',
  /** 住院床头卡（护士站打印中心 / 入院分床） */
  ipBedCardPrint: 'ip-bedCard-print',
  /** 住院预交金收据（收费处预交金） */
  ipPrepayReceiptPrint: 'ip-prepayReceipt-print',
  /** 住院口服药贴（护士站打印中心） */
  ipOralMedLabelPrint: 'ip-oralMedLabel-print',
  /** 住院输液瓶签（护士站打印中心；勿复用门诊瓶签） */
  ipInfusionLabelPrint: 'ip-infusionLabel-print',
  /** 住院检查申请单（护士站医嘱执行） */
  ipExamApplyPrint: 'ip-examApply-print',
  /** 住院检验申请单（护士站医嘱执行） */
  ipLabApplyPrint: 'ip-labApply-print',
  /** 库存计价流转单（进货/出库/调拨/请领等） */
  invMovementPrint: 'inv-movement-print',
  /** 库存盘点盈亏单 */
  invStocktakePrint: 'inv-stocktake-print',
  /** 库存退货单 */
  invReturnPrint: 'inv-return-print',
  /** 体检总检报告 */
  peisReportPrint: 'peis-report-print',
  /** 体检指引单 */
  peisGuidePrint: 'peis-guide-print',
  /** 财务调价单 */
  finPriceAdjustmentPrint: 'fin-price-adjustment-print',
  /** 通用报表列表（A4 横向壳，动态列） */
  reportListPrint: 'report-list-print',
} as const;

export type PrintScenario = (typeof PRINT_SCENARIO)[keyof typeof PRINT_SCENARIO];

/** 库房/药房/科室库存单据打印场景（纸张+打印机合一弹窗） */
export const INV_PRINT_SCENARIO_CODES: PrintScenario[] = [
  PRINT_SCENARIO.invMovementPrint,
  PRINT_SCENARIO.invStocktakePrint,
  PRINT_SCENARIO.invReturnPrint,
];

const INV_PRINT_SCENARIO_SET = new Set<string>(INV_PRINT_SCENARIO_CODES);

/** 是否库房单据打印场景 */
export function isInvPrintScenario(scenarioCode: string): boolean {
  return INV_PRINT_SCENARIO_SET.has(scenarioCode.trim());
}
