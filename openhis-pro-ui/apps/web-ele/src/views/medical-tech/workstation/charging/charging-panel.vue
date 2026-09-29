<script setup lang="ts">
import type { ApiOptionItem } from '#/types/api-response';
import type {
  MedicalTechDeviceCatalogRowDto,
  MedicalTechDevicePricingWriteItemDto,
  MedicalTechPricedDeviceRowDto,
} from '#/api/medical-tech/medical-tech-charging.types';

import { computed, inject, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  httpCancelMedicalTechDevicePricing,
  httpConfirmMedicalTechDevicePricing,
  httpListMedicalTechPricedDevices,
  httpLoadMedicalTechChargingInit,
  httpPageMedicalTechDeviceCatalog,
} from '#/api/medical-tech/medical-tech-charging.http';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import {
  calcMoneyLineTotal,
  formatMoneyDisplay,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { medicalTechWorkstationContextKey } from '../workbench-tab.config';
import {
  convertMedicalTechQtyBetweenBandKinds,
  formatMedicalTechDraftLineAmount,
  formatMedicalTechUnitPrice,
  listMedicalTechUnitBandOptions,
  resolveMedicalTechDefaultBandKind,
  resolveMedicalTechUnitCodeByBand,
  resolveMedicalTechUnitNameByBand,
  resolveMedicalTechUnitPriceByBand,
  type MedicalTechUnitBandSelectOption,
} from './medical-tech-charging-unit';

defineOptions({ name: 'MedicalTechChargingPanel' });

/** 待划价草稿行：保留目录快照供换单位 */
interface DraftPricingRow {
  catalog: MedicalTechDeviceCatalogRowDto;
  definitionId: string;
  displayUnitCode: string;
  displayUnitName: string;
  displayUnitPrice: null | number | string;
  itemCode?: string;
  itemName?: string;
  locationId: string;
  lotNumber?: null | string;
  quantity: number;
  rowKey: string;
  selectedBandKind: string;
  specification?: string;
}

const ctx = inject(medicalTechWorkstationContextKey);
if (!ctx) {
  throw new Error('MedicalTechChargingPanel 须在 MedicalTechWorkstation 壳内使用');
}

const { encounterScope, selectedEncounterId, reloadPatientQueue } = ctx;

const locationOptions = ref<ApiOptionItem[]>([]);
const defaultLocationId = ref('');
const catalogKeyword = ref('');
const catalogLocationId = ref('');
const catalogPage = reactive({ pageNo: 1, pageSize: 20 });
const catalogRows = ref<MedicalTechDeviceCatalogRowDto[]>([]);
const catalogTotal = ref(0);
const draftItems = ref<DraftPricingRow[]>([]);
const pricedRows = ref<MedicalTechPricedDeviceRowDto[]>([]);
let draftSeq = 0;

/** 待划价金额合计：行金额按计算刻度加总后再按结算刻度展示 */
const draftAmountLabel = computed(() => {
  // 缺单价按 0 计入，避免合计出现空金额
  const lineTotals = draftItems.value.map((item) =>
    calcMoneyLineTotal(item.displayUnitPrice, item.quantity),
  );
  return `¥ ${formatMoneyDisplay(sumMoneyThenSettle(lineTotals))}`;
});

const { run: runInit, loading: initLoading } = useRequest(httpLoadMedicalTechChargingInit, {
  manual: true,
  showErrorMessage: false,
  onSuccess: (data) => {
    locationOptions.value = data.locationOptions ?? [];
    const def = preserveApiIdForRequest(data.defaultLocationId) ?? '';
    defaultLocationId.value = def;
    if (!catalogLocationId.value && def) {
      catalogLocationId.value = def;
    }
  },
});

const { run: runPageCatalog, loading: catalogLoading } = useRequest(
  httpPageMedicalTechDeviceCatalog,
  {
    manual: true,
    showErrorMessage: false,
    onSuccess: (data) => {
      catalogRows.value = data.content ?? [];
      catalogTotal.value = data.totalCount ?? 0;
    },
  },
);

const { run: runConfirm, loading: confirmLoading } = useRequest(
  httpConfirmMedicalTechDevicePricing,
  {
    manual: true,
    showErrorMessage: false,
    onSuccess: (data) => {
      if (data.succeeded === false) {
        ElMessage.warning(data.message || '划价未成功');
        return;
      }
      ElMessage.success(data.message || '划价成功');
      draftItems.value = [];
      void loadPricedList();
      // 划价挂待结算费用：刷新队列余额（库内余额 − 待结算）
      void reloadPatientQueue();
    },
  },
);

const { run: runListPriced, loading: pricedLoading } = useRequest(
  httpListMedicalTechPricedDevices,
  {
    manual: true,
    showErrorMessage: false,
    onSuccess: (data) => {
      pricedRows.value = data.rows ?? [];
    },
  },
);

const { run: runCancelPricing, loading: cancelLoading } = useRequest(
  httpCancelMedicalTechDevicePricing,
  {
    manual: true,
    showErrorMessage: false,
    onSuccess: (data) => {
      if (data.succeeded === false) {
        ElMessage.warning(data.message || '冲销未成功');
        return;
      }
      ElMessage.success(data.message || `已冲销 ${data.cancelledCount ?? 0} 条`);
      void loadPricedList();
      // 冲销后待结算回落：同步刷新队列余额
      void reloadPatientQueue();
    },
  },
);

/** 加载计费初始化（耗材房）；门禁失败静默清空 */
async function loadInit() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    locationOptions.value = [];
    defaultLocationId.value = '';
    return;
  }
  try {
    await runInit({ encounterId });
  } catch {
    locationOptions.value = [];
    defaultLocationId.value = '';
  }
}

