/** 检验/检查/治疗申请单号前缀（与后端 AggregateTypeConstants.REQUISITION_APPLICATION 一致） */
export const SERVICE_APPLY_FORM_PRESCRIPTION_NO_PREFIX = 'AR';

/** 表格行是否带申请单整单号（用于聚组展示） */
export interface ServiceApplyFormTableRow {
  applyFormNo?: string;
  isEditor?: boolean;
}

/** 判断 prescriptionNo 是否为 AR 前缀申请单整单号 */
export function isServiceApplyFormPrescriptionNo(prescriptionNo?: null | string): boolean {
  const value = String(prescriptionNo ?? '').trim();
  return value.length > 0 && value.startsWith(SERVICE_APPLY_FORM_PRESCRIPTION_NO_PREFIX);
}

/** 解析列表行申请单整单号（非 AR 前缀返回 undefined） */
export function resolveServiceApplyFormNo(prescriptionNo?: null | string): string | undefined {
  if (!isServiceApplyFormPrescriptionNo(prescriptionNo)) {
    return undefined;
  }
  return String(prescriptionNo).trim();
}

/** 申请单聚邻排序键：AR 前缀申请单优先成组 */
export function resolveServiceApplyFormSortKey(prescriptionNo?: null | string): string {
  if (isServiceApplyFormPrescriptionNo(prescriptionNo)) {
    return `0_${String(prescriptionNo).trim()}`;
  }
  const trimmed = String(prescriptionNo ?? '').trim();
  if (trimmed) {
    return `1_${trimmed}`;
  }
  return '2_';
}

/** 同一申请单首行：用于组头申请单号 Tag */
export function isServiceApplyFormGroupStart<T extends ServiceApplyFormTableRow>(
  row: T,
  rowIndex: number,
  rows: readonly T[],
): boolean {
  const formNo = row.applyFormNo?.trim();
  if (!formNo || row.isEditor) {
    return false;
  }
  if (rowIndex <= 0) {
    return true;
  }
  const previous = rows[rowIndex - 1];
  return previous?.applyFormNo?.trim() !== formNo;
}
