import type {
  OrderTemplateApplyConfirmDialogPayload,
  OrderTemplateApplyConfirmDialogResult,
  OrderTemplateApplyConfirmTemplateRow,
} from './order-template-apply-confirm.types';

import { ref } from 'vue';

type DialogPending = {
  resolve: (result: OrderTemplateApplyConfirmDialogResult | null) => void;
};

const pendingState: { current: DialogPending | null } = { current: null };

/** 弹窗可见性（组件绑定） */
export const orderTemplateApplyConfirmDialogOpen = ref(false);

/** 组套名称（组件绑定） */
export const orderTemplateApplyConfirmDialogTemplateName = ref('');

/** 组套行（组件绑定） */
export const orderTemplateApplyConfirmDialogTemplateRows = ref<
  OrderTemplateApplyConfirmTemplateRow[]
>([]);

function resetDialogState(): void {
  orderTemplateApplyConfirmDialogTemplateName.value = '';
  orderTemplateApplyConfirmDialogTemplateRows.value = [];
}

/** 打开组套套用确认弹窗；确认返回编辑后的组套行，取消返回 null */
export function openOrderTemplateApplyConfirmDialog(
  payload: OrderTemplateApplyConfirmDialogPayload,
): Promise<OrderTemplateApplyConfirmDialogResult | null> {
  if (pendingState.current) {
    pendingState.current.resolve(null);
  }
  orderTemplateApplyConfirmDialogTemplateName.value = payload.templateName;
  orderTemplateApplyConfirmDialogTemplateRows.value = payload.templateRows.map((row) => ({
    ...row,
  }));
  orderTemplateApplyConfirmDialogOpen.value = true;
  return new Promise((resolve) => {
    pendingState.current = { resolve };
  });
}

/** 弹窗确认 */
export function confirmOrderTemplateApplyConfirmDialog(
  result: OrderTemplateApplyConfirmDialogResult,
): void {
  const pending = pendingState.current;
  if (!pending) {
    return;
  }
  pendingState.current = null;
  orderTemplateApplyConfirmDialogOpen.value = false;
  resetDialogState();
  pending.resolve(result);
}

/** 弹窗取消 */
export function cancelOrderTemplateApplyConfirmDialog(): void {
  const pending = pendingState.current;
  if (!pending) {
    return;
  }
  pendingState.current = null;
  orderTemplateApplyConfirmDialogOpen.value = false;
  resetDialogState();
  pending.resolve(null);
}

/** 组件内更新组套行快照 */
export function replaceOrderTemplateApplyConfirmDialogTemplateRows(
  rows: OrderTemplateApplyConfirmTemplateRow[],
): void {
  orderTemplateApplyConfirmDialogTemplateRows.value = rows;
}