/** 分页查询选品目录；失败静默清空 */
async function loadCatalog() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    catalogRows.value = [];
    catalogTotal.value = 0;
    return;
  }
  try {
    await runPageCatalog({
      pageNo: catalogPage.pageNo,
      pageSize: catalogPage.pageSize,
      encounterId,
      keyword: catalogKeyword.value.trim() || undefined,
      locationId: preserveApiIdForRequest(catalogLocationId.value) || undefined,
    });
  } catch {
    catalogRows.value = [];
    catalogTotal.value = 0;
  }
}

/** 加载已划价列表；失败静默清空 */
async function loadPricedList() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    pricedRows.value = [];
    return;
  }
  try {
    await runListPriced({ encounterId });
  } catch {
    pricedRows.value = [];
  }
}

/** 切换就诊时全量刷新；门诊不打计费接口 */
async function bootstrapForEncounter() {
  draftItems.value = [];
  catalogPage.pageNo = 1;
  // 计费契约仅住院；门诊场景直接清空本地态
  if (encounterScope.value !== 'INPATIENT') {
    locationOptions.value = [];
    defaultLocationId.value = '';
    catalogRows.value = [];
    catalogTotal.value = 0;
    pricedRows.value = [];
    return;
  }
  await loadInit();
  await Promise.all([loadCatalog(), loadPricedList()]);
}

/** 关键字回车/清空重查选品 */
function handleCatalogSearch() {
  catalogPage.pageNo = 1;
  void loadCatalog();
}

/** 耗材房变更重查选品 */
function handleLocationChange() {
  catalogPage.pageNo = 1;
  void loadCatalog();
}

/** 选品分页 */
function handleCatalogPageChange(page: number) {
  catalogPage.pageNo = page;
  void loadCatalog();
}

/** 目录行是否已在待划价（同定义、同批号） */
function isCatalogRowInDraft(row: MedicalTechDeviceCatalogRowDto): boolean {
  const definitionId = preserveApiIdForRequest(row.definitionId);
  if (!definitionId) {
    return false;
  }
  const lot = String(row.lotNumber ?? '').trim();
  return draftItems.value.some(
    (item) => item.definitionId === definitionId && String(item.lotNumber ?? '').trim() === lot,
  );
}

/** 目录行样式：可点加入；已在草稿中高亮 */
function catalogRowClassName(data: { row: MedicalTechDeviceCatalogRowDto }) {
  if (isCatalogRowInDraft(data.row)) {
    return 'mt-charging-catalog-row is-picked';
  }
  return 'mt-charging-catalog-row';
}

/** 构建同品同批同库同单位合并键 */
function buildDraftMergeKey(
  definitionId: string,
  locationId: string,
  lotNumber: null | string | undefined,
  bandKind: string,
): string {
  return [
    definitionId,
    locationId,
    String(lotNumber ?? '').trim(),
    String(bandKind ?? '')
      .trim()
      .toUpperCase(),
  ].join('|');
}

