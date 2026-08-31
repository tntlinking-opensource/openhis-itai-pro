<script setup lang="ts">
import type { TableInstance } from 'element-plus/es/components/table';

import type { ReturnMedicineDetailRow, ReturnMedicineQueuePatient } from './types';

import { computed, nextTick, ref, watch } from 'vue';

import { ElButton, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { MSFX_HIS_SCENE } from '#/msfx/constants';
import MsfxUploadStatusBar from '#/msfx/components/msfx-upload-status-bar.vue';
import { formatMoneyYuanWithSymbol, sumMoneyThenSettle } from '#/utils/format/money-scale';

defineOptions({
  name: 'ReturnMedicineDetailCenter',
});

const props = withDefaults(
  defineProps<{
    actionDisabled?: boolean;
    detailLoading?: boolean;
    detailRows: ReturnMedicineDetailRow[];
    /** PENDING_REFUND | REFUNDED */
    refundQueueStatus: string;
    selectedPatient: null | ReturnMedicineQueuePatient;
    /** 码上放心场景，默认门诊退药 */
    msfxHisScene?: string;
  }>(),
  {
    actionDisabled: false,
    detailLoading: false,
    msfxHisScene: MSFX_HIS_SCENE.OP_RETURN,
  },
);

const emit = defineEmits<{
  confirmReturn: [];
}>();

const detailTableRef = ref<null | TableInstance>(null);

/**
 * 码上放心上传键：优先勾选行共同 busNo，否则取明细首个非空 busNo。
 */
const msfxBusNo = computed(() => {
  const selected = (detailTableRef.value?.getSelectionRows() ?? []) as ReturnMedicineDetailRow[];
  const fromSelected = selected.map((r) => String(r.busNo ?? '').trim()).filter((b) => b !== '');
  if (fromSelected.length > 0) {
    return fromSelected[0]!;
  }
  for (const row of props.detailRows) {
    const b = String(row.busNo ?? '').trim();
    if (b) return b;
  }
  return '';
});

const isPendingQueue = computed(() => props.refundQueueStatus === 'PENDING_REFUND');

const canOperate = computed(
  () => isPendingQueue.value && !props.actionDisabled && !!props.selectedPatient,
);

const tableEmptyText = computed(() => {
  if (!props.selectedPatient) return '请从左侧选择退药患者';
  return isPendingQueue.value ? '暂无待退药药品明细' : '暂无已退药记录';
});

/** 当前明细总价合计（有明细才展示） */
const detailTotalAmountText = computed(() => {
  if (props.detailRows.length === 0) return '';
  // 只累加有有效总价的行
  const amounts = props.detailRows
    .map((row) => row.totalPrice)
    .filter((price): price is number => price != null);
  return formatMoneyYuanWithSymbol(sumMoneyThenSettle(amounts));
});

/** 退药状态 → 标签类型 */
function refundStatusTagType(refundEnum: string) {
  if (refundEnum === 'PENDING_REFUND') return 'warning' as const;
  if (refundEnum === 'REFUNDED') return 'success' as const;
  if (refundEnum === 'DECLINED') return 'danger' as const;
  return 'info' as const;
}

/** 仅待退药行可勾选 */
function rowSelectable(row: ReturnMedicineDetailRow) {
  return row.selectable;
}

/** 取当前勾选的明细行 */
function getSelectedDetailRows(): ReturnMedicineDetailRow[] {
  return (detailTableRef.value?.getSelectionRows() ?? []) as ReturnMedicineDetailRow[];
}

/** 清空表格勾选 */
async function clearDetailSelection() {
  await nextTick();
  detailTableRef.value?.clearSelection();
}

/** 待退药明细加载后默认全选 */
watch(
  () => {
    const pid = props.selectedPatient?.id ?? '';
    const keys = props.detailRows.map((r) => `${r.id}:${r.selectable}`).join('|');
    return { pid, keys, loading: props.detailLoading, can: canOperate.value };
  },
  async ({ pid, keys, loading, can }) => {
    if (!pid || loading) return;
    await nextTick();
    const table = detailTableRef.value;
    if (!table) return;
    table.clearSelection();
    if (!can || !keys) return;
    for (const row of props.detailRows) {
      if (row.selectable) table.toggleRowSelection(row, true);
    }
  },
  { flush: 'post' },
);

defineExpose({
  clearDetailSelection,
  getSelectedDetailRows,
});
</script>

<template>
  <section class="flex h-full min-h-0 flex-col overflow-hidden">
    <section class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-3 pb-2 pt-2">
      <header
        class="return-detail-toolbar flex flex-col gap-2 border-b border-[var(--el-border-color-lighter)] pb-2"
      >
        <SectionTitle title="退药明细（药品）" class="!mb-0 shrink-0" />
        <p
          v-if="selectedPatient"
          class="return-patient-summary m-0 flex flex-wrap gap-x-4 gap-y-1 text-sm text-[var(--el-text-color-regular)]"
        >
          <span class="font-medium text-[var(--el-text-color-primary)]">{{
            selectedPatient.patientName
          }}</span>
          <span>{{ selectedPatient.genderEnumText }} / {{ selectedPatient.ageLabel }}</span>
          <span>就诊号：{{ selectedPatient.visitNo }}</span>
          <span>科室：{{ selectedPatient.departmentName }}</span>
          <span>就诊：{{ selectedPatient.visitTimeLabel }}</span>
          <ElTag
            :type="refundStatusTagType(selectedPatient.refundEnum)"
            effect="light"
            size="small"
          >
            {{ selectedPatient.refundEnumText }}
          </ElTag>
        </p>
        <div class="mb-2 flex w-full min-w-0 flex-wrap items-center justify-between gap-1">
          <div class="flex flex-wrap items-center gap-2">
            <ElButton type="primary" :disabled="!canOperate" @click="emit('confirmReturn')">
              确认退药
            </ElButton>
            <MsfxUploadStatusBar
              :bus-no="msfxBusNo"
              :his-scene-enum="msfxHisScene"
              :need-supplier-ent-id="false"
              compact
            />
          </div>
          <div
            v-if="selectedPatient && detailTotalAmountText"
            class="return-detail-total shrink-0 text-sm"
          >
            <span class="return-detail-total__label">合计金额</span>
            <strong class="return-detail-total__value">{{ detailTotalAmountText }}</strong>
          </div>
        </div>
      </header>
      <div
        v-loading="detailLoading"
        class="return-detail-body-wrap flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
      >
        <ElTable
          v-if="selectedPatient && detailRows.length > 0"
          ref="detailTableRef"
          :data="detailRows"
          border
          stripe
          height="100%"
          row-key="id"
          class="return-detail-table w-full min-w-0"
          :empty-text="tableEmptyText"
        >
          <ElTableColumn
            v-if="isPendingQueue"
            type="selection"
            width="48"
            align="center"
            :selectable="rowSelectable"
          />
          <ElTableColumn type="index" label="序号" width="56" align="center" />
          <ElTableColumn prop="itemName" label="药品名称" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="lotNumber" label="批号" width="110" show-overflow-tooltip />
          <ElTableColumn prop="quantity" label="应退数量" width="88" align="center" />
          <ElTableColumn prop="unitText" label="单位" width="72" align="center" />
          <ElTableColumn prop="totalPriceLabel" label="总价" width="88" align="right" />
          <ElTableColumn prop="doctorName" label="开单医生" width="96" show-overflow-tooltip />
          <ElTableColumn label="退药状态" width="96" align="center">
            <template #default="{ row }">
              <ElTag :type="refundStatusTagType(row.refundEnum)" effect="light">
                {{ row.refundEnumText }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="reqStatusText" label="请求状态" width="96" show-overflow-tooltip />
          <ElTableColumn prop="traceNo" label="追溯码" min-width="120">
            <template #default="{ row }">
              <TraceCodeTooltip :value="row.traceNo" />
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty
          v-else-if="selectedPatient && !detailLoading"
          class="return-detail-empty flex flex-1 flex-col justify-center"
          :description="tableEmptyText"
        />
        <ElEmpty
          v-else-if="!selectedPatient"
          class="return-detail-empty flex flex-1 flex-col justify-center"
          description="请从左侧选择退药患者"
        />
      </div>
    </section>
  </section>
</template>

<style scoped>
:deep(.return-detail-table.el-table) {
  --el-table-header-bg-color: var(--el-fill-color-light);
}

:deep(.return-detail-table .el-table__header .cell) {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.return-detail-total {
  display: inline-flex;
  gap: 8px;
  align-items: baseline;
  line-height: 20px;
}

.return-detail-total__label {
  color: var(--el-text-color-regular);
}

.return-detail-total__value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
