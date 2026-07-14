<script setup lang="ts">
import { computed } from 'vue';

import {
  ElButton,
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
  orderTemplateApplyConfirmDialogOpen,
  orderTemplateApplyConfirmDialogTemplateName,
  orderTemplateApplyConfirmDialogTemplateRows,
  replaceOrderTemplateApplyConfirmDialogTemplateRows,
} from '../services/order-template-apply-confirm-dialog.controller';

defineOptions({ name: 'OrderTemplateApplyConfirmDialog' });

/** 组套行是否存在无效数量 */
const hasInvalidTemplateQuantity = computed(() =>
  orderTemplateApplyConfirmDialogTemplateRows.value.some(
    (row) => !Number.isFinite(row.quantity) || row.quantity <= 0,
  ),
);

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

/** 删除组套行：仅本次不套用 */
function handleRemoveTemplateRow(index: number) {
  replaceOrderTemplateApplyConfirmDialogTemplateRows(
    orderTemplateApplyConfirmDialogTemplateRows.value.filter((_, i) => i !== index),
  );
}

/** 取消套用 */
function handleCancel() {
  cancelOrderTemplateApplyConfirmDialog();
}

/** 确认套用组套 */
function handleConfirm() {
  if (hasInvalidTemplateQuantity.value) {
    return;
  }
  if (orderTemplateApplyConfirmDialogTemplateRows.value.length === 0) {
    ElMessage.warning('请至少保留一条组套项目');
    return;
  }
  confirmOrderTemplateApplyConfirmDialog({
    templateRows: orderTemplateApplyConfirmDialogTemplateRows.value,
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
      请确认组套内项目；删除或改数量仅对本次套用生效。
    </p>

    <ElTable :data="orderTemplateApplyConfirmDialogTemplateRows" border stripe max-height="420">
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

    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton
        type="primary"
        :disabled="
          hasInvalidTemplateQuantity || orderTemplateApplyConfirmDialogTemplateRows.length === 0
        "
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