/** 点击目录行加入待划价；同键重复点击数量 +1 */
function handleCatalogRowClick(row: MedicalTechDeviceCatalogRowDto) {
  const definitionId = preserveApiIdForRequest(row.definitionId);
  const locationId =
    preserveApiIdForRequest(row.locationId) ||
    preserveApiIdForRequest(catalogLocationId.value) ||
    preserveApiIdForRequest(defaultLocationId.value);
  if (!definitionId || !locationId) {
    ElMessage.warning('该行缺少定义或库房，无法加入');
    return;
  }
  const selectedBandKind = resolveMedicalTechDefaultBandKind(row);
  const unitCode = resolveMedicalTechUnitCodeByBand(row, selectedBandKind);
  if (!unitCode) {
    ElMessage.warning('该行缺少计价单位，无法加入');
    return;
  }
  const mergeKey = buildDraftMergeKey(definitionId, locationId, row.lotNumber, selectedBandKind);
  // 同品同批同单位：数量累加，避免草稿表重复堆行
  const existing = draftItems.value.find(
    (item) =>
      buildDraftMergeKey(
        item.definitionId,
        item.locationId,
        item.lotNumber,
        item.selectedBandKind,
      ) === mergeKey,
  );
  if (existing) {
    existing.quantity = Number(existing.quantity) + 1;
    return;
  }
  draftSeq += 1;
  draftItems.value.push({
    rowKey: `draft-${draftSeq}`,
    catalog: row,
    definitionId,
    quantity: 1,
    selectedBandKind,
    displayUnitCode: unitCode,
    displayUnitName: resolveMedicalTechUnitNameByBand(row, selectedBandKind),
    displayUnitPrice: resolveMedicalTechUnitPriceByBand(row, selectedBandKind),
    locationId,
    lotNumber: row.lotNumber ?? null,
    itemName: row.itemName ?? '',
    itemCode: row.itemCode ?? '',
    specification: row.specification ?? '',
  });
}

/** 已选行可切换单位选项 */
function unitBandOptionsForDraft(row: DraftPricingRow): MedicalTechUnitBandSelectOption[] {
  return listMedicalTechUnitBandOptions(row.catalog);
}

/** 切换草稿行单位槽：换算数量并刷新单价/单位码 */
function handleDraftBandKindChange(rowKey: string, bandKind: string) {
  const target = draftItems.value.find((item) => item.rowKey === rowKey);
  if (!target) {
    return;
  }
  const nextKind = String(bandKind ?? '')
    .trim()
    .toUpperCase();
  if (!nextKind || nextKind === target.selectedBandKind) {
    return;
  }
  const nextQty = convertMedicalTechQtyBetweenBandKinds(
    target.quantity,
    target.selectedBandKind,
    nextKind,
    target.catalog,
  );
  target.quantity = nextQty;
  target.selectedBandKind = nextKind;
  target.displayUnitCode = resolveMedicalTechUnitCodeByBand(target.catalog, nextKind);
  target.displayUnitName = resolveMedicalTechUnitNameByBand(target.catalog, nextKind);
  target.displayUnitPrice = resolveMedicalTechUnitPriceByBand(target.catalog, nextKind);
}

/** 移除草稿行 */
function handleRemoveDraft(rowKey: string) {
  // 按本地行键剔除
  draftItems.value = draftItems.value.filter((item) => item.rowKey !== rowKey);
}

/** 清空待划价草稿 */
function handleClearDraft() {
  draftItems.value = [];
}

/** 确认划价 */
async function handleConfirmPricing() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (draftItems.value.length === 0) {
    ElMessage.warning('请先从目录点击加入耗材');
    return;
  }

  // 组装写行：数量须 > 0，ID 一律 preserve
  const items: MedicalTechDevicePricingWriteItemDto[] = [];
  for (const draft of draftItems.value) {
    const qty = Number(draft.quantity);
    if (!Number.isFinite(qty) || qty <= 0) {
      ElMessage.warning(`「${draft.itemName || draft.definitionId}」数量须大于 0`);
      return;
    }
    const definitionId = preserveApiIdForRequest(draft.definitionId);
    const locationId = preserveApiIdForRequest(draft.locationId);
    const unitCode = String(draft.displayUnitCode ?? '').trim();
    if (!definitionId || !locationId || !unitCode) {
      ElMessage.warning('草稿行主键或单位无效');
      return;
    }
    items.push({
      definitionId,
      quantity: draft.quantity,
      unitCode,
      locationId,
      lotNumber: draft.lotNumber ?? null,
    });
  }

  await runConfirm({ encounterId, items });
}

