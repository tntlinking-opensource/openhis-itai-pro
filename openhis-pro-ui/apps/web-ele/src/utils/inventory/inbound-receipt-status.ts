/**
 * 药库进货单状态（与后端枚举 code / 部分数字形态对齐）。
 * 可编辑：仅 PENDING_APPROVAL、REJECT；本地草稿另由调用方处理。
 */

/** 后端若用 1～12 等数字表示状态时的映射（与常见 Java ordinal 对齐，可按联调再调） */
const STATUS_NUM_TO_CODE: Record<number, string> = {
  1: 'PENDING_APPROVAL',
  2: 'APPROVAL',
  3: 'AGREE',
  4: 'REJECT',
  9: 'WITHDRAW',
  10: 'PENDING_REVIEW',
  11: 'EXPIRED_INVALIDATED',
  12: 'SEND',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING_APPROVAL: '待审核',
  APPROVAL: '审核中',
  AGREE: '同意',
  REJECT: '驳回',
  WITHDRAW: '已撤回',
  PENDING_REVIEW: '待复审',
  EXPIRED_INVALIDATED: '已失效',
  SEND: '已发送',
  DRAFT: '草稿',
};

export function normalizeInboundReceiptStatusEnum(raw: unknown): string {
  if (raw === undefined || raw === null) return '';
  if (typeof raw === 'bigint') {
    const n = Number(raw);
    return Number.isFinite(n) ? (STATUS_NUM_TO_CODE[n] ?? String(raw)) : String(raw);
  }
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    const n = raw as number;
    return STATUS_NUM_TO_CODE[n] ?? String(n);
  }
  return String(raw).trim().toUpperCase();
}

/** 已落库单据是否允许改单头/明细（不含本地草稿） */
export function isInboundReceiptDocumentEditableByCode(normalizedCode: string): boolean {
  if (!normalizedCode) return false;
  if (normalizedCode === 'DRAFT') return true;
  return normalizedCode === 'PENDING_APPROVAL' || normalizedCode === 'REJECT';
}

export function isInboundReceiptDocumentEditableRaw(statusRaw: unknown): boolean {
  return isInboundReceiptDocumentEditableByCode(normalizeInboundReceiptStatusEnum(statusRaw));
}

export function inboundReceiptStatusLabel(normalizedOrRaw: unknown): string {
  const code = normalizeInboundReceiptStatusEnum(normalizedOrRaw);
  if (!code) return '—';
  return STATUS_LABELS[code] ?? code;
}

export interface InboundReceiptDocActionFlags {
  saveDisabled: boolean;
  saveAndSubmitDisabled: boolean;
  removeDisabled: boolean;
  submitApprovalDisabled: boolean;
  withdrawDisabled: boolean;
  approveDisabled: boolean;
  rejectDisabled: boolean;
}

/**
 * 底部单据按钮禁用态（与 documentEditable、状态码组合）。
 * 草稿：可保存/保存并提交/删除草稿；提交/撤回/审批另走落库后逻辑。
 */
export function buildInboundReceiptDocActionFlags(params: {
  documentEditable: boolean;
  hasPersistedBusNo: boolean;
  isDraft: boolean;
  statusRaw: unknown;
}): InboundReceiptDocActionFlags {
  if (params.isDraft) {
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

  const code = normalizeInboundReceiptStatusEnum(params.statusRaw);
  const ro = !params.documentEditable;

  const canResubmit =
    params.hasPersistedBusNo && (code === 'REJECT' || code === 'WITHDRAW' || code === 'DRAFT');

  return {
    saveDisabled: ro,
    saveAndSubmitDisabled: ro,
    removeDisabled: ro,
    submitApprovalDisabled: !canResubmit,
    withdrawDisabled: !(code === 'PENDING_APPROVAL' || code === 'APPROVAL'),
    approveDisabled: !(code === 'APPROVAL' || code === 'PENDING_REVIEW'),
    rejectDisabled: !(code === 'APPROVAL' || code === 'PENDING_REVIEW'),
  };
}
