<script setup lang="ts">
import type { AdviceDraftBindDialogSourceGroup } from '../services/advice-draft-bind.types';

import { computed } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  cancelOrderTemplateApplyConfirmDialog,
  confirmOrderTemplateApplyConfirmDialog,
  orderTemplateApplyConfirmDialogBindRows,
  orderTemplateApplyConfirmDialogBindSourceGroups,
  orderTemplateApplyConfirmDialogOpen,
  orderTemplateApplyConfirmDialogSkipSourceKeys,
  orderTemplateApplyConfirmDialogTemplateName,
  orderTemplateApplyConfirmDialogTemplateRows,
  replaceOrderTemplateApplyConfirmDialogBindRows,
  replaceOrderTemplateApplyConfirmDialogSkipSourceKeys,
  replaceOrderTemplateApplyConfirmDialogTemplateRows,
} from '../services/order-template-apply-confirm-dialog.controller';

defineOptions({ name: 'OrderTemplateApplyConfirmDialog' });

/** 组套行是否存在无效数量 */
const hasInvalidTemplateQuantity = computed(() =>
  orderTemplateApplyConfirmDialogTemplateRows.value.some(
    (row) => !Number.isFinite(row.quantity) || row.quantity <= 0,
  ),
);

/** 带出行是否存在无效数量 */
const hasInvalidBindQuantity = computed(() =>
  orderTemplateApplyConfirmDialogBindRows.value.some(
    (row) => !Number.isFinite(row.quantity) || row.quantity <= 0,
  ),
);

/** 是否至少保留一条可套用项 */
const hasApplicableRows = computed(
  () =>
    orderTemplateApplyConfirmDialogTemplateRows.value.length > 0 ||
    orderTemplateApplyConfirmDialogBindRows.value.length > 0,
);

/** 当前仍有带出行的来源（用于展示「不再提示」勾选） */
const activeBindSourceGroups = computed(() => {
  const keys = new Set(
    orderTemplateApplyConfirmDialogBindRows.value
      .map((row) => String(row.sourceCacheKey ?? '').trim())
      .filter(Boolean),
  );
  return orderTemplateApplyConfirmDialogBindSourceGroups.value.filter((group) =>
    keys.has(group.sourceCacheKey),
  );
});

/** 更新某一组套行的数量 */
function handleTemplateQuantityChange(index: number, value: number | undefined) {
  const next = [...orderTemplateApplyConfirmDialogTemplateRows.value];
  const row = next[index];
  if (!row) {
    return;
  }
  next[index] = {
    ...row,
    quantity: Number.isFinite(value) && (value ?? 0) > 0 ? Number(value) : row.quantity,
  };
  replaceOrderTemplateApplyConfirmDialogTemplateRows(next);
}

/** 删除组套行：仅本次不套用，并移除以其为来源的带出行 */
function handleRemoveTemplateRow(index: number) {
  const removed = orderTemplateApplyConfirmDialogTemplateRows.value[index];
  replaceOrderTemplateApplyConfirmDialogTemplateRows(
    orderTemplateApplyConfirmDialogTemplateRows.value.filter((_, i) => i !== index),
  );
  if (!removed) {
    return;
  }
  const removedLineIndex = removed.lineIndex;
  replaceOrderTemplateApplyConfirmDialogBindRows(
    orderTemplateApplyConfirmDialogBindRows.value.filter((row) => {
      const triggerIndex = row.bindItem.triggerLineIndex;
      if (triggerIndex === undefined || triggerIndex === null) {
        return true;
      }
      return triggerIndex !== removedLineIndex;
    }),
  );
}

/** 更新某一绑定带出行的数量 */
function handleBindQuantityChange(index: number, value: number | undefined) {
  const next = [...orderTemplateApplyConfirmDialogBindRows.value];
  const row = next[index];
  if (!row) {
    return;
  }
  next[index] = {
    ...row,
    quantity: Number.isFinite(value) && (value ?? 0) > 0 ? Number(value) : row.quantity,
  };
  replaceOrderTemplateApplyConfirmDialogBindRows(next);
}

/** 删除绑定带出行：仅本次不带出 */
function handleRemoveBindRow(index: number) {
  replaceOrderTemplateApplyConfirmDialogBindRows(
    orderTemplateApplyConfirmDialogBindRows.value.filter((_, i) => i !== index),
  );
}

/** 更新「不再提示」勾选 */
function handleSkipSourceChange(keys: string[]) {
  replaceOrderTemplateApplyConfirmDialogSkipSourceKeys(keys);
}

function bindSourceGroupLabel(group: AdviceDraftBindDialogSourceGroup): string {
  const label = String(group.sourceLabel ?? '').trim();
  const kind = String(group.sourceKindLabel ?? '').trim();
  const nameWithKind = label && kind ? `${label}（${kind}）` : label || kind;
  return nameWithKind
    ? `${nameWithKind} 的绑定项以后自动带出，不再提示`
    : '该来源的绑定项以后自动带出，不再提示';
}

function formatBindSourceDisplay(row: { sourceItemName: string; sourceKindLabel: string }): string {
  const name = String(row.sourceItemName ?? '').trim();
  const kind = String(row.sourceKindLabel ?? '').trim();
  if (name && kind) {
    return `${name}（${kind}）`;
  }
  return name || kind || '—';
}

/** 取消套用 */
function handleCancel() {
  cancelOrderTemplateApplyConfirmDialog();
}

