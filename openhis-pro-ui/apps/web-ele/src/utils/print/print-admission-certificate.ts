/**
 * 住院证打印：prepare → hiprint 模板 → confirm（A4；scenario = ip-admissionCertificate-print）。
 */

import type { InpatientAdmissionCertificatePrintSource } from '#/utils/print/mappers/inpatient-admission-certificate';

import { ElMessage } from 'element-plus';

import { print, PRINT_SCENARIO } from './runtime';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';

export type { InpatientAdmissionCertificatePrintSource as AdmissionCertificatePrintSource } from '#/utils/print/mappers/inpatient-admission-certificate';
export {
  formatAdmissionCertificatePrepayAmount,
  resolveAdmissionCertificateDiagnosisText,
  resolveAdmissionCertificateGenderText,
} from '#/utils/print/mappers/inpatient-admission-certificate';

export interface PrintAdmissionCertificateOptions {
  source: InpatientAdmissionCertificatePrintSource;
  encounterId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框 */
  background?: boolean;
}

/** 打印住院证（模板打印） */
export async function printAdmissionCertificate(
  options: InpatientAdmissionCertificatePrintSource | PrintAdmissionCertificateOptions,
) {
  const normalized: PrintAdmissionCertificateOptions =
    'source' in options && options.source
      ? options
      : { source: options as InpatientAdmissionCertificatePrintSource };

  const source: InpatientAdmissionCertificatePrintSource = {
    ...normalized.source,
    hospitalName: normalized.source.hospitalName?.trim() || resolvePrintHospitalName(),
  };

  const patientName = String(source.patientName ?? '').trim();
  if (!patientName) {
    ElMessage.warning('缺少患者姓名，无法打印住院证');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.ipAdmissionCertificatePrint,
    source,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.ipAdmissionCertificatePrint,
      encounterId: normalized.encounterId ?? undefined,
    },
    jobName: normalized.jobName ?? '住院证',
    selectPrinter: normalized.background ? 'never' : 'auto',
    background: normalized.background === true,
  });
}
