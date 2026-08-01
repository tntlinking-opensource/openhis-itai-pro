<script setup lang="ts">
import type {
  NurseStationDispenseSummaryPatientPickupGroupDto,
  NurseStationDispenseSummarySlipHeaderDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.types';

import { computed } from 'vue';

import {
  flattenPatientPickupGroupsForTable,
  formatSummarySlipDateTime,
  formatSummarySlipLineQuantity,
  resolveSlipStatusTagType,
  resolveSlipStatusText,
} from '../services/map-nurse-station-dispense-summary';

defineOptions({
  name: 'DispenseSummaryPatientPickupDialog',
});

interface Props {
  header: NurseStationDispenseSummarySlipHeaderDto | null;
  patientGroups: NurseStationDispenseSummaryPatientPickupGroupDto[];
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const visible = defineModel<boolean>('visible', { required: true });

/** 状态文案 */
const statusText = computed(() => {
  if (!props.header) return '—';
  return resolveSlipStatusText(props.header);
});

/** 状态 Tag 颜色 */
const statusTagType = computed(() => resolveSlipStatusTagType(statusText.value));

/** 单头信息项（仅单据号区外补充；页头时间/制单人外层已有，此处不重复开始～至） */
const headerInfoItems = computed(() => {
  const header = props.header;
  if (!header) return [];
  return [
    { label: '汇总药房', value: header.purposeLocationName?.trim() || '—' },
    { label: '申请人', value: header.applicantName?.trim() || '—' },
    { label: '申请时间', value: formatSummarySlipDateTime(header.applyTime) },
    { label: '发药时间', value: formatSummarySlipDateTime(header.dispenseTime) },
    { label: '领药人', value: header.receiverName?.trim() || '—' },
  ];
});

/** 平铺表格行（患者列合并） */
const tableRows = computed(() => flattenPatientPickupGroupsForTable(props.patientGroups ?? []));

/** 药品行合计（已按 medReqId 合并后的行数） */
const lineCount = computed(() => tableRows.value.length);

/** 患者列合并：姓名列 rowspan */
function handlePatientSpanMethod({
  column,
  row,
}: {
  column: { label?: string };
  row: { patientRowSpan: number };
}): { rowspan: number; colspan: number } | undefined {
  // 按列标题识别姓名列（避免 index 列导致 columnIndex 偏移）
  if (column.label !== '姓名') return undefined;
  const span = row.patientRowSpan;
  if (span > 0) {
    return { rowspan: span, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

/** 关闭抽屉 */
function handleClose() {
  visible.value = false;
}
</script>

<template>
  <ElDrawer
    v-model="visible"
    class="summary-pickup-drawer"
    size="82%"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <template #header>
      <div class="summary-pickup-drawer__head">
        <span class="summary-pickup-drawer__head-title">摆药单查询</span>
        <ElTag v-if="props.header" size="small" :type="statusTagType" effect="light">
          {{ statusText }}
        </ElTag>
      </div>
    </template>

    <div v-loading="props.loading" class="summary-pickup-drawer__body">
      <section v-if="props.header" class="summary-pickup-drawer__info">
        <div class="summary-pickup-drawer__bus-no">
          {{ props.header.busNo || '—' }}
        </div>
        <div class="summary-pickup-drawer__grid">
          <div
            v-for="item in headerInfoItems"
            :key="item.label"
            class="summary-pickup-drawer__grid-item"
          >
            <span class="summary-pickup-drawer__label">{{ item.label }}</span>
            <span class="summary-pickup-drawer__value" :title="item.value">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <section class="summary-pickup-drawer__section">
        <div class="summary-pickup-drawer__section-head">
          <span class="summary-pickup-drawer__section-title">摆药明细</span>
          <span class="summary-pickup-drawer__section-count">共 {{ lineCount }} 项</span>
        </div>

        <div class="summary-pickup-drawer__table-wrap">
          <ElTable
            :data="tableRows"
            class="order-table order-table--horizontal-only w-full"
            stripe
            height="100%"
            row-key="rowKey"
            empty-text="暂无摆药明细"
            :span-method="handlePatientSpanMethod"
          >
            <ElTableColumn type="index" label="序号" width="56" align="center" />
            <ElTableColumn label="姓名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.patientDisplay }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="药品" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.line.medicationName?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="规格" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.line.specificationText?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="数量" min-width="96" align="center">
              <template #default="{ row }">
                {{ formatSummarySlipLineQuantity(row.line) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="单位" min-width="72" align="center">
              <template #default="{ row }">
                {{ row.line.unitCodeText?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="剂量" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.line.dosageInstruction?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="用法" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.line.methodCodeText?.trim() || row.line.methodCode?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="频次" min-width="90" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.line.frequencyCodeText?.trim() || row.line.frequencyCode?.trim() || '—' }}
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="summary-pickup-drawer__footer">
        <ElButton @click="handleClose">关闭</ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped lang="scss">
@use '../../components/nurse-station-patient-order-list.scss';

.summary-pickup-drawer__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 28px;
}

.summary-pickup-drawer__head-title {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.summary-pickup-drawer__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
}

.summary-pickup-drawer__info {
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-light);
  padding: 12px 14px;
}

.summary-pickup-drawer__bus-no {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 10px;
}

.summary-pickup-drawer__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
}

.summary-pickup-drawer__grid-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.summary-pickup-drawer__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.summary-pickup-drawer__value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-pickup-drawer__section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.summary-pickup-drawer__section-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.summary-pickup-drawer__section-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.summary-pickup-drawer__section-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-pickup-drawer__table-wrap {
  flex: 1;
  min-height: 240px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.summary-pickup-drawer__table-wrap :deep(.order-table .el-table__inner-wrapper) {
  border-radius: 10px;
}

.summary-pickup-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

<!-- 抽屉 body 占满剩余高度，明细表可纵向撑满 -->
<style lang="scss">
.summary-pickup-drawer.el-drawer {
  .el-drawer__body {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding-bottom: 0;
  }
}
</style>
