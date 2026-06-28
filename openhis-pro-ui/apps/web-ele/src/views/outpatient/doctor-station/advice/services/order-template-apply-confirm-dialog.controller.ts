import type {
  OrderTemplateApplyConfirmDialogPayload,
  OrderTemplateApplyConfirmDialogResult,
  OrderTemplateApplyConfirmTemplateRow,
} from './order-template-apply-confirm.types';

import type {
  AdviceDraftBindDialogRow,
  AdviceDraftBindDialogSourceGroup,
} from './advice-draft-bind.types';

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

/** 绑定带出行（组件绑定） */
export const orderTemplateApplyConfirmDialogBindRows = ref<AdviceDraftBindDialogRow[]>([]);

/** 「不再提示」来源分组（组件绑定） */
export const orderTemplateApplyConfirmDialogBindSourceGroups = ref<
  AdviceDraftBindDialogSourceGroup[]
>([]);

/** 用户勾选「不再提示」的来源键 */
export const orderTemplateApplyConfirmDialogSkipSourceKeys = ref<string[]>([]);

function resetDialogState(): void {
  orderTemplateApplyConfirmDialogTemplateName.value = '';
  orderTemplateApplyConfirmDialogTemplateRows.value = [];
  orderTemplateApplyConfirmDialogBindRows.value = [];
  orderTemplateApplyConfirmDialogBindSourceGroups.value = [];
  orderTemplateApplyConfirmDialogSkipSourceKeys.value = [];
}

/** 打开组套套用确认弹窗；确认返回带出行编辑结果，取消返回 null */
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
  orderTemplateApplyConfirmDialogBindRows.value = payload.bindRows.map((row) => ({
    ...row,
    bindItem: { ...row.bindItem },
  }));
  orderTemplateApplyConfirmDialogBindSourceGroups.value = payload.bindSourceGroups.map((group) => ({
    ...group,
  }));
  orderTemplateApplyConfirmDialogSkipSourceKeys.value = [];
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

/** 组件内更新带出行快照 */
export function replaceOrderTemplateApplyConfirmDialogBindRows(
  rows: AdviceDraftBindDialogRow[],
): void {
  orderTemplateApplyConfirmDialogBindRows.value = rows;
}

/** 组件内更新「不再提示」勾选 */
export function replaceOrderTemplateApplyConfirmDialogSkipSourceKeys(keys: string[]): void {
  orderTemplateApplyConfirmDialogSkipSourceKeys.value = keys;
}
