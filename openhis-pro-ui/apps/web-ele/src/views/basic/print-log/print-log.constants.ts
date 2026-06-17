export const PRINT_EVENT_TYPE_OPTIONS = [
  { label: '预览 PREVIEW', value: 'PREVIEW' },
  { label: '打印 PRINT', value: 'PRINT' },
  { label: '导出 PDF', value: 'EXPORT_PDF' },
  { label: '导出 Excel', value: 'EXPORT_EXCEL' },
  { label: '设计试打', value: 'DESIGN_PREVIEW' },
] as const;

export const PRINT_STATUS_OPTIONS = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
] as const;

export const PRINT_TARGET_TYPE_OPTIONS = [
  { label: '模板 TEMPLATE', value: 'TEMPLATE' },
  { label: '报表 REPORT', value: 'REPORT' },
  { label: '病历 EMR', value: 'EMR' },
] as const;

export function formatPrintEventType(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const hit = PRINT_EVENT_TYPE_OPTIONS.find((o) => o.value === value.toUpperCase());
  return hit?.label ?? value;
}

export function formatPrintStatus(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const upper = value.toUpperCase();
  if (upper === 'SUCCESS') {
    return '成功';
  }
  if (upper === 'FAILED') {
    return '失败';
  }
  return value;
}

export function printStatusTagType(value?: string | null): 'success' | 'danger' | 'info' {
  const upper = (value ?? '').toUpperCase();
  if (upper === 'SUCCESS') {
    return 'success';
  }
  if (upper === 'FAILED') {
    return 'danger';
  }
  return 'info';
}

export function formatDateRangeTodayMinus(days: number): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - Math.max(0, days - 1));
  const fmt = (d: Date) => d.toISOString().slice(0, 10);
  return { from: fmt(from), to: fmt(to) };
}
