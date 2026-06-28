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

/** 同一申请单首行：用于组间分隔线与组头标签 */
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

/** 同一申请单末行：用于闭合聚组边框 */
export function isServiceApplyFormGroupEnd<T extends ServiceApplyFormTableRow>(
  row: T,
  rowIndex: number,
  rows: readonly T[],
): boolean {
  const formNo = row.applyFormNo?.trim();
  if (!formNo || row.isEditor) {
    return false;
  }
  const next = rows[rowIndex + 1];
  return next?.applyFormNo?.trim() !== formNo;
}

/** 同一申请单续行（非首行）：用于去掉组内横线 */
export function isServiceApplyFormGroupContinue<T extends ServiceApplyFormTableRow>(
  row: T,
  rowIndex: number,
  rows: readonly T[],
): boolean {
  const formNo = row.applyFormNo?.trim();
  if (!formNo || row.isEditor || rowIndex <= 0) {
    return false;
  }
  return rows[rowIndex - 1]?.applyFormNo?.trim() === formNo;
}

export interface BuildServiceApplyFormRowClassNameParams<T extends ServiceApplyFormTableRow> {
  row: T;
  rowIndex: number;
  rows: readonly T[];
}

/** 申请单聚组：表格行 class-name */
export function buildServiceApplyFormRowClassName<T extends ServiceApplyFormTableRow>(
  params: BuildServiceApplyFormRowClassNameParams<T>,
): string {
  const { row, rowIndex, rows } = params;
  if (!row.applyFormNo?.trim() || row.isEditor) {
    return '';
  }
  const parts = ['is-doctor-apply-form-member'];
  if (isServiceApplyFormGroupStart(row, rowIndex, rows)) {
    parts.push('is-doctor-apply-form-group-start');
  }
  if (isServiceApplyFormGroupEnd(row, rowIndex, rows)) {
    parts.push('is-doctor-apply-form-group-end');
  }
  if (isServiceApplyFormGroupContinue(row, rowIndex, rows)) {
    parts.push('is-doctor-apply-form-group-continue');
  }
  return parts.join(' ');
}

export interface BuildServiceApplyFormCellClassNameParams<T extends ServiceApplyFormTableRow> {
  row: T;
  rowIndex: number;
  rows: readonly T[];
  columnIndex: number;
  edgeLeftIndex?: number;
  edgeRightIndex: number;
  columnFixed?: boolean | string;
}

/** 申请单聚组：全表 cell-class-name（四边框住整组行） */
export function buildServiceApplyFormCellClassName<T extends ServiceApplyFormTableRow>(
  params: BuildServiceApplyFormCellClassNameParams<T>,
): string {
  const {
    row,
    rowIndex,
    rows,
    columnIndex,
    edgeLeftIndex = 0,
    edgeRightIndex,
    columnFixed,
  } = params;
  if (!row.applyFormNo?.trim() || row.isEditor) {
    return '';
  }
  const parts = ['doctor-apply-form-cell'];
  if (isServiceApplyFormGroupStart(row, rowIndex, rows)) {
    parts.push('is-group-start');
  }
  if (isServiceApplyFormGroupEnd(row, rowIndex, rows)) {
    parts.push('is-group-end');
  }
  if (isServiceApplyFormGroupContinue(row, rowIndex, rows)) {
    parts.push('is-group-continue');
  }
  if (columnIndex === edgeLeftIndex) {
    parts.push('is-group-edge-left');
  }
  const columnFixedText = String(columnFixed ?? '').trim();
  if (columnIndex === edgeRightIndex || columnFixedText === 'right') {
    parts.push('is-group-edge-right');
  }
  return parts.join(' ');
}
