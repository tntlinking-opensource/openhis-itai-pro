<script setup lang="ts">
import type { PricingCatalogCategory } from '../services/map-nurse-station-inpatient-billing';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';
import type { NurseStationPricingCatalogRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElOption,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import {
  buildPricingCatalogRowKey,
  formatPricingCatalogRemainingQty,
  formatPricingCatalogUnitPriceWithUnit,
  listPricingUnitBandSelectOptions,
} from '../services/map-nurse-station-inpatient-billing';
import PricingOrderTemplatePanel from './pricing-order-template-panel.vue';

defineOptions({ name: 'BatchPricingDialog' });

/** 弹窗内已选行（与父级 Map 条目结构对齐） */
export interface BatchPricingSelectedItem {
  rowKey: string;
  catalog: NurseStationPricingCatalogRowDto;
  category: PricingCatalogCategory;
  quantity: number;
  selectedBandKind: string;
  displayUnitPrice: number;
  displayUnitCode: string;
  displayUnitName: string;
}

interface Props {
  modelValue?: boolean;
  title?: string;
  canOperateBilling?: boolean;
  patientLabel?: string;
  modeLabel?: string;
  supplementOrderContent?: string;
  projectType?: PricingCatalogCategory;
  projectKeyword?: string;
  batchPricingTime?: string;
  catalogRows?: NurseStationPricingCatalogRowDto[];
  catalogLoading?: boolean;
  catalogPageNo?: number;
  catalogPageSize?: number;
  catalogTotal?: number;
  selectedItems?: BatchPricingSelectedItem[];
  selectedQuantityTotal?: number;
  selectedAmountTotal?: number;
  saving?: boolean;
  templateApplying?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  title: '批量划价',
  canOperateBilling: false,
  patientLabel: '-',
  modeLabel: '直接划价',
  supplementOrderContent: '',
  projectType: 'device',
  projectKeyword: '',
  batchPricingTime: '',
  catalogRows: () => [],
  catalogLoading: false,
  catalogPageNo: 1,
  catalogPageSize: 30,
  catalogTotal: 0,
  selectedItems: () => [],
  selectedQuantityTotal: 0,
  selectedAmountTotal: 0,
  saving: false,
  templateApplying: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  'update:projectKeyword': [value: string];
  'update:batchPricingTime': [value: string];
  opened: [];
  closed: [];
  catalogSearch: [];
  catalogPageChange: [page: number];
  projectTypeChange: [value: PricingCatalogCategory];
  catalogRowClick: [row: NurseStationPricingCatalogRowDto];
  quantityChange: [rowKey: string, value: null | number | undefined];
  bandKindChange: [rowKey: string, bandKind: string];
  removeItem: [rowKey: string];
  clearSelected: [];
  submit: [];
  cancel: [];
  templateSelect: [template: OrderTemplateHeaderItem];
}>();

const templateDrawerVisible = ref(false);
const templatePanelRef = ref<InstanceType<typeof PricingOrderTemplatePanel> | null>(null);
const prevTemplateApplying = ref(false);

/** 已选 rowKey 集合，供目录行高亮 */
const selectedKeySet = computed(() => {
  const keys = new Set<string>();
  for (const item of props.selectedItems) {
    keys.add(item.rowKey);
  }
  return keys;
});

/** 已选是否含耗材（决定批号列） */
const selectedHasDevice = computed(() =>
  props.selectedItems.some((item) => item.category === 'device'),
);

/** 金额两位小数展示 */
function formatCurrency(value: number) {
  return `￥${formatMoneyDisplay(value)}`;
}

/** 目录行是否已在已选 */
function isCatalogRowSelected(row: NurseStationPricingCatalogRowDto) {
  return selectedKeySet.value.has(buildPricingCatalogRowKey(row, props.projectType));
}

/** 已选行单位档选项 */
function unitBandOptionsForSelected(row: BatchPricingSelectedItem) {
  return listPricingUnitBandSelectOptions(row.catalog);
}

/** 打开组套抽屉 */
function openTemplateDrawer() {
  if (!props.canOperateBilling) {
    return;
  }
  templateDrawerVisible.value = true;
}

/** 组套树选中 */
function handleTemplateSelect(header: OrderTemplateHeaderItem) {
  emit('templateSelect', header);
}

/** 目录行单击加入 */
function handleCatalogRowClick(row: NurseStationPricingCatalogRowDto) {
  if (!props.canOperateBilling) {
    return;
  }
  emit('catalogRowClick', row);
}

/** 目录行已选高亮 */
function catalogRowClassName({ row }: { row: NurseStationPricingCatalogRowDto }) {
  return isCatalogRowSelected(row) ? 'batch-pricing-catalog-row--selected' : '';
}

/** 切换耗材/诊疗 */
function handleProjectTypeChange(value: string | number | boolean | undefined) {
  emit('projectTypeChange', value as PricingCatalogCategory);
}

/** 与开药选品一致：关键字变更 300ms 防抖后远程检索 */
const debouncedCatalogSearch = useDebounceFn(() => {
  if (!props.modelValue) {
    return;
  }
  emit('catalogSearch');
}, 300);

/** 回车 / 清空：立刻检索（取消未触发的防抖） */
function emitImmediateCatalogSearch() {
  debouncedCatalogSearch.cancel();
  emit('catalogSearch');
}

watch(
  () => props.projectKeyword,
  () => {
    if (!props.modelValue) {
      return;
    }
    debouncedCatalogSearch();
  },
);

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      templateDrawerVisible.value = false;
      debouncedCatalogSearch.cancel();
    }
  },
);

