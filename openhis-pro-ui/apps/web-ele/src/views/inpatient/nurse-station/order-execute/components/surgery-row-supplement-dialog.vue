<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type { NurseStationOrderExecuteTableRow } from '../services/map-nurse-station-order-execute';
import type {
  NurseStationPricingCatalogRowDto,
  NurseStationPricingWriteItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  httpConfirmSupplementNurseStationPricing,
  httpPageNurseStationPricingCatalog,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.http';
import AppDialog from '#/components/app-dialog.vue';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildSupplementPricingWriteItem,
  PRICING_PRODUCT_TABLE_DEVICE,
  PRICING_PRODUCT_TABLE_SERVICE,
} from '../../inpatient-billing/services/map-nurse-station-inpatient-billing';

defineOptions({ name: 'SurgeryRowSupplementDialog' });

interface SelectedLine {
  rowKey: string;
  catalog: NurseStationPricingCatalogRowDto;
  quantity: number;
}

interface Props {
  modelValue?: boolean;
  patient?: NurseStationModulePatient | null;
  row?: NurseStationOrderExecuteTableRow | null;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  patient: null,
  row: null,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  success: [];
}>();

const projectType = ref<'device' | 'service'>('device');
const projectKeyword = ref('');
const catalogRows = ref<NurseStationPricingCatalogRowDto[]>([]);
const catalogLoading = ref(false);
const catalogPageNo = ref(1);
const catalogPageSize = 20;
const catalogTotal = ref(0);
const selectedItems = ref<SelectedLine[]>([]);
const saving = ref(false);

const surgeryTitle = computed(() => {
  const text = String(props.row?.content ?? '').trim();
  return text || '手术申请';
});

const patientLabel = computed(() => {
  const p = props.patient;
  if (!p) {
    return '—';
  }
  const bed = String(p.bedNo ?? '').trim();
  const name = String(p.name ?? '').trim();
  if (bed && name) {
    return `${bed}${name}`;
  }
  return name || bed || '—';
});

const selectedQuantityTotal = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
);

const selectedAmountTotal = computed(() =>
  selectedItems.value.reduce((sum, item) => {
    const price = Number(item.catalog.unitPrice ?? 0);
    return sum + calcMoneyLineTotal(price, item.quantity);
  }, 0),
);

/** 关闭弹窗 */
function closeDialog() {
  emit('update:modelValue', false);
}

function formatCurrency(value: number) {
  return formatMoneyDisplay(value);
}

function buildCatalogRowKey(row: NurseStationPricingCatalogRowDto) {
  return [
    row.productDefinitionId ?? '',
    row.chargeItemDefinitionId ?? '',
    row.lotNumber ?? '',
    row.activityId ?? '',
  ].join('|');
}

/** 拉取补费目录（左栏） */
async function loadCatalog() {
  catalogLoading.value = true;
  try {
    const keyword = projectKeyword.value.trim();
    const patient = props.patient;
    if (!patient) {
      catalogRows.value = [];
      catalogTotal.value = 0;
      return;
    }
    const res = await httpPageNurseStationPricingCatalog({
      encounterId: preserveApiIdForRequest(patient.id),
      patientId: preserveApiIdForRequest(patient.patientId),
      keyword: keyword || undefined,
      pageNo: catalogPageNo.value,
      pageSize: catalogPageSize,
      productTableNames: [
        projectType.value === 'device'
          ? PRICING_PRODUCT_TABLE_DEVICE
          : PRICING_PRODUCT_TABLE_SERVICE,
      ],
    });
    const content = res.content ?? [];
    catalogRows.value = content;
    catalogTotal.value = res.totalCount ?? res.total ?? content.length;
  } finally {
    catalogLoading.value = false;
  }
}

function handleProjectTypeChange(value: string | number | boolean | undefined) {
  projectType.value = value === 'service' ? 'service' : 'device';
  catalogPageNo.value = 1;
  void loadCatalog();
}

function handleCatalogSearch() {
  catalogPageNo.value = 1;
  void loadCatalog();
}

function handleCatalogPageChange(page: number) {
  catalogPageNo.value = page;
  void loadCatalog();
}

