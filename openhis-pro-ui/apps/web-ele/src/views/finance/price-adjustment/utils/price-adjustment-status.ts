import type { PriceAdjustmentStatusEnum } from '../types';

/** 状态 → 中文标签 */
export function priceAdjustmentStatusLabel(status: unknown): string {
  const map: Record<PriceAdjustmentStatusEnum, string> = {
    draft: '草稿',
    pending: '待审批',
    approved: '已生效',
    rejected: '已拒绝',
    deprecated: '已废弃',
  };
  const key = String(status ?? '').toLowerCase() as PriceAdjustmentStatusEnum;
  return map[key] ?? '—';
}

/** 草稿 / 已拒绝可编辑 */
export function isPriceAdjustmentDocumentEditable(status: unknown, isDraft?: boolean): boolean {
  if (isDraft) return true;
  const key = String(status ?? '').toLowerCase();
  return key === 'draft' || key === 'rejected';
}

/** 与 InvDocActions v-bind 同构的禁用态 */
export interface PriceAdjustmentDocActionFlags {
  approveDisabled: boolean;
  rejectDisabled: boolean;
  removeDisabled: boolean;
  saveAndSubmitDisabled: boolean;
  saveDisabled: boolean;
  submitApprovalDisabled: boolean;
  withdrawDisabled: boolean;
}

/** 按单据状态计算底部按钮禁用态（对齐进货单 inv-doc-actions） */
export function buildPriceAdjustmentDocActionFlags(input: {
  documentEditable: boolean;
  hasPersistedBusNo: boolean;
  isDraft: boolean;
  statusRaw: unknown;
}): PriceAdjustmentDocActionFlags {
  if (input.isDraft) {
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

  const code = String(input.statusRaw ?? '').toLowerCase();
  const ro = !input.documentEditable;

  if (code === 'pending') {
    return {
      saveDisabled: true,
      saveAndSubmitDisabled: true,
      removeDisabled: true,
      submitApprovalDisabled: true,
      withdrawDisabled: true,
      approveDisabled: false,
      rejectDisabled: false,
    };
  }

  if (code === 'draft') {
    return {
      saveDisabled: false,
      saveAndSubmitDisabled: false,
      removeDisabled: false,
      submitApprovalDisabled: false,
      withdrawDisabled: false,
      approveDisabled: true,
      rejectDisabled: true,
    };
  }

  if (code === 'rejected') {
    return {
      saveDisabled: ro,
      saveAndSubmitDisabled: ro,
      removeDisabled: true,
      submitApprovalDisabled: ro,
      withdrawDisabled: true,
      approveDisabled: true,
      rejectDisabled: true,
    };
  }

  if (code === 'approved' || code === 'deprecated') {
    return {
      saveDisabled: true,
      saveAndSubmitDisabled: true,
      removeDisabled: true,
      submitApprovalDisabled: true,
      withdrawDisabled: true,
      approveDisabled: true,
      rejectDisabled: true,
    };
  }

  return {
    saveDisabled: ro,
    saveAndSubmitDisabled: ro,
    removeDisabled: ro,
    submitApprovalDisabled: ro,
    withdrawDisabled: true,
    approveDisabled: true,
    rejectDisabled: true,
  };
}