/** 确认套用组套与带出行 */
function handleConfirm() {
  if (hasInvalidTemplateQuantity.value || hasInvalidBindQuantity.value) {
    return;
  }
  if (!hasApplicableRows.value) {
    ElMessage.warning('请至少保留一条组套或带出项目');
    return;
  }
  const remainingSourceKeys = new Set(
    orderTemplateApplyConfirmDialogBindRows.value
      .map((row) => String(row.sourceCacheKey ?? '').trim())
      .filter(Boolean),
  );
  const skipSourceKeys = orderTemplateApplyConfirmDialogSkipSourceKeys.value.filter((key) =>
    remainingSourceKeys.has(key),
  );
  confirmOrderTemplateApplyConfirmDialog({
    templateRows: orderTemplateApplyConfirmDialogTemplateRows.value,
    bindRows: orderTemplateApplyConfirmDialogBindRows.value,
    skipSourceKeys,
  });
}
</script>

<template>
  <ElDialog
    v-model="orderTemplateApplyConfirmDialogOpen"
    :title="`组套套用确认${orderTemplateApplyConfirmDialogTemplateName ? `：${orderTemplateApplyConfirmDialogTemplateName}` : ''}`"
    width="960px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <p class="order-template-apply-confirm-dialog__hint">
      请确认组套内项目及自动带出的诊疗/耗材；标记「组套」为组套明细，「带」为用法或诊疗绑定规则带出项。删除或改数量仅对本次套用生效。
    </p>

    <section
      v-if="orderTemplateApplyConfirmDialogTemplateRows.length > 0"
      class="order-template-apply-confirm-dialog__section"
    >
      <div class="order-template-apply-confirm-dialog__section-title">组套项目</div>
      <ElTable :data="orderTemplateApplyConfirmDialogTemplateRows" border stripe max-height="280">
        <ElTableColumn label="标记" width="72" align="center">
          <template #default>
            <ElTag size="small" type="primary" effect="plain">组套</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="itemName" label="项目名称" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="kindLabel" label="类型" width="72" align="center" />
        <ElTableColumn label="数量" width="200" align="center">
          <template #default="{ row, $index }">
            <div class="order-template-apply-confirm-dialog__qty">
              <ElInputNumber
                class="order-template-apply-confirm-dialog__qty-input"
                :model-value="row.quantity"
                :min="0.01"
                :step="1"
                :precision="2"
                :controls="false"
                @update:model-value="(value) => handleTemplateQuantityChange($index, value)"
              />
              <span v-if="row.unitName" class="order-template-apply-confirm-dialog__unit">{{
                row.unitName
              }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="dosageText" label="剂量/说明" min-width="120" show-overflow-tooltip />
        <ElTableColumn label="操作" width="72" align="center" fixed="right">
          <template #default="{ $index }">
            <ElButton type="danger" link size="small" @click="handleRemoveTemplateRow($index)">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>

    <section
      v-if="orderTemplateApplyConfirmDialogBindRows.length > 0"
      class="order-template-apply-confirm-dialog__section"
    >
      <div class="order-template-apply-confirm-dialog__section-title">自动带出项目</div>
      <ElTable :data="orderTemplateApplyConfirmDialogBindRows" border stripe max-height="280">
        <ElTableColumn label="标记" width="72" align="center">
          <template #default>
            <ElTag size="small" type="warning" effect="plain">带</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="来源" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatBindSourceDisplay(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="targetItemName"
          label="带出项目"
          min-width="200"
          show-overflow-tooltip
        />
        <ElTableColumn prop="targetKindLabel" label="类型" width="72" align="center" />
        <ElTableColumn label="数量" width="200" align="center">
          <template #default="{ row, $index }">
            <div class="order-template-apply-confirm-dialog__qty">
              <ElInputNumber
                class="order-template-apply-confirm-dialog__qty-input"
                :model-value="row.quantity"
                :min="1"
                :step="1"
                :precision="0"
                :controls="false"
                @update:model-value="(value) => handleBindQuantityChange($index, value)"
              />
              <span v-if="row.unitName" class="order-template-apply-confirm-dialog__unit">{{
                row.unitName
              }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="72" align="center" fixed="right">
          <template #default="{ $index }">
            <ElButton type="danger" link size="small" @click="handleRemoveBindRow($index)">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>

    <div
      v-if="activeBindSourceGroups.length > 0 && orderTemplateApplyConfirmDialogBindRows.length > 0"
      class="order-template-apply-confirm-dialog__skip"
    >
      <ElCheckboxGroup
        :model-value="orderTemplateApplyConfirmDialogSkipSourceKeys"
        @update:model-value="handleSkipSourceChange"
      >
        <ElCheckbox
          v-for="group in activeBindSourceGroups"
          :key="group.sourceCacheKey"
          :value="group.sourceCacheKey"
        >
          {{ bindSourceGroupLabel(group) }}
        </ElCheckbox>
      </ElCheckboxGroup>
    </div>

    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton
        type="primary"
        :disabled="hasInvalidTemplateQuantity || hasInvalidBindQuantity || !hasApplicableRows"
        @click="handleConfirm"
      >
        确认套用
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.order-template-apply-confirm-dialog__hint {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}

.order-template-apply-confirm-dialog__section + .order-template-apply-confirm-dialog__section {
  margin-top: 16px;
}

.order-template-apply-confirm-dialog__section-title {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.order-template-apply-confirm-dialog__skip {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.order-template-apply-confirm-dialog__skip :deep(.el-checkbox) {
  display: flex;
  height: auto;
  margin-right: 0;
  white-space: normal;
}

.order-template-apply-confirm-dialog__qty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.order-template-apply-confirm-dialog__qty-input {
  width: 96px;
}

.order-template-apply-confirm-dialog__unit {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}
</style>
