/**
 * 药库出库单底部操作条禁用态。
 * 后端 `statusEnum` 可能为 SupplyStatus 排序号或 {@code getCode()} 字符串（如 PENDING_APPROVAL），须先规范化。
 * 可编辑明细/单头：新建草稿、或待审核(1)。
 */

import type { InboundReceiptDocActionFlags } from '#/utils/inventory/inbound-receipt-status';

const STATUS_SORT_LABELS: Record<number, string> = {
  1: '待审核',
  2: '审核中',
  3: '同意',
  4: '驳回',
  9: '已撤回',
  10: '待复审',
  11: '已失效',
  12: '已发送',
};

/** SupplyStatus code → 排序号（与进货/药房入库对齐） */
const STATUS_CODE_TO_SORT: Record<string, number> = {
  PENDING_APPROVAL: 1,
  APPROVAL: 2,
  AGREE: 3,
  REJECT: 4,
  WITHDRAW: 9,
  PENDING_REVIEW: 10,
  EXPIRED_INVALIDATED: 11,
  SEND: 12,
};

const STATUS_CODE_LABELS: Record<string, string> = {
  PENDING_APPROVAL: '待审核',
  APPROVAL: '审核中',
  AGREE: '同意',
  REJECT: '驳回',
  WITHDRAW: '已撤回',
  PENDING_REVIEW: '待复审',
  EXPIRED_INVALIDATED: '已失效',
  SEND: '已发送',
};

/** 将供应状态（排序号或 code 字符串）规范为出库页门控用的排序号。 */
export function normalizeStockOutStatusSort(v: unknown): number | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  const s = String(v).trim().toUpperCase();
  if (!s) return undefined;
  if (STATUS_CODE_TO_SORT[s] !== undefined) return STATUS_CODE_TO_SORT[s];
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

export function stockOutStatusLabel(statusRaw?: unknown): string {
  if (statusRaw === undefined || statusRaw === null) return '—';
  const sort = normalizeStockOutStatusSort(statusRaw);
  if (sort !== undefined && STATUS_SORT_LABELS[sort]) {
    return STATUS_SORT_LABELS[sort]!;
  }
  const code = String(statusRaw).trim().toUpperCase();
  if (code && STATUS_CODE_LABELS[code]) return STATUS_CODE_LABELS[code]!;
  return String(statusRaw);
}

/**
 * 与 `InvDocActions` 对齐的禁用组合；草稿态与进货单一致。
 */
export function buildStockOutDocActionFlags(p: {
  /** 无后端单号（右侧本地新建） */
  isDraft: boolean;
  hasPersistedBusNo: boolean;
  /** 排序号或 SupplyStatus code */
  status?: null | number | string;
}): InboundReceiptDocActionFlags {
  if (p.isDraft) {
    return {
      saveDisabled: false,
      saveAndSubmitDisabled: false,
      removeDisabled: false,
      submitApprovalDisabled: true,
      withdrawDisabled: true,
      approveDisabled: true,
      rejectDisabled: true,
    };
  }

  const s = normalizeStockOutStatusSort(p.status);
  const has = p.hasPersistedBusNo;
  const lineEditable = s === 1;
  const submitOk = has && (s === 1 || s === 9);

  return {
    saveDisabled: !lineEditable,
    saveAndSubmitDisabled: !lineEditable,
    removeDisabled: !(has && s === 1),
    submitApprovalDisabled: !submitOk,
    withdrawDisabled: !(has && s === 2),
    approveDisabled: !(has && s === 2),
    rejectDisabled: !(has && s === 2),
  };
}