watch(
  () => props.templateApplying,
  (applying) => {
    if (prevTemplateApplying.value && !applying) {
      templateDrawerVisible.value = false;
      templatePanelRef.value?.clearSelection();
    }
    prevTemplateApplying.value = applying;
  },
);
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    class="batch-pricing-dialog-wrapper"
    :title="title"
    width="calc(100vw - 48px)"
    top="3vh"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('opened')"
    @closed="emit('closed')"
  >
    <div class="batch-pricing-dialog">
      <div class="batch-pricing-dialog__context">
        <ElAlert
          v-if="!canOperateBilling"
          title="当前不是单选患者状态，仅可查看弹窗；请先在左侧只勾选 1 位患者"
          type="warning"
          :closable="false"
          class="w-full"
        />
        <div class="batch-pricing-dialog__context-meta">
          <span>当前患者：{{ patientLabel }}</span>
          <span>模式：{{ modeLabel }}</span>
          <span v-if="supplementOrderContent">关联医嘱：{{ supplementOrderContent }}</span>
        </div>
      </div>

      <div class="batch-pricing-dialog__body">
        <section class="batch-pricing-panel batch-pricing-panel--catalog">
          <header class="batch-pricing-panel__header">
            <span>项目检索</span>
            <ElButton
              type="primary"
              plain
              size="small"
              :disabled="!canOperateBilling"
              @click="openTemplateDrawer"
            >
              组套
            </ElButton>
          </header>
          <div class="batch-pricing-filter">
            <ElRadioGroup :model-value="projectType" @change="handleProjectTypeChange">
              <ElRadioButton label="device">耗材</ElRadioButton>
              <ElRadioButton label="service">诊疗项目</ElRadioButton>
            </ElRadioGroup>
            <ElInput
              :model-value="projectKeyword"
              clearable
              placeholder="请输入编码、项目名称或商品名称"
              class="batch-pricing-filter__keyword"
              @update:model-value="emit('update:projectKeyword', String($event ?? ''))"
              @keyup.enter="emitImmediateCatalogSearch"
              @clear="emitImmediateCatalogSearch"
            />
          </div>
          <div class="batch-pricing-panel__table">
            <ElTable
              v-loading="catalogLoading"
              :data="catalogRows"
              border
              height="100%"
              :row-key="(row) => buildPricingCatalogRowKey(row, projectType)"
              :row-class-name="catalogRowClassName"
              @row-click="handleCatalogRowClick"
            >
              <ElTableColumn prop="busNo" label="编码" min-width="100" show-overflow-tooltip />
              <ElTableColumn prop="name" label="项目名称" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.name ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="规格" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ String(row.totalVolume ?? '').trim() || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="projectType === 'device'"
                label="批号"
                width="92"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.lotNumber?.trim() || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="projectType === 'device'"
                label="剩余"
                min-width="72"
                align="center"
              >
                <template #default="{ row }">
                  {{ formatPricingCatalogRemainingQty(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="单价" width="88" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ formatPricingCatalogUnitPriceWithUnit(row, projectType) }}
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
          <div class="batch-pricing-panel__pager">
            <ElPagination
              :current-page="catalogPageNo"
              :page-size="catalogPageSize"
              :total="catalogTotal"
              layout="total, prev, pager, next"
              small
              @current-change="emit('catalogPageChange', $event)"
            />
          </div>
        </section>

        <section class="batch-pricing-panel batch-pricing-panel--cart">
          <header class="batch-pricing-panel__header">
            <span>已选项目（{{ selectedItems.length }}）</span>
            <ElButton
              text
              type="danger"
              :disabled="selectedItems.length === 0 || !canOperateBilling"
              @click="emit('clearSelected')"
            >
              清空
            </ElButton>
          </header>
          <div v-if="selectedItems.length === 0" class="batch-pricing-cart-empty">
            <ElEmpty description="点击左侧项目加入，或从组套套用" :image-size="64" />
          </div>
          <div v-else class="batch-pricing-panel__table">
            <ElTable :data="selectedItems" border height="100%" row-key="rowKey">
              <ElTableColumn label="项目名称" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.catalog.name ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="规格" min-width="90" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ String(row.catalog.totalVolume ?? '').trim() || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="projectType === 'device' || selectedHasDevice"
                label="批号"
                min-width="90"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.catalog.lotNumber?.trim() || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="单位" width="88" align="center">
                <template #default="{ row }">
                  <ElSelect
                    v-if="unitBandOptionsForSelected(row).length > 1"
                    :model-value="row.selectedBandKind"
                    size="small"
                    class="batch-pricing-unit-select"
                    :disabled="!canOperateBilling"
                    @change="(v) => emit('bandKindChange', row.rowKey, String(v ?? ''))"
                  >
                    <ElOption
                      v-for="opt in unitBandOptionsForSelected(row)"
                      :key="opt.bandKind"
                      :label="opt.unitName"
                      :value="opt.bandKind"
                    />
                  </ElSelect>
                  <span v-else>{{ row.displayUnitName || '—' }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="单价" min-width="80" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(row.displayUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="数量" width="108" align="center" class-name="qty-column">
                <template #default="{ row }">
                  <ElInputNumber
                    class="batch-pricing-qty-input"
                    :model-value="row.quantity"
                    :min="1"
                    :step="1"
                    :controls="true"
                    controls-position="right"
                    :disabled="!canOperateBilling"
                    @update:model-value="emit('quantityChange', row.rowKey, $event)"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="金额" min-width="80" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(calcMoneyLineTotal(row.displayUnitPrice, row.quantity)) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="56" align="center" fixed="right">
                <template #default="{ row }">
                  <ElButton
                    link
                    type="danger"
                    :disabled="!canOperateBilling"
                    @click="emit('removeItem', row.rowKey)"
                  >
                    删除
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </section>
      </div>

      <div class="batch-pricing-dialog__footer">
        <div class="batch-pricing-footer-left">
          <span class="batch-pricing-footer-label">划价时间</span>
          <ElDatePicker
            :model-value="batchPricingTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择划价时间"
            class="batch-pricing-footer-time"
            :disabled="!canOperateBilling"
            @update:model-value="emit('update:batchPricingTime', String($event ?? ''))"
          />
          <div class="batch-pricing-summary">
            <span>已选项目：{{ selectedItems.length }} 项</span>
            <span>总数量：{{ selectedQuantityTotal }}</span>
            <span>金额合计：{{ formatCurrency(selectedAmountTotal) }}</span>
          </div>
        </div>
        <div class="batch-pricing-actions">
          <ElButton @click="emit('cancel')">取消</ElButton>
          <ElButton
            type="primary"
            :loading="saving"
            :disabled="!canOperateBilling || selectedItems.length === 0"
            @click="emit('submit')"
          >
            {{ modeLabel === '医嘱补费' ? '确认补费' : '确认划价' }}
          </ElButton>
        </div>
      </div>
    </div>
  </AppDialog>

  <ElDrawer
    v-model="templateDrawerVisible"
    title="划价组套"
    direction="ltr"
    size="300px"
    append-to-body
    :close-on-click-modal="true"
  >
    <PricingOrderTemplatePanel
      ref="templatePanelRef"
      :active="templateDrawerVisible"
      :applying="templateApplying"
      :disabled="!canOperateBilling"
      :show-header="false"
      class="batch-pricing-template-drawer-panel"
      @select="handleTemplateSelect"
    />
  </ElDrawer>
</template>

<style scoped>
.batch-pricing-dialog {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  /* 列表区最高 650，避免弹窗过高 */
  height: auto;
  max-height: min(calc(650px + 160px), calc(100vh - 72px));
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  width: 100%;
}

.batch-pricing-dialog__context {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 8px;
}

.batch-pricing-dialog__context-meta {
  align-items: center;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  color: var(--el-text-color-primary);
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 18px;
  min-height: 38px;
  padding: 0 12px;
}

.batch-pricing-dialog__body {
  /* 检索略窄，已选放大；列表主体最高 650 */
  display: grid;
  flex: 0 1 auto;
  gap: 10px;
  grid-template-columns: minmax(0, 1.056fr) minmax(0, 1.144fr);
  height: min(650px, calc(100vh - 220px));
  max-height: 650px;
  min-height: 0;
  overflow: hidden;
  width: 100%;
}

.batch-pricing-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.batch-pricing-panel__header {
  align-items: center;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-primary);
  display: flex;
  flex: 0 0 auto;
  font-size: 14px;
  font-weight: 600;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 10px;
}

.batch-pricing-filter {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 48px;
  padding: 8px 10px;
}

.batch-pricing-filter__keyword {
  flex: 1 1 120px;
  min-width: 96px;
}

.batch-pricing-panel__table {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.batch-pricing-panel__pager {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  padding: 6px 10px;
}

.batch-pricing-cart-empty {
  align-items: center;
  display: flex;
  flex: 1 1 auto;
  justify-content: center;
  min-height: 140px;
}

.batch-pricing-dialog__footer {
  align-items: center;
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  padding-top: 8px;
}

.batch-pricing-footer-left {
  align-items: center;
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.batch-pricing-footer-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.batch-pricing-footer-time {
  width: 200px;
}

.batch-pricing-summary {
  color: var(--el-text-color-regular);
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 16px;
}

.batch-pricing-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.batch-pricing-panel :deep(.el-table) {
  width: 100%;
}

.batch-pricing-panel :deep(.batch-pricing-catalog-row--selected > td) {
  background: var(--el-color-primary-light-9) !important;
}

:deep(.qty-column .cell) {
  overflow: visible;
  text-overflow: clip;
}

.batch-pricing-unit-select {
  width: 72px;
}

.batch-pricing-qty-input {
  width: 92px;
}

.batch-pricing-template-drawer-panel {
  border: none;
  border-radius: 0;
  height: 100%;
}
</style>

<style>
.batch-pricing-dialog-wrapper {
  max-width: calc(100vw - 24px);
}

.el-dialog.batch-pricing-dialog-wrapper {
  margin-left: auto;
  margin-right: auto;
  max-width: calc(100vw - 24px);
  width: calc(100vw - 48px) !important;
}

.batch-pricing-dialog-wrapper .el-dialog__body {
  overflow-x: hidden;
}
</style>
