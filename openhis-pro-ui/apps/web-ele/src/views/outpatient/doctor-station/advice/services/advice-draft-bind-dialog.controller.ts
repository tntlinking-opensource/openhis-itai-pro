import type {
  AdviceDraftBindDialogConfirmResult,
  AdviceDraftBindDialogRow,
  AdviceDraftBindDialogSourceGroup,
} from './advice-draft-bind.types';

import { ref } from 'vue';

type DialogPending = {
  resolve: (result: AdviceDraftBindDialogConfirmResult | null) => void;
};

const pendingState: { current: DialogPending | null } = { current: null };

/** 弹窗可见性（组件绑定） */
export const adviceDraftBindDialogOpen = ref(false);

/** 弹窗编辑行（组件绑定） */
export const adviceDraftBindDialogRows = ref<AdviceDraftBindDialogRow[]>([]);

/** 弹窗内可选「不再提示」的来源分组 */
export const adviceDraftBindDialogSourceGroups = ref<AdviceDraftBindDialogSourceGroup[]>([]);

/** 用户勾选「不再提示」的来源键 */
export const adviceDraftBindDialogSkipSourceKeys = ref<string[]>([]);

function resetDialogState(): void {
  adviceDraftBindDialogRows.value = [];
  adviceDraftBindDialogSourceGroups.value = [];
  adviceDraftBindDialogSkipSourceKeys.value = [];
}

/** 打开绑定确认弹窗；确认返回编辑后行与需写入缓存的来源键，取消返回 null */
export function openAdviceDraftBindDialog(
  rows: AdviceDraftBindDialogRow[],
  sourceGroups: AdviceDraftBindDialogSourceGroup[],
): Promise<AdviceDraftBindDialogConfirmResult | null> {
  if (pendingState.current) {
    pendingState.current.resolve(null);
  }
  adviceDraftBindDialogRows.value = rows.map((row) => ({
    ...row,
    bindItem: { ...row.bindItem },
  }));
  adviceDraftBindDialogSourceGroups.value = sourceGroups.map((group) => ({ ...group }));
  adviceDraftBindDialogSkipSourceKeys.value = [];
  adviceDraftBindDialogOpen.value = true;
  return new Promise((resolve) => {
    pendingState.current = { resolve };
  });
}

/** 弹窗确认 */
export function confirmAdviceDraftBindDialog(result: AdviceDraftBindDialogConfirmResult): void {
  const pending = pendingState.current;
  if (!pending) {
    return;
  }
  pendingState.current = null;
  adviceDraftBindDialogOpen.value = false;
  resetDialogState();
  pending.resolve(result);
}

/** 弹窗取消 */
export function cancelAdviceDraftBindDialog(): void {
  const pending = pendingState.current;
  if (!pending) {
    return;
  }
  pendingState.current = null;
  adviceDraftBindDialogOpen.value = false;
  resetDialogState();
  pending.resolve(null);
}

/** 组件内更新弹窗行快照 */
export function replaceAdviceDraftBindDialogRows(rows: AdviceDraftBindDialogRow[]): void {
  adviceDraftBindDialogRows.value = rows;
}

/** 组件内更新「不再提示」勾选 */
export function replaceAdviceDraftBindDialogSkipSourceKeys(keys: string[]): void {
  adviceDraftBindDialogSkipSourceKeys.value = keys;
}