/** 冲销单条可取消划价 */
async function handleCancelPriced(row: MedicalTechPricedDeviceRowDto) {
  if (row.cancellable !== true) {
    ElMessage.warning('该行不可冲销');
    return;
  }
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  const requestId = preserveApiIdForRequest(row.requestId);
  if (!encounterId || !requestId) {
    ElMessage.warning('主键无效');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认冲销「${row.itemName || requestId}」？`, '取消划价', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await runCancelPricing({
    encounterId,
    items: [{ requestId }],
  });
}

watch(
  selectedEncounterId,
  () => {
    void bootstrapForEncounter();
  },
  { immediate: true },
);
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-col bg-white">
    <div
      v-if="!selectedEncounterId"
      class="text-text-sub flex flex-1 items-center justify-center text-sm"
    >
      请先选择住院患者
    </div>

    <div v-else class="flex min-h-0 min-w-0 flex-1">
      <!-- 选品：点行或「加入」进入待划价 -->
      <div class="flex min-h-0 min-w-0 flex-1 flex-col">
        <div class="flex shrink-0 flex-nowrap items-center gap-2 px-3 pb-2 pt-2">
          <SectionTitle class="shrink-0" title="耗材选品" />
          <div class="w-[148px] shrink-0">
            <ElSelect
              v-model="catalogLocationId"
              clearable
              class="w-full"
              placeholder="耗材房"
              :loading="initLoading"
              @change="handleLocationChange"
            >
              <ElOption
                v-for="opt in locationOptions"
                :key="String(opt.value)"
                :label="opt.label"
                :value="String(opt.value)"
              />
            </ElSelect>
          </div>
          <div class="w-[168px] shrink-0">
            <ElInput
              v-model="catalogKeyword"
              class="w-full"
              clearable
              placeholder="品名 / 编码，回车查询"
              @clear="handleCatalogSearch"
              @keyup.enter="handleCatalogSearch"
            />
          </div>
        </div>
        <div class="min-h-0 min-w-0 flex-1 px-3">
          <ElTable
            v-loading="catalogLoading"
            :data="catalogRows"
            border
            class="mt-charging-table mt-charging-catalog-table h-full"
            height="100%"
            stripe
            :row-class-name="catalogRowClassName"
            @row-click="handleCatalogRowClick"
          >
            <ElTableColumn
              align="center"
              label="编码"
              min-width="96"
              prop="itemCode"
              show-overflow-tooltip
            />
            <ElTableColumn label="项目名称" min-width="120" prop="itemName" show-overflow-tooltip />
            <ElTableColumn
              align="center"
              label="规格"
              min-width="72"
              prop="specification"
              show-overflow-tooltip
            />
            <ElTableColumn
              align="center"
              label="单位"
              prop="unitName"
              show-overflow-tooltip
              width="64"
            />
            <ElTableColumn
              align="center"
              label="批号"
              prop="lotNumber"
              show-overflow-tooltip
              width="88"
            />
            <ElTableColumn align="center" label="库存" prop="availableQty" width="64" />
            <ElTableColumn align="right" label="单价" width="80">
              <template #default="{ row }">
                {{ formatMedicalTechUnitPrice(row.unitPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn align="center" label="操作" width="64">
              <template #default="{ row }">
                <ElButton link type="primary" @click.stop="handleCatalogRowClick(row)">
                  {{ isCatalogRowInDraft(row) ? '+1' : '加入' }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
        <div class="flex shrink-0 justify-end px-3 py-1">
          <ElPagination
            :current-page="catalogPage.pageNo"
            :page-size="catalogPage.pageSize"
            :total="catalogTotal"
            layout="total, prev, next"
            small
            @current-change="handleCatalogPageChange"
          />
        </div>
      </div>

      <!-- 待划价贴在上方，没选时不占空表；已划价用剩余高度 -->
      <div
        class="flex min-h-0 min-w-0 flex-1 flex-col border-l border-[var(--el-border-color-lighter)]"
      >
        <div class="flex shrink-0 flex-nowrap items-center gap-2 px-3 py-2">
          <SectionTitle class="shrink-0" :title="`待划价（${draftItems.length}）`" />
          <span
            v-if="draftItems.length > 0"
            class="shrink-0 text-sm text-[var(--el-text-color-primary)]"
          >
            合计 {{ draftAmountLabel }}
          </span>
          <div class="ml-auto flex shrink-0 items-center gap-1">
            <ElButton
              text
              type="danger"
              :disabled="draftItems.length === 0"
              @click="handleClearDraft"
            >
              清空
            </ElButton>
            <ElButton
              type="primary"
              :loading="confirmLoading"
              :disabled="draftItems.length === 0"
              @click="handleConfirmPricing"
            >
              确认划价
            </ElButton>
          </div>
        </div>
        <p
          v-if="draftItems.length === 0"
          class="shrink-0 px-3 pb-2 text-xs leading-snug text-[var(--el-text-color-regular)]"
        >
          点左侧耗材或「加入」，改数量后确认划价
        </p>
        <div v-else class="min-w-0 shrink-0 px-3 pb-2">
          <ElTable :data="draftItems" border class="mt-charging-table" :max-height="280" stripe>
            <ElTableColumn label="项目" min-width="120">
              <template #default="{ row }">
                <div class="truncate leading-snug">{{ row.itemName || '—' }}</div>
                <div class="truncate text-xs text-[var(--el-text-color-regular)]">
                  {{ [row.specification, row.lotNumber].filter(Boolean).join(' · ') || '—' }}
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn align="center" label="单位" width="88">
              <template #default="{ row }">
                <ElSelect
                  v-if="unitBandOptionsForDraft(row).length > 1"
                  :model-value="row.selectedBandKind"
                  size="small"
                  class="w-full"
                  @change="(v) => handleDraftBandKindChange(row.rowKey, String(v ?? ''))"
                  @click.stop
                >
                  <ElOption
                    v-for="opt in unitBandOptionsForDraft(row)"
                    :key="opt.bandKind"
                    :label="opt.unitName"
                    :value="opt.bandKind"
                  />
                </ElSelect>
                <span v-else>{{ row.displayUnitName || '—' }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn align="right" label="单价" width="76">
              <template #default="{ row }">
                {{ formatMedicalTechUnitPrice(row.displayUnitPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn align="center" label="数量" width="120">
              <template #default="{ row }">
                <ElInputNumber
                  v-model="row.quantity"
                  class="mt-charging-qty"
                  :min="0.01"
                  :step="1"
                  controls-position="right"
                  size="small"
                  @click.stop
                />
              </template>
            </ElTableColumn>
            <ElTableColumn align="right" label="金额" width="76">
              <template #default="{ row }">
                {{ formatMedicalTechDraftLineAmount(row.quantity, row.displayUnitPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn align="center" label="" width="52">
              <template #default="{ row }">
                <ElButton link type="danger" @click.stop="handleRemoveDraft(row.rowKey)">
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>

        <div
          class="flex min-h-0 min-w-0 flex-1 flex-col border-t border-[var(--el-border-color-lighter)]"
        >
          <div class="shrink-0 px-3 py-2">
            <SectionTitle :title="`已划价（${pricedRows.length}）`" />
          </div>
          <div class="min-h-0 min-w-0 flex-1 px-3 pb-2">
            <ElTable
              v-loading="pricedLoading || cancelLoading"
              :data="pricedRows"
              border
              class="mt-charging-table h-full"
              height="100%"
              stripe
            >
              <ElTableColumn label="品名" min-width="100" prop="itemName" show-overflow-tooltip />
              <ElTableColumn align="right" label="数量" prop="quantity" width="64" />
              <ElTableColumn align="right" label="金额" prop="totalPrice" width="72" />
              <ElTableColumn align="center" label="确费人" min-width="72" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.feeConfirmerName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="计费人" min-width="72" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.billingPractitionerName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                align="center"
                label="划价时间"
                prop="pricedTime"
                show-overflow-tooltip
                width="156"
              />
              <ElTableColumn align="center" label="操作" width="64">
                <template #default="{ row }">
                  <ElButton
                    v-if="row.cancellable"
                    link
                    type="danger"
                    @click="handleCancelPriced(row)"
                  >
                    冲销
                  </ElButton>
                  <span v-else class="text-xs text-[var(--el-text-color-regular)]">—</span>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
