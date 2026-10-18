<script setup lang="ts">
import type { TagProps } from 'element-plus';
import type { TableInstance } from 'element-plus/es/components/table';

import type {
  OrderBillingCategory,
  OrderBillingTableRow,
  SurgeryAnesthBillingKind,
} from '../services/map-nurse-station-inpatient-billing';

import { adviceTherapyTypeClass } from '../../../doctor-station/advice/services/advice-list';
import { computed, nextTick, ref, watch } from 'vue';

interface SelectOptionItem {
  label: string;
  value: string;
}

interface Props {
  /** 是否允许补费子行冲销 */
  canOperateBilling?: boolean;
  category?: OrderBillingCategory;
  dept?: string;
  deptOptions?: SelectOptionItem[];
  filteredRows?: OrderBillingTableRow[];
  keyword?: string;
  /** 手麻计费：手术/麻醉筛选值 */
  kind?: SurgeryAnesthBillingKind;
  loading?: boolean;
  /** 手麻计费工具栏：手术/麻醉筛选，不展示长临与科室 */
  surgeryAnesthFilter?: boolean;
}

defineOptions({
  name: 'OrderBillingPanel',
});

const props = withDefaults(defineProps<Props>(), {
  canOperateBilling: false,
  category: 'all',
  dept: '',
  deptOptions: () => [],
  filteredRows: () => [],
  keyword: '',
  kind: 'all',
  loading: false,
  surgeryAnesthFilter: false,
});

const emit = defineEmits<{
  onCancelChildClick: [row: OrderBillingTableRow];
  onChargeDetailClick: [row: OrderBillingTableRow];
  onQuery: [];
  onSupplementFeeClick: [row: OrderBillingTableRow];
  'update:category': [value: OrderBillingCategory];
  'update:dept': [value: string];
  'update:keyword': [value: string];
  'update:kind': [value: SurgeryAnesthBillingKind];
}>();

const tableRef = ref<TableInstance>();

const categoryModel = computed({
  get: () => props.category,
  set: (value: OrderBillingCategory) => emit('update:category', value),
});
const keywordModel = computed({
  get: () => props.keyword,
  set: (value: string) => emit('update:keyword', value),
});
const deptModel = computed({
  get: () => props.dept,
  set: (value: string) => emit('update:dept', value),
});
const kindModel = computed({
  get: () => props.kind,
  set: (value: SurgeryAnesthBillingKind) => emit('update:kind', value),
});

const EXPAND_CHILD_SCROLL_THRESHOLD = 6;

/** 补费类型展示文案（过长时缩短为「补费」） */
function resolveSupplementCategoryDisplay(categoryText: string | undefined) {
  const text = String(categoryText ?? '').trim();
  if (!text || text.includes('补费')) {
    return '补费';
  }
  return text;
}

/** 补费子行是否展示冲销 */
function canCancelSupplementChild(child: OrderBillingTableRow) {
  return Boolean(props.canOperateBilling && child.canCancel);
}

/** 补费明细超过阈值时启用滚动 */
function shouldExpandChildScroll(childCount = 0) {
  return childCount > EXPAND_CHILD_SCROLL_THRESHOLD;
}

/** 父行样式类名，无子项时隐藏展开按钮 */
function resolveOrderBillingRowClassName({ row }: { row: OrderBillingTableRow }) {
  const classes = ['order-billing-table__row--parent'];
  if (!row.children?.length) {
    classes.push('order-billing-table__row--no-children');
  }
  return classes.join(' ');
}

/** 长临医嘱文字样式 class（与住院医生站一致） */
function resolveOrderBillingTherapyClass(category: OrderBillingTableRow['category']) {
  if (category === 'long') {
    return adviceTherapyTypeClass('LONG_TERM');
  }
  if (category === 'temp') {
    return adviceTherapyTypeClass('TEMPORARY');
  }
  return '';
}