/** 点击左栏加入已选（已存在则数量 +1） */
function handleCatalogRowClick(row: NurseStationPricingCatalogRowDto) {
  const rowKey = buildCatalogRowKey(row);
  const exist = selectedItems.value.find((item) => item.rowKey === rowKey);
  if (exist) {
    exist.quantity += 1;
    selectedItems.value = [...selectedItems.value];
    return;
  }
  selectedItems.value = [
    ...selectedItems.value,
    {
      rowKey,
      catalog: row,
      quantity: 1,
    },
  ];
}

function handleQuantityChange(rowKey: string, value: number | undefined) {
  const qty = Math.max(1, Number(value ?? 1));
  selectedItems.value = selectedItems.value.map((item) =>
    item.rowKey === rowKey ? { ...item, quantity: qty } : item,
  );
}

function handleRemoveItem(rowKey: string) {
  selectedItems.value = selectedItems.value.filter((item) => item.rowKey !== rowKey);
}

function handleClearSelected() {
  selectedItems.value = [];
}

/** 提交手术行补费 */
async function submitSupplement() {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先选择补费项目');
    return;
  }
  const patient = props.patient;
  const order = props.row;
  if (!patient || !order) {
    return;
  }
  const encounterId = preserveApiIdForRequest(patient.id) ?? '';
  const patientId = preserveApiIdForRequest(patient.patientId) ?? '';
  const accountId = preserveApiIdForRequest(patient.accountId) ?? '';
  const writeItems: NurseStationPricingWriteItemDto[] = [];
  for (const line of selectedItems.value) {
    const writeItem = buildSupplementPricingWriteItem({
      encounterId,
      patientId,
      accountId,
      catalogRow: line.catalog,
      quantity: line.quantity,
      categoryHint: projectType.value,
      parentRequestAnchor: {
        orderType: order.orderType,
        requestId: order.orderId,
      },
    });
    if (!writeItem) {
      ElMessage.warning(`「${line.catalog.name ?? ''}」缺少计费字段，请换一条目录`);
      return;
    }
    writeItems.push(writeItem);
  }
  saving.value = true;
  try {
    await httpConfirmSupplementNurseStationPricing({
      items: writeItems,
      pricingTime: undefined,
    });
    ElMessage.success('手术行补费已提交');
    emit('success');
    closeDialog();
  } finally {
    saving.value = false;
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return;
    }
    projectType.value = 'device';
    projectKeyword.value = '';
    catalogPageNo.value = 1;
    selectedItems.value = [];
    void loadCatalog();
  },
);
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    class="surgery-sup-dialog-wrapper"
    title="手术行补费"
    width="calc(100vw - 48px)"
    top="3vh"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="surgery-sup-dialog">
      <div class="surgery-sup-dialog__context">
        <div class="surgery-sup-dialog__context-meta">
          <span>当前患者：{{ patientLabel }}</span>
          <span>模式：手术补费</span>
          <span>关联医嘱：{{ surgeryTitle }}</span>
        </div>
      </div>

      <div class="surgery-sup-dialog__body">
        <section class="surgery-sup-panel surgery-sup-panel--catalog">
          <header class="surgery-sup-panel__header">
            <span>项目检索</span>
          </header>
          <div class="surgery-sup-filter">
            <ElRadioGroup :model-value="projectType" @change="handleProjectTypeChange">
              <ElRadioButton label="device">耗材</ElRadioButton>
              <ElRadioButton label="service">诊疗项目</ElRadioButton>
            </ElRadioGroup>
            <ElInput
              v-model="projectKeyword"
              clearable
              placeholder="编码/名称"
              class="surgery-sup-filter__keyword"
              @keyup.enter="handleCatalogSearch"
              @clear="handleCatalogSearch"
            />
            <ElButton type="primary" :loading="catalogLoading" @click="handleCatalogSearch">
              查询
            </ElButton>
          </div>
          <div class="surgery-sup-panel__table">
            <ElTable
              v-loading="catalogLoading"
              :data="catalogRows"
              border
              height="100%"
              :row-key="(row) => buildCatalogRowKey(row)"
              @row-click="handleCatalogRowClick"
            >
              <ElTableColumn prop="busNo" label="编码" min-width="100" show-overflow-tooltip />
              <ElTableColumn prop="name" label="项目名称" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.name ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="规格" min-width="100" show-overflow-tooltip>
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
              <ElTableColumn label="单价" width="88" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ formatCurrency(Number(row.unitPrice ?? 0)) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="单位" width="72" align="center" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.unitName || row.unitCode || '—' }}
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
          <div class="surgery-sup-panel__pager">
            <ElPagination
              :current-page="catalogPageNo"
              :page-size="catalogPageSize"
              :total="catalogTotal"
              layout="total, prev, pager, next"
              small
              @current-change="handleCatalogPageChange"
            />
          </div>
        </section>

        <section class="surgery-sup-panel surgery-sup-panel--cart">
          <header class="surgery-sup-panel__header">
            <span>已选项目（{{ selectedItems.length }}）</span>
            <ElButton
              text
              type="danger"
              :disabled="selectedItems.length === 0"
              @click="handleClearSelected"
            >
              清空
            </ElButton>
          </header>
          <div v-if="selectedItems.length === 0" class="surgery-sup-cart-empty">
            <ElEmpty description="点击左侧项目加入" :image-size="64" />
          </div>
          <div v-else class="surgery-sup-panel__table">
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
              <ElTableColumn label="单位" width="72" align="center">
                <template #default="{ row }">
                  {{ row.catalog.unitName || row.catalog.unitCode || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="单价" min-width="80" align="right">
                <template #default="{ row }">
                  {{ formatCurrency(Number(row.catalog.unitPrice ?? 0)) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="数量" width="108" align="center">
                <template #default="{ row }">
                  <ElInputNumber
                    class="surgery-sup-qty-input"
                    :model-value="row.quantity"
                    :min="1"
                    :step="1"
                    :controls="true"
                    controls-position="right"
                    @update:model-value="(v) => handleQuantityChange(row.rowKey, v)"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="金额" min-width="80" align="right">
                <template #default="{ row }">
                  {{
                    formatCurrency(
                      calcMoneyLineTotal(Number(row.catalog.unitPrice ?? 0), row.quantity),
                    )
                  }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="56" align="center" fixed="right">
                <template #default="{ row }">
                  <ElButton link type="danger" @click="handleRemoveItem(row.rowKey)">
                    删除
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </section>
      </div>

      <div class="surgery-sup-dialog__footer">
        <div class="surgery-sup-footer-left">
          <div class="surgery-sup-summary">
            <span>已选项目：{{ selectedItems.length }} 项</span>
            <span>总数量：{{ selectedQuantityTotal }}</span>
            <span>金额合计：{{ formatCurrency(selectedAmountTotal) }}</span>
          </div>
        </div>
        <div class="surgery-sup-actions">
          <ElButton @click="closeDialog">取消</ElButton>
          <ElButton
            type="primary"
            :loading="saving"
            :disabled="selectedItems.length === 0"
            @click="submitSupplement"
          >
            确认补费
          </ElButton>
        </div>
      </div>
    </div>
  </AppDialog>
</template>

<style scoped>
.surgery-sup-dialog {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: auto;
  max-height: min(calc(650px + 160px), calc(100vh - 72px));
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  width: 100%;
}

.surgery-sup-dialog__context {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 8px;
}

.surgery-sup-dialog__context-meta {
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

.surgery-sup-dialog__body {
  display: grid;
  flex: 0 1 auto;
  gap: 10px;
  grid-template-columns: minmax(0, 1.056fr) minmax(0, 1.144fr);
  height: min(650px, calc(100vh - 220px));
  max-height: 650px;
  min-height: 0;
  overflow: hidden;
}

.surgery-sup-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.surgery-sup-panel__header {
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

.surgery-sup-filter {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 48px;
  padding: 8px 10px;
}

.surgery-sup-filter__keyword {
  flex: 1 1 120px;
  min-width: 96px;
}

.surgery-sup-panel__table {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.surgery-sup-panel__pager {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  padding: 6px 10px;
}

.surgery-sup-cart-empty {
  align-items: center;
  display: flex;
  flex: 1 1 auto;
  justify-content: center;
  min-height: 140px;
}

.surgery-sup-dialog__footer {
  align-items: center;
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  padding-top: 8px;
}

.surgery-sup-footer-left {
  align-items: center;
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.surgery-sup-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.surgery-sup-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.surgery-sup-qty-input {
  width: 96px;
}

.surgery-sup-qty-input :deep(.el-input__wrapper) {
  padding-left: 8px;
  padding-right: 8px;
}

.surgery-sup-panel--catalog :deep(.el-table__row) {
  cursor: pointer;
}
</style>
