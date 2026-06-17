<script setup lang="ts">
import type {
  NurseStationDispenseSummarySlipHeaderDto,
  NurseStationDispenseSummarySlipLineDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.types';

import { computed } from 'vue';

import {
  formatSummarySlipLineQuantity,
  formatSummarySlipDateTime,
  resolveSlipStatusTagType,
  resolveSlipStatusText,
} from '../services/map-nurse-station-dispense-summary';

defineOptions({
  name: 'DispenseSummaryDetailDialog',
});

interface Props {
  header: NurseStationDispenseSummarySlipHeaderDto | null;
  lines: NurseStationDispenseSummarySlipLineDto[];
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<{
  print: [];
}>();

const visible = defineModel<boolean>('visible', { required: true });

/** 状态文案 */
const statusText = computed(() => {
  if (!props.header) return '—';
  return resolveSlipStatusText(props.header);
});

/** 状态 Tag 颜色 */
const statusTagType = computed(() => resolveSlipStatusTagType(statusText.value));

/** 单头信息项（网格展示） */
const headerInfoItems = computed(() => {
  const header = props.header;
  if (!header) return [];
  return [
    { label: '申请时间', value: formatSummarySlipDateTime(header.applyTime) },
    { label: '发药时间', value: formatSummarySlipDateTime(header.dispenseTime) },
    { label: '汇总药房', value: header.purposeLocationName?.trim() || '—' },
    { label: '申请人', value: header.applicantName?.trim() || '—' },
    { label: '领药人', value: header.receiverName?.trim() || '—' },
  ];
});

/** 关闭弹窗 */
function handleClose() {
  visible.value = false;
}

/** 触发打印 */
function handlePrint() {
  emit('print');
}
</script>

<template>
  <ElDialog
    v-model="visible"
    class="summary-detail-dialog"
    width="820px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <template #header>
      <div class="summary-detail-dialog__head">
        <span class="summary-detail-dialog__head-title">汇总单详情</span>
        <ElTag v-if="props.header" size="small" :type="statusTagType" effect="light">
          {{ statusText }}
        </ElTag>
      </div>
    </template>

    <div v-loading="props.loading" class="summary-detail-dialog__body">
      <section v-if="props.header" class="summary-detail-dialog__info">
        <div class="summary-detail-dialog__bus-no">
          {{ props.header.busNo || '—' }}
        </div>
        <div class="summary-detail-dialog__grid">
          <div
            v-for="item in headerInfoItems"
            :key="item.label"
            class="summary-detail-dialog__grid-item"
          >
            <span class="summary-detail-dialog__label">{{ item.label }}</span>
            <span class="summary-detail-dialog__value" :title="item.value">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <section class="summary-detail-dialog__section">
        <div class="summary-detail-dialog__section-head">
          <span class="summary-detail-dialog__section-title">药品明细</span>
          <span class="summary-detail-dialog__section-count">共 {{ props.lines.length }} 项</span>
        </div>

        <div class="summary-detail-dialog__table-wrap">
          <ElTable
            :data="props.lines"
            class="order-table order-table--horizontal-only w-full"
            stripe
            max-height="360"
            empty-text="暂无药品明细"
          >
            <ElTableColumn type="index" label="序号" width="56" align="center" />
            <ElTableColumn
              prop="medicationName"
              label="药品名称"
              min-width="180"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="specificationText"
              label="规格"
              min-width="110"
              show-overflow-tooltip
            />
            <ElTableColumn label="数量" min-width="96" align="center">
              <template #default="{ row }">
                {{ formatSummarySlipLineQuantity(row) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="unitPrice" label="单价" min-width="88" align="right">
              <template #default="{ row }">
                {{ row.unitPrice ?? '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="totalPrice" label="总价" min-width="88" align="right">
              <template #default="{ row }">
                {{ row.totalPrice ?? '—' }}
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="summary-detail-dialog__footer">
        <ElButton @click="handleClose">关闭</ElButton>
        <ElButton type="primary" plain :disabled="!props.header" @click="handlePrint">
          打印
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
@use '../../components/nurse-station-patient-order-list.scss';

.summary-detail-dialog__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 28px;
}

.summary-detail-dialog__head-title {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.summary-detail-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 120px;
}

.summary-detail-dialog__info {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-light);
  padding: 12px 14px;
}

.summary-detail-dialog__bus-no {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 10px;
}

.summary-detail-dialog__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
}

.summary-detail-dialog__grid-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.summary-detail-dialog__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.summary-detail-dialog__value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-detail-dialog__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.summary-detail-dialog__section-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.summary-detail-dialog__section-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-detail-dialog__table-wrap {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.summary-detail-dialog__table-wrap :deep(.order-table .el-table__inner-wrapper) {
  border-radius: 10px;
}

.summary-detail-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
