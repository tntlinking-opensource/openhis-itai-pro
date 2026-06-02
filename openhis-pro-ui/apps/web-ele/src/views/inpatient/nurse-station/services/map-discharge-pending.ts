import type {
  DischargePendingExecutionItemDto,
  DischargePendingMedicationItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import { formatDateTime } from '@vben/utils';

type TherapyFilter = 'all' | 'long' | 'temporary';

/** 长临医嘱 code → 展示文案 */
export function therapyEnumToLabel(code?: string): string {
  const c = (code ?? '').trim();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  return c || '—';
}

/** 阻塞原因 code → 展示文案 */
export function blockingReasonToLabel(code?: string): string {
  const c = (code ?? '').trim();
  if (c.includes('LONG_NOT_STOPPED')) return '长期医嘱未停嘱';
  if (c.includes('TEMP_NOT_COMPLETED')) return '临时医嘱未完结';
  return c || '—';
}

/** 就诊状态 code → 出 Tab 展示文案（无 statusEnumText 时兜底） */
export function encounterStatusToDischargeLabel(code?: string, statusEnumText?: string): string {
  const text = (statusEnumText ?? '').trim();
  if (text) return text;
  const c = (code ?? '').trim();
  if (c === 'ON_HOLD') return '待出院';
  if (c === 'PENDING') return '待结算';
  if (c === 'COMPLETED') return '已结算';
  if (c === 'IN_PROGRESS') return '在院';
  if (c === 'DISCHARGED') return '已离院';
  return c || '—';
}

/** 格式化开立时间 */
export function formatDischargeReqTime(raw?: string): string {
  const text = (raw ?? '').trim();
  if (!text) return '—';
  return formatDateTime(text) || text;
}

/** 按长临筛选待执行医嘱行 */
export function filterDischargeExecutionRows(
  rows: DischargePendingExecutionItemDto[],
  therapyFilter: TherapyFilter,
): DischargePendingExecutionItemDto[] {
  if (therapyFilter === 'all') return rows;
  const target = therapyFilter === 'long' ? 'LONG_TERM' : 'TEMPORARY';
  return rows.filter((row) => (row.therapyEnum ?? '').trim() === target);
}

/** 待发退药行数量摘要 */
export function summarizeMedicationRows(rows: DischargePendingMedicationItemDto[]): string {
  if (rows.length === 0) return '无待发/待退药';
  return `${rows.length} 条待发/待退药`;
}
