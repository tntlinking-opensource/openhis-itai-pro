import type { BuildWorkInjury2301PayloadRequest } from './work-injury.types';

/** 2301 审批表单（UI 编辑态）。 */
export interface WorkInjury2301FormValues {
  approveType: string;
  startDate: string;
  endDate: string;
  terminReason: string;
  injureAffirmId: string;
  orgCode: string;
  orgName: string;
}

export const DEFAULT_WORK_INJURY_2301_FORM: WorkInjury2301FormValues = {
  approveType: '12',
  startDate: '',
  endDate: '',
  terminReason: '门诊工伤审批',
  injureAffirmId: '',
  orgCode: '',
  orgName: '',
};

/** 将 YYYY-MM-DD 转为平台 yyyyMMdd。 */
export function toWiPlatformDay(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return '';
  }
  return trimmed.replaceAll('-', '');
}

export function createDefaultWorkInjury2301Form(
  partial?: Partial<WorkInjury2301FormValues>,
): WorkInjury2301FormValues {
  const today = new Date();
  const yyyy = today.getFullYear();
  const mm = String(today.getMonth() + 1).padStart(2, '0');
  const dd = String(today.getDate()).padStart(2, '0');
  const startDate = `${yyyy}-${mm}-${dd}`;
  return {
    ...DEFAULT_WORK_INJURY_2301_FORM,
    startDate,
    ...partial,
  };
}

export function toWorkInjury2301Context(form: WorkInjury2301FormValues): Record<string, string> {
  const context: Record<string, string> = {
    approveType: form.approveType.trim(),
    terminReason: form.terminReason.trim(),
  };
  const startDate = toWiPlatformDay(form.startDate);
  const endDate = toWiPlatformDay(form.endDate);
  const injureAffirmId = form.injureAffirmId.trim();
  const orgCode = form.orgCode.trim();
  const orgName = form.orgName.trim();
  if (startDate) {
    context.startDate = startDate;
  }
  if (endDate) {
    context.endDate = endDate;
  }
  if (injureAffirmId) {
    context.injureAffirmId = injureAffirmId;
  }
  if (orgCode) {
    context.orgCode = orgCode;
  }
  if (orgName) {
    context.orgName = orgName;
  }
  return context;
}

export function toBuildWorkInjury2301PayloadRequest(
  encounterId: string | number,
  contractId: string | number | undefined,
  form: WorkInjury2301FormValues,
): BuildWorkInjury2301PayloadRequest {
  const context = toWorkInjury2301Context(form);
  return {
    encounterId,
    contractId,
    approveType: context.approveType,
    startDate: context.startDate,
    endDate: context.endDate,
    terminReason: context.terminReason,
    injureAffirmId: context.injureAffirmId,
    orgCode: context.orgCode,
    orgName: context.orgName,
  };
}
