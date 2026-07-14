/**
 * 药库损益单：单据号解析与审批口径（列表队列 / 工作站页共用）。
 */

import { isSupplyStatusApproved } from '#/utils/inventory/inbound-receipt-status';

export type GainLossOrderRowLike = {
  busNo?: string;
  orderBusNo?: string;
  statusEnum?: number | string;
  statusEnum_enumText?: string;
  supplyBusNo?: string;
};

/** 列表/路由可能对单据号返回不同字段名，统一解析 */
export function resolveGainLossBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  const candidates = [row.supplyBusNo, row.busNo, row.orderBusNo];
  for (const raw of candidates) {
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

/** 是否已审批通过（同意） */
export function isGainLossApproved(row?: GainLossOrderRowLike | null): boolean {
  if (!row) return false;
  const s = row.statusEnum;
  return s === 3 || s === '3' || isSupplyStatusApproved(s);
}
