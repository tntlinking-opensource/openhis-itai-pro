<script setup lang="ts">
import type { AdviceDraftBindDialogSourceGroup } from '../services/advice-draft-bind.types';

import { computed } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElInputNumber,
  ElTable,
  ElTableColumn,
} from 'element-plus';
import type { CheckboxGroupValueType } from 'element-plus';

import {
  adviceDraftBindDialogOpen,
  adviceDraftBindDialogRows,
  adviceDraftBindDialogSkipSourceKeys,
  adviceDraftBindDialogSourceGroups,
  cancelAdviceDraftBindDialog,
  confirmAdviceDraftBindDialog,
  replaceAdviceDraftBindDialogRows,
  replaceAdviceDraftBindDialogSkipSourceKeys,
} from '../services/advice-draft-bind-dialog.controller';

defineOptions({ name: 'AdviceDraftBindDialog' });

/** 是否有无效数量 */
const hasInvalidQuantity = computed(() =>
  adviceDraftBindDialogRows.value.some(
    (row) => !Number.isFinite(row.quantity) || row.quantity <= 0,
  ),
);

/** 是否至少保留一条带出项 */
const hasRows = computed(() => adviceDraftBindDialogRows.value.length > 0);

/** 当前仍有带出行的来源（用于展示「不再提示」勾选） */
const activeSourceGroups = computed(() => {
  const keys = new Set(
    adviceDraftBindDialogRows.value
      .map((row) => String(row.sourceCacheKey ?? '').trim())
      .filter(Boolean),
  );
  return adviceDraftBindDialogSourceGroups.value.filter((group) => keys.has(group.sourceCacheKey));
});

/** 更新某一行的数量 */
function handleQuantityChange(index: number, value: number | undefined) {
  const next = [...adviceDraftBindDialogRows.value];
  const row = next[index];
  if (!row) {
    return;
  }
  next[index] = {
    ...row,
    quantity: Number.isFinite(value) && (value ?? 0) > 0 ? Number(value) : row.quantity,
  };
  replaceAdviceDraftBindDialogRows(next);
}

/** 删除行：仅本次不带出，不影响缓存 */
function handleRemoveRow(index: number) {
  replaceAdviceDraftBindDialogRows(adviceDraftBindDialogRows.value.filter((_, i) => i !== index));
}

/** 更新「不再提示」勾选 */
function handleSkipSourceChange(val: CheckboxGroupValueType) {
  const keys = Array.isArray(val) ? val.map((item) => String(item)) : [];
  replaceAdviceDraftBindDialogSkipSourceKeys(keys);
}

function sourceGroupLabel(group: AdviceDraftBindDialogSourceGroup): string {
  const label = String(group.sourceLabel ?? '').trim();
  const kind = String(group.sourceKindLabel ?? '').trim();
  const nameWithKind = label && kind ? `${label}（${kind}）` : label || kind;
  return nameWithKind
    ? `${nameWithKind} 的绑定项以后自动带出，不再提示`
    : '该来源的绑定项以后自动带出，不再提示';
}

function formatSourceDisplay(row: { sourceItemName: string; sourceKindLabel: string }): string {
  const name = String(row.sourceItemName ?? '').trim();
  const kind = String(row.sourceKindLabel ?? '').trim();
  if (name && kind) {
    return `${name}（${kind}）`;
  }
  return name || kind || '—';
}

/** 取消 */
function handleCancel() {
  cancelAdviceDraftBindDialog();
}

/** 确认并回传编辑后的行 */
function handleConfirm() {
  if (hasInvalidQuantity.value) {
    return;
  }
  const remainingSourceKeys = new Set(
    adviceDraftBindDialogRows.value
      .map((row) => String(row.sourceCacheKey ?? '').trim())
      .filter(Boolean),
  );
  const skipSourceKeys = adviceDraftBindDialogSkipSourceKeys.value.filter((key) =>
    remainingSourceKeys.has(key),
  );
  confirmAdviceDraftBindDialog({
    rows: adviceDraftBindDialogRows.value,
    skipSourceKeys,
  });
}
</script>

<template>
  <ElDialog
    v-model="adviceDraftBindDialogOpen"
    title="绑定带出项目确认"
    width="760px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <p class="advice-draft-bind-dialog__hint">
      以下诊疗/耗材由用法或诊疗绑定规则带出，请确认数量后继续保存（将与当前编辑医嘱一并暂存）。删除行仅对本次保存生效。
    </p>
    <ElTable :data="adviceDraftBindDialogRows" border stripe max-height="360">
      <ElTableColumn label="来源" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ formatSourceDisplay(row) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="targetItemName" label="带出项目" min-width="160" show-overflow-tooltip />
      <ElTableColumn prop="targetKindLabel" label="类型" width="72" align="center" />
      <ElTableColumn label="数量" width="140" align="center">
        <template #default="{ row, $index }">
          <div class="advice-draft-bind-dialog__qty">
            <ElInputNumber
              :model-value="row.quantity"
              :min="1"
              :step="1"
              :precision="0"
              :controls="false"
              size="small"
              @update:model-value="(value) => handleQuantityChange($index, value)"
            />
            <span v-if="row.unitName" class="advice-draft-bind-dialog__unit">{{
              row.unitName
            }}</span>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="72" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton type="danger" link size="small" @click="handleRemoveRow($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <div v-if="activeSourceGroups.length > 0 && hasRows" class="advice-draft-bind-dialog__skip">
      <ElCheckboxGroup
        :model-value="adviceDraftBindDialogSkipSourceKeys"
        @update:model-value="handleSkipSourceChange"
      >
        <ElCheckbox
          v-for="group in activeSourceGroups"
          :key="group.sourceCacheKey"
          :value="group.sourceCacheKey"
        >
          {{ sourceGroupLabel(group) }}
        </ElCheckbox>
      </ElCheckboxGroup>
    </div>

    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" :disabled="hasInvalidQuantity" @click="handleConfirm">
        确定保存
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.advice-draft-bind-dialog__hint {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}

.advice-draft-bind-dialog__skip {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.advice-draft-bind-dialog__skip :deep(.el-checkbox) {
  display: flex;
  height: auto;
  margin-right: 0;
  white-space: normal;
}

.advice-draft-bind-dialog__qty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.advice-draft-bind-dialog__unit {
  color: var(--el-text-color-regular);
  font-size: 12px;
  white-space: nowrap;
}
</style>
