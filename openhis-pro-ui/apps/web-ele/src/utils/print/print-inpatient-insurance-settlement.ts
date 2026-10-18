/**
 * 住院医保结算单打印：prepare → 库模板 → confirm（A4；scenario = ip-insuranceSettlement-print）。
 *
 * <p>版式以院点库 prt_template 为准；本文件只组 source 并调 print()。</p>
 */

import type { InpatientInsuranceSettlementPrintSource } from '#/utils/print/mappers/inpatient-insurance-settlement';

import { ElMessage } from 'element-plus';

import { print, PRINT_SCENARIO } from './runtime';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';

export type { InpatientInsuranceSettlementPrintSource as InsuranceSettlementPrintSource } from '#/utils/print/mappers/inpatient-insurance-settlement';

export interface PrintInpatientInsuranceSettlementOptions {
  source: InpatientInsuranceSettlementPrintSource;
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框 */
  background?: boolean;
}

/** 打印住院医保结算单（模板打印） */
export async function printInpatientInsuranceSettlement(
  options: InpatientInsuranceSettlementPrintSource | PrintInpatientInsuranceSettlementOptions,
) {
  const normalized: PrintInpatientInsuranceSettlementOptions =
    'source' in options && options.source
      ? options
      : { source: options as InpatientInsuranceSettlementPrintSource };

  const source: InpatientInsuranceSettlementPrintSource = {
    ...normalized.source,
    hospitalName: normalized.source.hospitalName?.trim() || resolvePrintHospitalName(),
  };

  const patientName = String(source.patientName ?? '').trim();
  if (!patientName) {
    ElMessage.warning('缺少患者姓名，无法打印医保结算单');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.ipInsuranceSettlementPrint,
    source,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.ipInsuranceSettlementPrint,
      encounterId: normalized.encounterId ?? undefined,
      paymentId: normalized.paymentId ?? undefined,
    },
    jobName: normalized.jobName ?? '医保结算单',
    selectPrinter: normalized.background ? 'never' : 'auto',
    background: normalized.background === true,
  });
}
