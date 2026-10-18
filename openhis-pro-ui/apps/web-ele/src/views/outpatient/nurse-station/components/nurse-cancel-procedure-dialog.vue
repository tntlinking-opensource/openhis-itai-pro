<script setup lang="ts">
import type { NetRemainingProcedureRecordItemDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import { computed, ref, watch } from 'vue';

import { formatDateTime } from '@zxplat/utils';

import { ElButton, ElTable, ElTableColumn } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

defineOptions({
  name: 'NurseCancelProcedureDialog',
});

const props = withDefaults(defineProps<Props>(), {
  itemLabel: '',
  submitting: false,
});

const emit = defineEmits<{
  confirm: [procedureIds: string[]];
}>();

interface Props {
  itemLabel?: string;
  records: NetRemainingProcedureRecordItemDto[];
  submitting?: boolean;
}

const open = defineModel<boolean>('open', { default: false });

const selectedProcedureIds = ref<string[]>([]);
const tableRef = ref<InstanceType<typeof ElTable> | null>(null);

const dialogTitle = computed(() => {
  const name = props.itemLabel?.trim();
  return name ? `取消执行 — ${name}` : '取消执行';
});

/** 格式化执行记录时间列 */
function formatRecordTime(row: NetRemainingProcedureRecordItemDto): string {
  const raw = (row.occurrenceTime ?? row.recordedTime ?? '').trim();
  return raw ? formatDateTime(raw) : '—';
}

/** 同步表格勾选到 procedureId 列表 */
function onSelectionChange(rows: NetRemainingProcedureRecordItemDto[]) {
  selectedProcedureIds.value = rows
    .map((r) => String(r.procedureId ?? '').trim())
    .filter((id) => id.length > 0);
}

/** 提交所选执行记录取消 */
function handleConfirm() {
  if (selectedProcedureIds.value.length === 0) {
    return;
  }
  emit('confirm', [...selectedProcedureIds.value]);
}

/** 关闭时清空勾选 */
function handleClosed() {
  selectedProcedureIds.value = [];
  tableRef.value?.clearSelection();
}

watch(open, (visible) => {
  if (!visible) {
    handleClosed();
  }
});
</script>

<template>
  <AppDialog
    v-model="open"
    :title="dialogTitle"
    width="640px"
    destroy-on-close
    @closed="handleClosed"
  >
    <p class="cancel-procedure-hint text-sm">
      请选择要取消的执行记录（仅展示尚未被对冲的已完成记录）。
    </p>
    <ElTable
      ref="tableRef"
      :data="records"
      row-key="procedureId"
      border
      stripe
      max-height="360"
      @selection-change="onSelectionChange"
    >
      <ElTableColumn type="selection" width="42" align="center" />
      <ElTableColumn label="记录时间" width="168" align="center">
        <template #default="{ row }">
          {{ formatRecordTime(row) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="statusEnumText" label="状态" width="88" align="center">
        <template #default="{ row }">
          {{ row.statusEnumText ?? row.statusEnum ?? '已完成' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="resultText" label="结果摘要" min-width="160" show-overflow-tooltip />
    </ElTable>
    <template #footer>
      <ElButton @click="open = false">关闭</ElButton>
      <ElButton
        type="danger"
        :loading="submitting"
        :disabled="selectedProcedureIds.length === 0"
        @click="handleConfirm"
      >
        确认取消
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.cancel-procedure-hint {
  margin: 0 0 0.75rem;
  color: var(--el-text-color-regular);
}
</style>