/** 结算状态 → Tag 类型 */
function resolveChargeStatusTagType(statusEnum?: string): TagProps['type'] {
  const code = String(statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (!code) {
    return 'info';
  }
  if (code.includes('REFUND')) {
    return 'danger';
  }
  if (code.includes('PENDING') || code === 'PLANNED') {
    return 'warning';
  }
  if (code.includes('SETTLED') || code.includes('CHARGED') || code === 'BILLED') {
    return 'success';
  }
  return 'info';
}

/** 将时间文案拆分为日期与时间两行展示 */
function splitDateTimeDisplay(value: string) {
  const text = String(value ?? '').trim();
  if (!text || text === '—') {
    return { date: '—', time: '' };
  }
  const [date, time] = text.split(' ');
  return { date: date || text, time: time ?? '' };
}

/** 默认展开全部含子项的父医嘱 */
async function expandAllParentRows() {
  await nextTick();
  const table = tableRef.value;
  if (!table) {
    return;
  }
  for (const row of props.filteredRows) {
    if (row.children?.length) {
      table.toggleRowExpansion(row, true);
    }
  }
}

watch(
  () => props.filteredRows,
  () => {
    void expandAllParentRows();
  },
  { deep: true, immediate: true },
);
</script>

<template>
  <div class="billing-list-card order-billing-page">
    <div class="billing-list-card__toolbar order-billing-toolbar">
      <div class="billing-list-card__query-actions order-billing-toolbar__query-actions">
        <ElRadioGroup v-if="surgeryAnesthFilter" v-model="kindModel">
          <ElRadioButton label="all">全部</ElRadioButton>
          <ElRadioButton label="surgery">手术</ElRadioButton>
          <ElRadioButton label="anesthesia">麻醉</ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup v-else v-model="categoryModel">
          <ElRadioButton label="all">全部</ElRadioButton>
          <ElRadioButton label="long">长期</ElRadioButton>
          <ElRadioButton label="temp">临时</ElRadioButton>
        </ElRadioGroup>
        <ElInput
          v-model="keywordModel"
          clearable
          :placeholder="surgeryAnesthFilter ? '术式/麻醉项目名称' : '医嘱名称'"
          class="order-billing-toolbar__input"
          :class="{ 'order-billing-toolbar__input--wide': surgeryAnesthFilter }"
          @keyup.enter="emit('onQuery')"
        />
        <ElSelect
          v-if="!surgeryAnesthFilter"
          v-model="deptModel"
          clearable
          placeholder="开方科室"
          class="order-billing-toolbar__select"
        >
          <ElOption
            v-for="item in deptOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
        <ElButton type="primary" :loading="loading" @click="emit('onQuery')">查询</ElButton>
      </div>
    </div>

    <section class="order-billing-table">
      <ElTable
        ref="tableRef"
        v-loading="loading"
        :data="filteredRows"
        border
        height="100%"
        row-key="id"
        class="order-billing-table__el-table"
        :row-class-name="resolveOrderBillingRowClassName"
      >
        <ElTableColumn
          type="expand"
          width="48"
          align="center"
          class-name="order-billing-table__expand-column"
        >
          <template #default="{ row }">
            <div v-if="row.children?.length" class="order-billing-table__expand-panel">
              <div
                class="order-billing-table__expand-body"
                :class="{
                  'order-billing-table__expand-body--scroll': shouldExpandChildScroll(
                    row.children.length,
                  ),
                }"
              >
                <div
                  v-for="(child, childIndex) in row.children"
                  :key="child.id"
                  class="order-billing-table__expand-row"
                  :class="{
                    'order-billing-table__expand-row--divider': childIndex > 0,
                  }"
                >
                  <span
                    class="order-billing-table__expand-cell order-billing-table__expand-cell--type"
                  >
                    <ElTag effect="light" type="info" class="order-billing-table__compact-tag">
                      {{ resolveSupplementCategoryDisplay(child.categoryText) }}
                    </ElTag>
                  </span>
                  <div class="order-billing-table__expand-detail">
                    <div class="order-billing-table__expand-detail-main" :title="child.content">
                      <span class="order-billing-table__expand-detail-name">
                        {{ child.content }}
                      </span>
                      <span class="order-billing-table__expand-detail-operator">
                        补费人：{{ child.requesterName?.trim() || '—' }}
                      </span>
                    </div>
                    <div class="order-billing-table__expand-detail-meta">
                      <span class="order-billing-table__expand-detail-qty">{{ child.dosage }}</span>
                      <span
                        class="order-billing-table__expand-detail-status"
                        :class="`is-${resolveChargeStatusTagType(child.chargeStatusEnum)}`"
                      >
                        {{ child.executionCount }}
                      </span>
                      <span class="order-billing-table__expand-detail-time">
                        {{ child.startStopAt }}
                      </span>
                    </div>
                  </div>
                  <div class="order-billing-table__expand-actions">
                    <ElButton
                      v-if="canCancelSupplementChild(child)"
                      link
                      type="danger"
                      @click="emit('onCancelChildClick', child)"
                    >
                      冲销
                    </ElButton>
                    <span v-else class="order-billing-table__expand-actions-placeholder">—</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="类型"
          width="96"
          align="center"
          header-align="center"
          class-name="order-billing-table__type-column"
        >
          <template #default="{ row }">
            <span
              class="ip-advice-therapy-text"
              :class="resolveOrderBillingTherapyClass(row.category)"
            >
              {{ row.category === 'long' ? '长期' : '临时' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="医嘱/项目" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="order-billing-table__content order-billing-table__content--parent">
              {{ row.content }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="dosage" label="剂量/数量" width="112" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="order-billing-table__dosage--parent">{{ row.dosage }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="频次/状态" width="108" align="center">
          <template #default="{ row }">
            <span class="order-billing-table__frequency">{{ row.executionCount }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="时间" width="156" align="center">
          <template #default="{ row }">
            <div class="order-billing-table__time">
              <span class="order-billing-table__time-date">
                {{ splitDateTimeDisplay(row.startStopAt).date }}
              </span>
              <span
                v-if="splitDateTimeDisplay(row.startStopAt).time"
                class="order-billing-table__time-clock"
              >
                {{ splitDateTimeDisplay(row.startStopAt).time }}
              </span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="操作"
          width="168"
          align="center"
          fixed="right"
          class-name="order-billing-table__action-column"
        >
          <template #default="{ row }">
            <div class="order-billing-table__actions">
              <ElButton link type="primary" @click="emit('onChargeDetailClick', row)">
                费用明细
              </ElButton>
              <span class="order-billing-table__action-divider" aria-hidden="true">|</span>
              <ElButton link type="primary" @click="emit('onSupplementFeeClick', row)">
                补费
              </ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>
  </div>
</template>

<style scoped>
.order-billing-page {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.order-billing-toolbar {
  align-items: center;
  display: flex;
  gap: 8px;
  min-height: 32px;
}

.order-billing-toolbar__query-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.order-billing-toolbar__input {
  width: 160px;
}

.order-billing-toolbar__input--wide {
  width: 220px;
}

.order-billing-toolbar__select {
  width: 160px;
}

.order-billing-table {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.order-billing-table__content--parent {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.order-billing-table__dosage--parent {
  color: var(--el-text-color-primary);
}

.order-billing-table__frequency {
  color: var(--el-text-color-regular);
}

.order-billing-table__time {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.order-billing-table__time-date {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.order-billing-table__time-clock {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.order-billing-table__actions {
  align-items: center;
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 2px;
  justify-content: center;
  white-space: nowrap;
}

.order-billing-table__action-divider {
  color: var(--el-border-color);
  user-select: none;
}

.ip-advice-therapy-text {
  display: inline-block;
  font-size: 14px;
  line-height: 1.4;
  text-align: center;
  width: 100%;
}

.ip-advice-therapy-text--long {
  color: #a6745c;
  font-weight: 500;
}

.ip-advice-therapy-text--temp {
  color: #3787a5;
  font-weight: 500;
}

.order-billing-page :deep(.order-billing-table__type-column.el-table__cell) {
  padding: 0 !important;
  text-align: center;
  vertical-align: middle;
}

.order-billing-page :deep(.order-billing-table__type-column .cell) {
  display: block;
  padding: 0 !important;
  text-align: center;
  width: 100%;
}

.order-billing-page :deep(.order-billing-table__type-column .cell .el-table__indent),
.order-billing-page :deep(.order-billing-table__type-column .cell .el-table__placeholder) {
  display: none;
  width: 0 !important;
}

.order-billing-table__expand-panel {
  margin: 8px 12px 10px 56px;
  width: auto;
}

.order-billing-table__expand-body {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  width: 100%;
}

.order-billing-table__expand-body--scroll {
  max-height: 280px;
  overflow-y: auto;
}

.order-billing-table__expand-row {
  align-items: center;
  box-sizing: border-box;
  column-gap: 12px;
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 72px;
  min-height: 52px;
  padding: 10px 16px;
  width: 100%;
}

.order-billing-table__expand-row--divider {
  border-top: 1px solid var(--el-border-color-extra-light);
}

.order-billing-table__expand-cell--type {
  display: flex;
  justify-content: center;
  min-width: 0;
}

.order-billing-table__expand-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.order-billing-table__expand-detail-main {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.order-billing-table__expand-detail-name {
  color: var(--el-text-color-primary);
  flex: 1 1 220px;
  font-size: 14px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-billing-table__expand-detail-operator {
  color: var(--el-text-color-regular);
  flex: 0 0 auto;
  font-size: 13px;
  white-space: nowrap;
}

.order-billing-table__expand-detail-meta {
  align-items: center;
  color: var(--el-text-color-secondary);
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 16px;
}

.order-billing-table__expand-detail-qty {
  color: var(--el-text-color-regular);
  font-weight: 500;
  min-width: 56px;
}

.order-billing-table__expand-detail-status {
  min-width: 72px;
}

.order-billing-table__expand-detail-status.is-warning {
  color: var(--el-color-warning);
}

.order-billing-table__expand-detail-status.is-success {
  color: var(--el-color-success);
}

.order-billing-table__expand-detail-status.is-info {
  color: var(--el-text-color-secondary);
}

.order-billing-table__expand-detail-time {
  color: var(--el-text-color-secondary);
  min-width: 156px;
}

.order-billing-table__expand-actions {
  align-items: center;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.order-billing-table__expand-actions-placeholder {
  color: var(--el-text-color-placeholder);
}

.order-billing-table__compact-tag {
  vertical-align: middle;
}

.order-billing-page :deep(.order-billing-table__compact-tag.el-tag) {
  font-size: 14px;
  height: 22px;
  padding: 0 6px;
}

.order-billing-page :deep(.order-billing-table__action-column .cell) {
  overflow: visible;
  text-overflow: clip;
}

.order-billing-page :deep(.order-billing-table__expand-column.el-table__cell) {
  text-align: center;
  vertical-align: middle;
}

.order-billing-page :deep(.order-billing-table__expand-column .cell) {
  align-items: center;
  display: flex;
  height: 100%;
  justify-content: center;
  padding: 0;
}

.order-billing-page :deep(.order-billing-table__expand-column .el-table__expand-icon) {
  align-items: center;
  display: inline-flex;
  height: 24px;
  justify-content: center;
  margin: 0;
  width: 24px;
}

.order-billing-page :deep(.order-billing-table__row--no-children .el-table__expand-icon) {
  cursor: default;
  visibility: hidden;
}

.order-billing-page :deep(.el-table__expanded-cell) {
  background: var(--el-fill-color-lighter);
  padding: 8px 0;
}

.order-billing-page :deep(.el-table__expanded-cell:hover) {
  background: var(--el-fill-color-lighter) !important;
}

.order-billing-page :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.order-billing-page :deep(.el-table__header-wrapper) {
  border-bottom: 1px solid var(--el-border-color-light);
}
</style>
