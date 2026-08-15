<script setup lang="ts">
import type { TagProps } from 'element-plus';

import type {
  NurseStationCostDetailLineDto,
  NurseStationCostDetailPresentationMode,
  NurseStationCostDetailPresentationOptionDto,
  NurseStationCostDetailStatusFilter,
  NurseStationCostDetailTotalsDto,
  NurseStationCostSummaryActivityLineDto,
  NurseStationCostSummaryLineDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';

import { computed } from 'vue';

import {
  buildCostSummaryLineRowKey,
  formatCostDetailMoney,
  formatCostDetailQuantity,
  formatCostDetailUnitPrice,
  formatCostSummaryActivityQuantity,
  formatCostSummaryQuantity,
  resolveCostDetailDisplayDateTime,
} from '../services/map-nurse-station-cost-detail';

type CostDetailQuickDay = 'today' | 'yesterday';

interface Props {
  dateRange?: string[];
  detailLines?: NurseStationCostDetailLineDto[];
  keyword?: string;
  loading?: boolean;
  /** 费用清单打印中 */
  printing?: boolean;
  /** 费用明细单打印中 */
  printingDetail?: boolean;
  presentationMode?: NurseStationCostDetailPresentationMode;
  presentationOptions?: NurseStationCostDetailPresentationOptionDto[];
  quickDay?: CostDetailQuickDay;
  statusEnum?: NurseStationCostDetailStatusFilter;
  statusFilterOptions?: NurseStationCostDetailPresentationOptionDto[];
  summaryLines?: NurseStationCostSummaryLineDto[];
  totals?: NurseStationCostDetailTotalsDto | null;
}

defineOptions({
  name: 'InpatientNurseStationCostDetailPanel',
});

const props = withDefaults(defineProps<Props>(), {
  dateRange: () => [],
  detailLines: () => [],
  keyword: '',
  loading: false,
  printing: false,
  printingDetail: false,
  presentationMode: 'DETAIL',
  presentationOptions: () => [],
  quickDay: 'today',
  statusEnum: 'PENDING_SETTLEMENT',
  statusFilterOptions: () => [],
  summaryLines: () => [],
  totals: null,
});

const emit = defineEmits<{
  onPrint: [];
  onPrintDetail: [];
  onQuery: [];
  onQuickDayChange: [day: CostDetailQuickDay];
  onPresentationModeChange: [mode: NurseStationCostDetailPresentationMode];
  onReset: [];
  'update:dateRange': [value: string[]];
  'update:keyword': [value: string];
  'update:statusEnum': [value: NurseStationCostDetailStatusFilter];
}>();

const dateRangeModel = computed({
  get: () => props.dateRange,
  set: (value: string[]) => emit('update:dateRange', value),
});

const keywordModel = computed({
  get: () => props.keyword,
  set: (value: string) => emit('update:keyword', value),
});

const statusEnumModel = computed({
  get: () => props.statusEnum,
  set: (value: NurseStationCostDetailStatusFilter) => emit('update:statusEnum', value),
});

const presentationModeModel = computed({
  get: () => props.presentationMode,
  set: (value: NurseStationCostDetailPresentationMode) => emit('onPresentationModeChange', value),
});

const isDetailMode = computed(() => props.presentationMode === 'DETAIL');

const activeLineCount = computed(() =>
  isDetailMode.value ? props.detailLines.length : props.summaryLines.length,
);

const showEmptyState = computed(() => !props.loading && activeLineCount.value === 0);

/** 适配 RadioGroup change 事件参数并切换快捷日期。 */
function handleQuickDayChangeEvent(value: boolean | number | string) {
  if (value === 'today' || value === 'yesterday') {
    emit('onQuickDayChange', value);
  }
}

/** 数值是否为负（冲销留痕数量/金额）。 */
function isNegativeNumeric(value: number | string | undefined) {
  if (value == null || value === '') {
    return false;
  }
  const n = Number(value);
  return Number.isFinite(n) && n < 0;
}

/** 明细行是否应用退费高亮样式。 */
function isRefundHighlightRow(row: NurseStationCostDetailLineDto) {
  return row.refundHighlight === true;
}

/**
 * 护士划价冲销留痕行（BR-IP-CDT-018）：来源仍为 nurse-pricing，
 * 以退费态或数量/金额为负识别，整行标红（不可用「护士冲销」文案判断）。
 */
function isNurseOffsetHighlightRow(row: NurseStationCostDetailLineDto) {
  if (String(row.generateSourceEnum ?? '').trim() !== 'nurse-pricing') {
    return false;
  }
  const status = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (status.includes('REFUND')) {
    return true;
  }
  return isNegativeNumeric(row.quantityValue) || isNegativeNumeric(row.totalPrice);
}

/** 明细表行 class：退费底色 + 护士冲销标红可叠加。 */
function resolveDetailRowClassName(row: NurseStationCostDetailLineDto) {
  const classes: string[] = [];
  if (isRefundHighlightRow(row)) {
    classes.push('cost-detail-row--refund');
  }
  if (isNurseOffsetHighlightRow(row)) {
    classes.push('cost-detail-row--cancel');
  }
  return classes.join(' ');
}

/** 费用状态 → Tag 类型。 */
function resolveStatusTagType(statusEnum: string | undefined): TagProps['type'] {
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

/** 汇总展开明细是否应用取消类标红样式。 */
function isSummaryActivityCancelRow(row: NurseStationCostSummaryActivityLineDto) {
  return row.cancelHighlight === true;
}

/** 将展示用业务时间拆分为日期与时间两行。 */
function splitBusinessDateTime(value: string | undefined) {
  const text = String(value ?? '').trim();
  if (!text || text === '—') {
    return { date: '—', time: '' };
  }
  const [date, time] = text.split(' ');
  return { date: date || text, time: time ?? '' };
}

/** 明细/汇总展开行：拆分展示时刻（优先 displayDateTime） */
function splitRowDisplayDateTime(row: {
  displayDateTime?: string;
  businessDateTime?: string;
  activityDateTime?: string;
}) {
  return splitBusinessDateTime(resolveCostDetailDisplayDateTime(row));
}
</script>

<template>
  <div class="cost-detail-page">
    <section class="cost-detail-filter">
      <div class="cost-detail-filter__left">
        <ElRadioGroup :model-value="quickDay" @change="handleQuickDayChangeEvent">
          <ElRadioButton label="today">今日</ElRadioButton>
          <ElRadioButton label="yesterday">昨日</ElRadioButton>
        </ElRadioGroup>
        <ElDatePicker
          v-model="dateRangeModel"
          type="daterange"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          range-separator="至"
          class="cost-detail-filter__date"
        />
        <span class="cost-detail-filter__label">项目查询</span>
        <ElInput
          v-model="keywordModel"
          clearable
          placeholder="项目名称/编码"
          class="cost-detail-filter__keyword"
          @keyup.enter="emit('onQuery')"
        />
        <span class="cost-detail-filter__label">费用状态</span>
        <ElSelect v-model="statusEnumModel" class="cost-detail-filter__status">
          <ElOption
            v-for="option in statusFilterOptions"
            :key="option.code"
            :label="option.display ?? option.code"
            :value="option.code"
          />
        </ElSelect>
      </div>
      <div class="cost-detail-filter__actions">
        <ElButton @click="emit('onReset')">重置</ElButton>
        <ElButton type="primary" :loading="loading" @click="emit('onQuery')">查询</ElButton>
        <ElButton plain :loading="printing" @click="emit('onPrint')">打印费用清单</ElButton>
        <ElButton plain :loading="printingDetail" @click="emit('onPrintDetail')">
          打印费用明细单
        </ElButton>
      </div>
    </section>

    <section class="cost-detail-card">
      <header class="cost-detail-card__header">
        <div class="cost-detail-card__header-top">
          <ElTabs v-model="presentationModeModel" class="cost-detail-card__tabs">
            <ElTabPane
              v-for="option in presentationOptions"
              :key="option.code"
              :label="option.display ?? option.code"
              :name="option.code"
            />
          </ElTabs>
          <div class="cost-detail-card__header-right">
            <div v-if="totals" class="cost-detail-card__totals">
              <span class="cost-detail-total-item">
                <span class="cost-detail-total-item__label">药品合计</span>
                <span class="cost-detail-total-item__value">{{
                  formatCostDetailMoney(totals.medicationTotal)
                }}</span>
              </span>
              <span class="cost-detail-total-item">
                <span class="cost-detail-total-item__label">诊疗合计</span>
                <span class="cost-detail-total-item__value">{{
                  formatCostDetailMoney(totals.serviceTotal)
                }}</span>
              </span>
              <span class="cost-detail-total-item">
                <span class="cost-detail-total-item__label">耗材合计</span>
                <span class="cost-detail-total-item__value">{{
                  formatCostDetailMoney(totals.deviceTotal)
                }}</span>
              </span>
              <span
                v-for="item in totals.categoryTotals ?? []"
                :key="item.categoryCode ?? item.categoryDisplay"
                class="cost-detail-total-item"
              >
                <span class="cost-detail-total-item__label">{{
                  item.categoryDisplay ?? item.categoryCode
                }}</span>
                <span class="cost-detail-total-item__value">{{
                  formatCostDetailMoney(item.amount)
                }}</span>
              </span>
              <span class="cost-detail-total-item cost-detail-total-item--grand">
                <span class="cost-detail-total-item__label">总合计</span>
                <span class="cost-detail-total-item__value">{{
                  formatCostDetailMoney(totals.grandTotal)
                }}</span>
              </span>
            </div>
            <span class="cost-detail-card__count">
              {{ isDetailMode ? '明细' : '汇总' }}项数：{{ activeLineCount }} 项
            </span>
          </div>
        </div>
      </header>

      <div class="cost-detail-card__body">
        <ElTable
          v-if="isDetailMode"
          v-show="!showEmptyState"
          v-loading="loading"
          :data="detailLines"
          border
          stripe
          height="100%"
          row-key="chargeItemId"
          class="cost-detail-table"
          :row-class-name="({ row }) => resolveDetailRowClassName(row)"
        >
          <ElTableColumn prop="itemName" label="项目名称" min-width="168" show-overflow-tooltip />
          <ElTableColumn prop="itemSpec" label="规格" min-width="96" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.itemSpec || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="数量" min-width="88" align="center">
            <template #default="{ row }">
              {{ formatCostDetailQuantity(row) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="单价" min-width="96" align="right">
            <template #default="{ row }">
              <span class="cost-detail-money">{{ formatCostDetailUnitPrice(row.unitPrice) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="金额" min-width="104" align="right">
            <template #default="{ row }">
              <span class="cost-detail-money cost-detail-money--amount">{{
                formatCostDetailMoney(row.totalPrice)
              }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="业务时间" min-width="132">
            <template #default="{ row }">
              <div class="cost-detail-datetime">
                <span>{{ splitRowDisplayDateTime(row).date }}</span>
                <span v-if="splitRowDisplayDateTime(row).time" class="cost-detail-datetime__time">
                  {{ splitRowDisplayDateTime(row).time }}
                </span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="费用状态" min-width="96" align="center">
            <template #default="{ row }">
              <ElTag
                v-if="row.statusDisplay"
                effect="light"
                size="small"
                :type="resolveStatusTagType(row.statusEnum)"
              >
                {{ row.statusDisplay }}
              </ElTag>
              <span v-else>—</span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="generateSourceDisplay" label="来源" min-width="88" align="center">
            <template #default="{ row }">
              {{ row.generateSourceDisplay || '—' }}
            </template>
          </ElTableColumn>
        </ElTable>

        <ElTable
          v-else
          v-show="!showEmptyState"
          v-loading="loading"
          :data="summaryLines"
          border
          stripe
          height="100%"
          class="cost-detail-table"
          :row-key="buildCostSummaryLineRowKey"
        >
          <ElTableColumn type="expand" width="48" align="center">
            <template #default="{ row }">
              <div class="cost-detail-summary-expand">
                <ElTable
                  :data="row.activityLines ?? []"
                  border
                  size="small"
                  class="cost-detail-summary-expand__table"
                  :row-class-name="
                    ({ row: activityRow }) =>
                      isSummaryActivityCancelRow(activityRow) ? 'cost-detail-row--cancel' : ''
                  "
                >
                  <ElTableColumn label="业务时间" min-width="132">
                    <template #default="{ row: activityRow }">
                      <div class="cost-detail-datetime">
                        <span>{{ splitRowDisplayDateTime(activityRow).date }}</span>
                        <span
                          v-if="splitRowDisplayDateTime(activityRow).time"
                          class="cost-detail-datetime__time"
                        >
                          {{ splitRowDisplayDateTime(activityRow).time }}
                        </span>
                      </div>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="操作" min-width="96" align="center">
                    <template #default="{ row: activityRow }">
                      {{ activityRow.activityTypeDisplay || '—' }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    prop="operatorName"
                    label="操作人"
                    min-width="96"
                    show-overflow-tooltip
                  >
                    <template #default="{ row: activityRow }">
                      {{ activityRow.operatorName || '—' }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="数量" min-width="96" align="center">
                    <template #default="{ row: activityRow }">
                      {{ formatCostSummaryActivityQuantity(activityRow) }}
                    </template>
                  </ElTableColumn>
                </ElTable>
                <div v-if="!row.activityLines?.length" class="cost-detail-summary-expand__empty">
                  暂无执行/划价明细
                </div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="itemName" label="项目名称" min-width="168" show-overflow-tooltip />
          <ElTableColumn prop="itemSpec" label="规格" min-width="96" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.itemSpec || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="categoryDisplay" label="分类" min-width="88" show-overflow-tooltip />
          <ElTableColumn label="单价" min-width="96" align="right">
            <template #default="{ row }">
              <span class="cost-detail-money">{{ formatCostDetailUnitPrice(row.unitPrice) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="行合计" min-width="104" align="right">
            <template #default="{ row }">
              <span class="cost-detail-money cost-detail-money--amount">{{
                formatCostDetailMoney(row.lineTotalAmount)
              }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="数量" min-width="120" align="center">
            <template #default="{ row }">
              {{ formatCostSummaryQuantity(row) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="expenseBlockDisplay"
            label="费用大类"
            min-width="96"
            align="center"
          />
        </ElTable>

        <div v-if="showEmptyState" class="cost-detail-empty">
          <ElEmpty description="当前查询条件下暂无费用数据" :image-size="88" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.cost-detail-page {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.cost-detail-filter {
  align-items: center;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
  display: flex;
  flex-shrink: 0;
  gap: 12px;
  justify-content: space-between;
  min-height: 52px;
  padding: 8px 12px;
}

.cost-detail-filter__left,
.cost-detail-filter__actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cost-detail-filter__date {
  width: 280px;
}

.cost-detail-filter__label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.cost-detail-filter__status {
  width: 120px;
}

.cost-detail-filter__keyword {
  width: 180px;
}

.cost-detail-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.cost-detail-card__header {
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  padding: 0 12px;
}

.cost-detail-card__header-top {
  align-items: center;
  display: flex;
  gap: 16px;
  min-height: 44px;
}

.cost-detail-card__header-right {
  align-items: center;
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 12px 20px;
  justify-content: flex-end;
  min-width: 0;
}

.cost-detail-card__tabs {
  flex-shrink: 0;
}

.cost-detail-card__tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.cost-detail-card__tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.cost-detail-card__tabs :deep(.el-tabs__item) {
  font-size: 14px;
  height: 40px;
  line-height: 40px;
  min-width: 88px;
  padding: 0 24px;
}

.cost-detail-card__tabs :deep(.el-tabs__active-bar) {
  height: 2px;
}

.cost-detail-card__count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.cost-detail-card__totals {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  justify-content: flex-end;
}

.cost-detail-total-item {
  align-items: baseline;
  display: inline-flex;
  gap: 6px;
  white-space: nowrap;
}

.cost-detail-total-item__label {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.cost-detail-total-item__value {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.cost-detail-total-item--grand .cost-detail-total-item__label {
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.cost-detail-total-item--grand .cost-detail-total-item__value {
  font-size: 18px;
  font-weight: 700;
}

.cost-detail-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 8px 12px 10px;
}

.cost-detail-table {
  flex: 1;
  min-height: 0;
}

.cost-detail-empty {
  align-items: center;
  display: flex;
  flex: 1;
  justify-content: center;
  min-height: 240px;
}

.cost-detail-datetime {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.cost-detail-datetime__time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cost-detail-money {
  font-variant-numeric: tabular-nums;
}

.cost-detail-money--amount {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.cost-detail-page :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.cost-detail-page :deep(.el-table__header-wrapper) {
  border-bottom: 1px solid var(--el-border-color-light);
}

.cost-detail-page :deep(.el-table__body td.el-table__cell) {
  font-size: 13px;
}

.cost-detail-page :deep(.cost-detail-row--refund) {
  --el-table-tr-bg-color: var(--el-color-warning-light-9);
}

.cost-detail-page :deep(.cost-detail-row--cancel) {
  color: var(--el-color-danger);
}

/* 金额列有独立字色，冲销行需一并继承危险色 */
.cost-detail-page :deep(.cost-detail-row--cancel .cost-detail-money--amount),
.cost-detail-page :deep(.cost-detail-row--cancel .cost-detail-datetime__time) {
  color: var(--el-color-danger);
}

.cost-detail-summary-expand {
  padding: 4px 12px 8px 40px;
}

.cost-detail-summary-expand__table {
  width: 100%;
}

.cost-detail-summary-expand__empty {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  padding: 8px 0;
  text-align: center;
}
</style>
