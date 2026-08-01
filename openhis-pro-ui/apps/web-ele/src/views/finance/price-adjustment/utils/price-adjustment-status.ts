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
  printDisabled: boolean;
  removeDisabled: boolean;
  saveAndSubmitDisabled: boolean;
  saveDisabled: boolean;
}

/** 按单据状态计算底部按钮禁用态（对齐进货单 inv-doc-actions） */
export function buildPriceAdjustmentDocActionFlags(input: {
  documentEditable: boolean;
  hasPersistedBusNo: boolean;
  isDraft: boolean;
  statusRaw: unknown;
}): PriceAdjustmentDocActionFlags {
  const printDisabled = !input.hasPersistedBusNo;

  if (input.isDraft) {
    return {
      printDisabled,
      saveDisabled: false,
      saveAndSubmitDisabled: false,
      removeDisabled: false,
    };
  }

  const code = String(input.statusRaw ?? '').toLowerCase();
  const ro = !input.documentEditable;

  if (code === 'pending') {
    return {
      printDisabled,
      saveDisabled: true,
      saveAndSubmitDisabled: true,
      removeDisabled: true,
    };
  }

  if (code === 'draft') {
    return {
      printDisabled,
      saveDisabled: false,
      saveAndSubmitDisabled: false,
      removeDisabled: false,
    };
  }

  if (code === 'rejected') {
    return {
      printDisabled,
      saveDisabled: ro,
      saveAndSubmitDisabled: ro,
      removeDisabled: true,
    };
  }

  if (code === 'approved' || code === 'deprecated') {
    return {
      printDisabled,
      saveDisabled: true,
      saveAndSubmitDisabled: true,
      removeDisabled: true,
    };
  }

  return {
    printDisabled,
    saveDisabled: ro,
    saveAndSubmitDisabled: ro,
    removeDisabled: ro,
  };
}
