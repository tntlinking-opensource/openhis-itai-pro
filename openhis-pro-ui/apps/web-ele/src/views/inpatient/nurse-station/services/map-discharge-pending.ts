import type {
  DischargePendingExecutionItemDto,
  DischargePendingMedicationItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';
import type {
  NurseStationOrderExecuteOrderType,
  NurseStationOrderExecuteWriteSlotDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';

import { formatDateTime } from '@vben/utils';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

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
  if (c.includes('LONG_PENDING_SLOTS')) return '长期医嘱仍有待执行时点';
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

/** 是否为长期已停嘱仍有待执行时点 */
export function isLongPendingSlotsReason(code?: string): boolean {
  return (code ?? '').trim().includes('LONG_PENDING_SLOTS');
}

/** 待执行计划时刻列表展示（与门禁 pending 同源） */
export function formatPendingPlanTimes(times?: string[]): string {
  if (!times || times.length === 0) return '—';
  const labels: string[] = [];
  // 按接口回传顺序拼接，空时刻跳过
  for (const raw of times) {
    const text = (raw ?? '').trim();
    if (!text) continue;
    labels.push(formatDateTime(text) || text);
  }
  if (labels.length === 0) return '—';
  return labels.join('、');
}

/** 出院待执行行 requestTable → 执行写路径 orderType */
export function requestTableToExecuteOrderType(
  requestTable?: string,
): NurseStationOrderExecuteOrderType | null {
  const table = (requestTable ?? '').trim().toLowerCase();
  if (table.includes('medication_request')) return 'MED_REQUEST';
  if (table.includes('service_request')) return 'SERVICE_REQUEST';
  return null;
}

/** 收集 LONG_PENDING_SLOTS 行上可批量不执行的写槽位 */
export function collectLongPendingSkipSlots(
  rows: DischargePendingExecutionItemDto[],
): NurseStationOrderExecuteWriteSlotDto[] {
  const items: NurseStationOrderExecuteWriteSlotDto[] = [];
  // 仅长期已停嘱仍 pending 的行参与 skip；其它阻塞原因禁止误 skip
  for (const row of rows) {
    if (!isLongPendingSlotsReason(row.blockingReason)) continue;
    const orderType = requestTableToExecuteOrderType(row.requestTable);
    const orderId = preserveApiIdForRequest(row.requestId);
    if (!orderType || !orderId) continue;
    const times = row.pendingPlanTimes ?? [];
    // 与门禁 pending 同源的每个计划时刻各写一个 IF-IP-EXE-04 槽位
    for (const planTime of times) {
      const text = (planTime ?? '').trim();
      if (!text) continue;
      items.push({ orderId, orderType, planTime: text });
    }
  }
  return items;
}
