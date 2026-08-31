<script lang="ts" setup>
import type { DeviceCatalogItem, ServiceCatalogItem } from '#/api';
import type { LabDeviceItem } from '#/api/basic/lab-device';
import type {
  PageSpecimenTubeMergeDevicesParams,
  SpecimenTubeMergeConsumableLine,
} from '#/api/basic/specimen-tube-merge';
import type { ApiOptionItem } from '#/types/api-response';
import type { BatchActivityPickRow } from '#/views/config/specimentubemerge/batch-add-activity-dialog.vue';

import { computed, onActivated, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCheckbox,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getServiceCatalogApi, pageDeviceCatalogApi, pageServiceCatalogApi } from '#/api';
import {
  getSpecimenTubeMergeBindingsApi,
  BLOOD_COLLECTION_FEE_LABEL,
  LAB_ACTIVITY_CATEGORY_CODE,
  pageSpecimenTubeMergeDevicesApi,
  saveSpecimenTubeMergeBindingsApi,
  SPECIMEN_TUBE_MERGE_TARGET_TABLE_DEVICE,
  specimenTubeMergeUnitOptionsByTargetApi,
} from '#/api/basic/specimen-tube-merge';
import SelectTable from '#/components/select-table.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import SpecimenTubeMergeBatchAddActivityDialog from '#/views/config/specimentubemerge/batch-add-activity-dialog.vue';

defineOptions({ name: 'SpecimenTubeMergeConfigPage' });

interface DeviceOptionItem {
  label: string;
  value: string;
  busNo?: string;
}

type EditableActivityRow = {
  __rowKey?: string;
  targetId?: string;
  selectedLabel?: string;
};

type EditableConsumableRow = {
  __rowKey?: string;
  __unitOptions?: ApiOptionItem[];
  __unitRequestSeq?: number;
  quantity?: number;
  targetId?: string;
  selectedLabel?: string;
  unitCode?: string;
};

/** KeepAlive 首次进入时 onMounted 已加载，跳过紧随其后的 onActivated */
let skipActivatedOnce = false;
let unitOptionsRequestSeq = 0;

const deviceKeyword = ref('');
const selectedDeviceId = ref('');
const deviceOptions = ref<DeviceOptionItem[]>([]);
const deviceLoading = ref(false);

const activityRows = ref<EditableActivityRow[]>([]);
const bindBloodCollectionFee = ref(false);
const consumableRows = ref<EditableConsumableRow[]>([]);
const bindingsLoading = ref(false);
/** 检验项目批量添加弹窗 */
const batchActivityDialogVisible = ref(false);

const deviceQueryParams = ref<PageSpecimenTubeMergeDevicesParams>({
  pageNo: 1,
  pageSize: 200,
  keyword: '',
});

/** 检验项目选品列（对齐诊疗目录列表 + 进货选品信息密度） */
const labActivityPickColumns = [
  { prop: 'busNo', label: '编码', width: 140 },
  { prop: 'name', label: '项目名称', minWidth: 180 },
  { prop: 'categoryCode_dictText', label: '分类', width: 110 },
  { prop: 'packageUnitCode_dictText', label: '单位', width: 88 },
  { prop: 'retailPrice', label: '售价', width: 96, align: 'right' as const },
  { prop: 'specification', label: '规格', width: 120 },
];

/** 耗材选品列（对齐器材目录列表 + 进货选品） */
const consumablePickColumns = [
  { prop: 'busNo', label: '编码', width: 140 },
  { prop: 'name', label: '项目名称', minWidth: 160 },
  { prop: 'specification', label: '规格', width: 120 },
  { prop: 'packageUnitCode_dictText', label: '包装单位', width: 96 },
  { prop: 'splitUnitCode_dictText', label: '最小单位', width: 96 },
  { prop: 'retailPrice', label: '售价', width: 96, align: 'right' as const },
  { prop: 'manufacturerText', label: '厂家', minWidth: 140 },
];

const { run: runPageDevices } = useRequest(pageSpecimenTubeMergeDevicesApi, { manual: true });
const { run: runGetBindings } = useRequest(getSpecimenTubeMergeBindingsApi, { manual: true });
const { run: runSaveBindings, loading: saveLoading } = useRequest(
  saveSpecimenTubeMergeBindingsApi,
  {
    manual: true,
  },
);

/** 左侧设备列表关键字过滤 */
const filteredDeviceOptions = computed<DeviceOptionItem[]>(() => {
  const keyword = deviceKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return deviceOptions.value;
  }
  return deviceOptions.value.filter((opt) => {
    return (
      opt.label.toLowerCase().includes(keyword) ||
      opt.value.toLowerCase().includes(keyword) ||
      String(opt.busNo ?? '')
        .toLowerCase()
        .includes(keyword)
    );
  });
});

/** 当前选中设备展示名 */
const selectedDeviceLabel = computed(() => {
  const found = deviceOptions.value.find((opt) => opt.value === selectedDeviceId.value);
  return found?.label ?? '';
});

/** 已维护检验项目 id（含未保存草稿），供批量选品排除 */
const boundActivityIdsForExclude = computed(() => {
  const ids: string[] = [];
  for (const row of activityRows.value) {
    const id = String(row.targetId ?? '').trim();
    if (id) {
      ids.push(id);
    }
  }
  return ids;
});

/** 格式化选品列表中的售价展示 */
function formatPickRetailPrice(price: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const num = Number(price);
  return Number.isFinite(num) ? formatMoneyDisplay(num) : String(price);
}

/** 诊疗目录是否启用态 */
function isServicePublicationActive(row: ServiceCatalogItem): boolean {
  const status = row.statusEnum;
  if (
    status === PublicationStatusCode.INACTIVE ||
    status === PublicationStatusCode.DISCARDED ||
    status === 3 ||
    status === '3'
  ) {
    return false;
  }
  return (
    status === PublicationStatusCode.ACTIVE ||
    status === 1 ||
    status === '1' ||
    status === 2 ||
    status === '2'
  );
}

/** 耗材目录是否启用态 */
function isDevicePublicationActive(row: DeviceCatalogItem): boolean {
  const status = row.statusEnum;
  if (
    status === PublicationStatusCode.INACTIVE ||
    status === PublicationStatusCode.DISCARDED ||
    status === 3 ||
    status === '3'
  ) {
    return false;
  }
  return (
    status === PublicationStatusCode.ACTIVE ||
    status === 1 ||
    status === '1' ||
    status === 2 ||
    status === '2'
  );
}

/** 为检验项目行生成稳定 key */
function ensureActivityRowKey(row: EditableActivityRow) {
  if (row.__rowKey) {
    return row.__rowKey;
  }
  const key = `act:${Math.random().toString(36).slice(2)}`;
  row.__rowKey = key;
  return key;
}

/** 为耗材行生成稳定 key */
function ensureConsumableRowKey(row: EditableConsumableRow) {
  if (row.__rowKey) {
    return row.__rowKey;
  }
  const key = `tmp:${Math.random().toString(36).slice(2)}`;
  row.__rowKey = key;
  return key;
}

/** 将设备分页结果映射为左侧候选 */
function mapDeviceOptions(list: LabDeviceItem[]): DeviceOptionItem[] {
  return (list ?? [])
    .map((item) => {
      const id = item.id !== null && item.id !== undefined ? String(item.id) : '';
      if (!id) {
        return null;
      }
      const name = String(item.name ?? '').trim();
      const busNo = String(item.busNo ?? '').trim();
      const label = busNo ? `${name}（${busNo}）` : name || id;
      return {
        label,
        value: id,
        busNo,
      };
    })
    .filter((item): item is DeviceOptionItem => item !== null);
}

/**
 * 收集单行选品应排除的 id：其它行已选项目不进列表；
 * 当前行自身保留，便于再次打开下拉时高亮已选。
 *
 * @param excludeRow 当前编辑行（可空）
 */
function collectActivityExcludeIds(excludeRow?: EditableActivityRow): Set<string> {
  const set = new Set<string>();
  for (const row of activityRows.value) {
    if (row === excludeRow) {
      continue;
    }
    const id = String(row.targetId ?? '').trim();
    if (id) {
      set.add(id);
    }
  }
  return set;
}

/** 分页检索检验类诊疗项目（category=22）供 SelectTable；排除其它行已绑定 */
async function searchLabActivityPick(
  params: {
    keyword?: string;
    pageNo: number;
    pageSize: number;
  },
  excludeRow?: EditableActivityRow,
) {
  const { pageNo, pageSize, keyword } = params;
  const excludeIds = collectActivityExcludeIds(excludeRow);
  try {
    const response = await pageServiceCatalogApi({
      pageNo,
      pageSize,
      keyword: keyword?.trim() || undefined,
      categoryCode: LAB_ACTIVITY_CATEGORY_CODE,
      includeReferencedPackageChildren: true,
    });
    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response?.data);
    // 启用态过滤；其它行已维护项剔除，本行已选仍保留以便下拉回显
    const content = parsed.content
      .filter((row) => {
        if (!isServicePublicationActive(row)) {
          return false;
        }
        const id = String(row.id ?? '').trim();
        if (id && excludeIds.has(id)) {
          return false;
        }
        return true;
      })
      .map((row) => ({
        ...row,
        retailPrice: formatPickRetailPrice(row.retailPrice),
      }));
    return {
      content,
      totalCount: parsed.totalCount,
    };
  } catch {
    ElMessage.error('加载检验项目失败，请稍后重试');
    return { content: [], totalCount: 0 };
  }
}

/** 分页检索启用态耗材供 SelectTable */
async function searchConsumablePick(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  const { pageNo, pageSize, keyword } = params;
  try {
    const response = await pageDeviceCatalogApi({
      pageNo,
      pageSize,
      keyword: keyword?.trim() || undefined,
    });
    const parsed = parsePagedRowsFromApiData<DeviceCatalogItem>(response?.data);
    const content = parsed.content
      .filter((row) => isDevicePublicationActive(row))
      .map((row) => ({
        ...row,
        retailPrice: formatPickRetailPrice(row.retailPrice),
      }));
    return {
      content,
      totalCount: parsed.totalCount,
    };
  } catch {
    ElMessage.error('加载耗材失败，请稍后重试');
    return { content: [], totalCount: 0 };
  }
}

/** 加载左侧启用态检验设备列表 */
async function loadDevices() {
  deviceLoading.value = true;
  try {
    const data = await runPageDevices({
      ...deviceQueryParams.value,
      keyword: deviceKeyword.value.trim() || deviceQueryParams.value.keyword,
    });
    deviceOptions.value = mapDeviceOptions(data?.content ?? []);
  } catch {
    deviceOptions.value = [];
  } finally {
    deviceLoading.value = false;
  }
}

/** 回填已选检验项目的展示名 */
async function enrichActivityRowLabels() {
  for (const row of activityRows.value) {
    if (!row.targetId || row.selectedLabel) {
      continue;
    }
    try {
      const response = await getServiceCatalogApi({ id: row.targetId });
      const name = String(response?.data?.name ?? '').trim();
      row.selectedLabel = name || row.targetId;
    } catch {
      row.selectedLabel = row.targetId;
    }
  }
}

/** 将绑定详情写入右侧表单 */
function applyBindings(
  bindings: {
    activityIds?: string[];
    bindBloodCollectionFee?: boolean;
    consumableLines?: SpecimenTubeMergeConsumableLine[];
  } | null,
) {
  activityRows.value = (bindings?.activityIds ?? []).map((id) => {
    const row: EditableActivityRow = { targetId: String(id) };
    ensureActivityRowKey(row);
    return row;
  });
  bindBloodCollectionFee.value = Boolean(bindings?.bindBloodCollectionFee);
  consumableRows.value = (bindings?.consumableLines ?? []).map((line) => {
    const row: EditableConsumableRow = {
      targetId: line.targetId ? String(line.targetId) : undefined,
      selectedLabel: line.targetName ? String(line.targetName) : undefined,
      quantity: line.quantity,
      unitCode: line.unitCode ? String(line.unitCode) : undefined,
      __unitOptions: line.unitCode
        ? [{ label: line.unitName || line.unitCode, value: String(line.unitCode) }]
        : undefined,
    };
    ensureConsumableRowKey(row);
    return row;
  });
}

/** 加载当前设备的合管绑定 */
async function loadBindings() {
  if (!selectedDeviceId.value) {
    applyBindings(null);
    return;
  }
  bindingsLoading.value = true;
  try {
    const bindings = await runGetBindings(selectedDeviceId.value);
    applyBindings(bindings ?? null);
    await enrichActivityRowLabels();
    for (const row of consumableRows.value) {
      if (row.targetId) {
        loadUnitOptionsForRow(row);
      }
    }
  } catch {
    applyBindings(null);
  } finally {
    bindingsLoading.value = false;
  }
}

/** 选中左侧设备并加载绑定 */
function handleSelectDevice(deviceId: string) {
  if (selectedDeviceId.value === deviceId) {
    return;
  }
  selectedDeviceId.value = deviceId;
  loadBindings();
}

/** 选中首个设备（无候选时清空右侧） */
function selectFirstDevice() {
  const first = filteredDeviceOptions.value[0];
  if (first) {
    selectedDeviceId.value = first.value;
    loadBindings();
    return;
  }
  selectedDeviceId.value = '';
  applyBindings(null);
}

/** 左侧搜索回车触发服务端检索 */
function handleDeviceSearch() {
  deviceQueryParams.value.pageNo = 1;
  loadDevices().then(() => {
    if (
      selectedDeviceId.value &&
      !deviceOptions.value.some((opt) => opt.value === selectedDeviceId.value)
    ) {
      selectFirstDevice();
    }
  });
}

/** 新增一行检验项目绑定 */
function addActivityRow() {
  const row: EditableActivityRow = {};
  ensureActivityRowKey(row);
  activityRows.value.push(row);
}

/** 打开检验项目批量添加弹窗 */
function openBatchActivityDialog() {
  if (!selectedDeviceId.value) {
    ElMessage.warning('请先在左侧选择检验设备');
    return;
  }
  batchActivityDialogVisible.value = true;
}

/**
 * 批量选品确认：优先填满空行，其余追加；与单行维护共用 activityRows。
 *
 * @param items 弹窗确认的检验项目
 */
function handleBatchActivityConfirm(items: BatchActivityPickRow[]) {
  if (!items.length) {
    return;
  }
  const existingIds = new Set(boundActivityIdsForExclude.value);
  let filled = 0;
  for (const item of items) {
    const id = String(item.id ?? '').trim();
    if (!id || existingIds.has(id)) {
      continue;
    }
    existingIds.add(id);
    // 优先占用尚未选品的空行，避免「添加行 + 批量」并存产生多余空行
    const emptyRow = activityRows.value.find((row) => !String(row.targetId ?? '').trim());
    if (emptyRow) {
      emptyRow.targetId = id;
      emptyRow.selectedLabel = String(item.name ?? '').trim() || id;
      filled++;
      continue;
    }
    const row: EditableActivityRow = {
      targetId: id,
      selectedLabel: String(item.name ?? '').trim() || id,
    };
    ensureActivityRowKey(row);
    activityRows.value.push(row);
    filled++;
  }
  if (filled === 0) {
    ElMessage.info('所选项目均已在绑定列表中');
    return;
  }
  ElMessage.success(`已添加 ${filled} 条检验项目`);
}

/** 删除检验项目绑定行 */
function removeActivityRow(index: number) {
  activityRows.value.splice(index, 1);
}

/** 检验项目是否与其他行重复 */
function isDuplicateActivityId(targetId: string, excludeRow?: EditableActivityRow): boolean {
  return activityRows.value.some(
    (row) => row !== excludeRow && String(row.targetId ?? '') === targetId,
  );
}

/** 检验项目选品确认：写展示名并校验重复 */
async function handleActivityPickClose(
  row: EditableActivityRow,
  item: Record<string, unknown>,
): Promise<boolean> {
  const id = String(item.id ?? '').trim();
  if (id && isDuplicateActivityId(id, row)) {
    ElMessage.warning('该检验项目已添加');
    return false;
  }
  row.selectedLabel = String(item.name ?? '');
  return true;
}

/** 检验项目清空时同步行状态 */
function handleActivityIdChange(row: EditableActivityRow, value: string | undefined) {
  if (!value) {
    row.targetId = undefined;
    row.selectedLabel = undefined;
    return;
  }
  row.targetId = String(value);
}

/** 新增一行耗材绑定 */
function addConsumableRow() {
  const row: EditableConsumableRow = {
    quantity: 1,
    __unitOptions: undefined,
  };
  ensureConsumableRowKey(row);
  consumableRows.value.push(row);
}

/** 删除耗材绑定行 */
function removeConsumableRow(index: number) {
  consumableRows.value.splice(index, 1);
}

/** 耗材目标变更后联动单位下拉 */
async function loadUnitOptionsForRow(row: EditableConsumableRow) {
  const targetId = String(row.targetId ?? '').trim();
  if (!targetId) {
    row.__unitOptions = undefined;
    row.unitCode = undefined;
    return;
  }
  try {
    const requestSeq = ++unitOptionsRequestSeq;
    row.__unitRequestSeq = requestSeq;
    const data = await specimenTubeMergeUnitOptionsByTargetApi({
      targetTable: SPECIMEN_TUBE_MERGE_TARGET_TABLE_DEVICE,
      targetId,
    });
    if (row.__unitRequestSeq !== requestSeq) {
      return;
    }
    row.__unitOptions = data?.unitOptions ?? [];
    const allowed = new Set((row.__unitOptions ?? []).map((opt) => String(opt.value)));
    const current = row.unitCode ? String(row.unitCode) : '';
    const preferred = data?.unitCode ? String(data.unitCode) : '';
    if (!current || !allowed.has(current)) {
      row.unitCode = preferred || row.__unitOptions?.[0]?.value;
    }
  } catch {
    row.__unitOptions = undefined;
    row.unitCode = undefined;
    ElMessage.error('获取单位信息失败，请稍后重试');
  }
}

/** 耗材是否与其他行重复 */
function isDuplicateConsumableId(targetId: string, excludeRow?: EditableConsumableRow): boolean {
  return consumableRows.value.some(
    (row) => row !== excludeRow && String(row.targetId ?? '') === targetId,
  );
}

/** 耗材选品确认：写展示名并校验重复 */
async function handleConsumablePickClose(
  row: EditableConsumableRow,
  item: Record<string, unknown>,
): Promise<boolean> {
  const id = String(item.id ?? '').trim();
  if (id && isDuplicateConsumableId(id, row)) {
    ElMessage.warning('该耗材已添加');
    return false;
  }
  row.selectedLabel = String(item.name ?? '');
  return true;
}

/** 耗材目标变更后刷新单位下拉 */
async function handleConsumableTargetChange(row: EditableConsumableRow) {
  row.unitCode = undefined;
  row.__unitOptions = undefined;
  await loadUnitOptionsForRow(row);
}

/** 耗材清空或选中后同步行状态并联动单位 */
async function handleConsumableIdChange(row: EditableConsumableRow, value: string | undefined) {
  if (!value) {
    row.targetId = undefined;
    row.selectedLabel = undefined;
    row.unitCode = undefined;
    row.__unitOptions = undefined;
    return;
  }
  row.targetId = String(value);
  await handleConsumableTargetChange(row);
}

/** 全量保存当前设备合管绑定 */
async function handleSaveBindings() {
  if (!selectedDeviceId.value) {
    ElMessage.warning('请先在左侧选择检验设备');
    return;
  }

  const activityIds: string[] = [];
  for (const row of activityRows.value) {
    if (!row.targetId) {
      continue;
    }
    const id = String(row.targetId);
    if (activityIds.includes(id)) {
      ElMessage.warning('检验项目不能重复');
      return;
    }
    activityIds.push(id);
  }

  for (const row of consumableRows.value) {
    if (!row.targetId) {
      ElMessage.warning('请选择耗材');
      return;
    }
    if (!row.quantity || row.quantity <= 0 || !Number.isInteger(row.quantity)) {
      ElMessage.warning('耗材数量必须为正整数');
      return;
    }
    if (!row.unitCode) {
      ElMessage.warning('请选择耗材单位');
      return;
    }
  }

  const payload = {
    deviceId: selectedDeviceId.value,
    activityIds,
    bindBloodCollectionFee: bindBloodCollectionFee.value,
    consumableLines: consumableRows.value.map((row, index) => ({
      targetId: preserveApiIdForRequest(row.targetId) ?? String(row.targetId),
      quantity: row.quantity as number,
      unitCode: String(row.unitCode),
      displayOrder: index + 1,
    })),
  };

  try {
    await runSaveBindings(payload);
    ElMessage.success('保存成功');
    await loadBindings();
  } catch {
    // useRequest 已提示
  }
}

onActivated(async () => {
  if (skipActivatedOnce) {
    skipActivatedOnce = false;
    return;
  }
  const prevDeviceId = selectedDeviceId.value;
  await loadDevices();
  if (prevDeviceId && deviceOptions.value.some((opt) => opt.value === prevDeviceId)) {
    selectedDeviceId.value = prevDeviceId;
    await loadBindings();
    return;
  }
  selectFirstDevice();
});

onMounted(async () => {
  skipActivatedOnce = true;
  await loadDevices();
  selectFirstDevice();
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full min-h-0 gap-3">
      <aside
        class="flex w-72 shrink-0 flex-col border-r border-[var(--el-border-color-light)] pr-2"
      >
        <div class="mb-2 text-sm font-medium text-[var(--el-text-color-primary)]">检验设备</div>
        <ElInput
          v-model="deviceKeyword"
          class="mb-2"
          clearable
          placeholder="搜索设备名称/编码"
          @keyup.enter="handleDeviceSearch"
          @clear="handleDeviceSearch"
        />

        <div v-loading="deviceLoading" class="min-h-0 flex-1 overflow-y-auto">
          <div
            v-for="opt in filteredDeviceOptions"
            :key="opt.value"
            class="mb-1 cursor-pointer rounded px-2 py-2 text-sm transition-colors"
            :class="
              selectedDeviceId === opt.value
                ? 'bg-[var(--el-color-primary-light-9)] text-[var(--el-color-primary)]'
                : 'text-[var(--el-text-color-primary)] hover:bg-[var(--el-fill-color-light)]'
            "
            @click="handleSelectDevice(opt.value)"
          >
            {{ opt.label }}
          </div>
          <div
            v-if="filteredDeviceOptions.length === 0"
            class="py-8 text-center text-sm text-[var(--el-text-color-secondary)]"
          >
            暂无启用态检验设备
          </div>
        </div>
      </aside>

      <div v-loading="bindingsLoading" class="min-w-0 flex-1">
        <template v-if="selectedDeviceId">
          <div class="mb-4 flex items-center justify-between gap-3">
            <div class="text-sm text-[var(--el-text-color-primary)]">
              当前设备：{{ selectedDeviceLabel }}
            </div>
            <ElButton type="primary" :loading="saveLoading" @click="handleSaveBindings">
              保存配置
            </ElButton>
          </div>

          <section class="mb-5">
            <div class="mb-2 flex items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-x-4 gap-y-1">
                <span class="text-sm text-[var(--el-text-color-regular)]">绑定检验项目</span>
                <ElCheckbox v-model="bindBloodCollectionFee">
                  {{ BLOOD_COLLECTION_FEE_LABEL }}
                </ElCheckbox>
              </div>
              <div class="flex items-center gap-3">
                <ElButton type="primary" link @click="openBatchActivityDialog">批量添加</ElButton>
                <ElButton type="primary" link @click="addActivityRow">添加检验项目</ElButton>
              </div>
            </div>

            <ElTable :data="activityRows" border stripe empty-text="暂无检验项目绑定">
              <ElTableColumn type="index" label="序号" width="58" align="center" />
              <ElTableColumn label="检验项目" min-width="280">
                <template #default="{ row }">
                  <SelectTable
                    :model-value="row.targetId"
                    :remote-method="(params) => searchLabActivityPick(params, row)"
                    :columns="labActivityPickColumns"
                    value-key="id"
                    label-key="name"
                    :selected-label="row.selectedLabel"
                    placeholder="请选择检验类诊疗项目"
                    filterable
                    clearable
                    panel-width="920"
                    class="w-full"
                    :before-close="(item) => handleActivityPickClose(row, item)"
                    @update:model-value="(v) => handleActivityIdChange(row, v)"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="90" align="center">
                <template #default="{ $index }">
                  <ElButton type="danger" link @click="removeActivityRow($index)">删除</ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </section>

          <section>
            <div class="mb-2 flex items-center justify-between gap-2">
              <span class="text-sm text-[var(--el-text-color-regular)]">绑定耗材</span>
              <ElButton type="primary" link @click="addConsumableRow">添加耗材</ElButton>
            </div>

            <ElTable :data="consumableRows" border stripe empty-text="暂无耗材绑定">
              <ElTableColumn type="index" label="序号" width="58" align="center" />
              <ElTableColumn label="耗材" min-width="220">
                <template #default="{ row }">
                  <SelectTable
                    :model-value="row.targetId"
                    :remote-method="searchConsumablePick"
                    :columns="consumablePickColumns"
                    value-key="id"
                    label-key="name"
                    :selected-label="row.selectedLabel"
                    placeholder="请选择耗材"
                    filterable
                    clearable
                    panel-width="920"
                    class="w-full"
                    :before-close="(item) => handleConsumablePickClose(row, item)"
                    @update:model-value="(v) => handleConsumableIdChange(row, v)"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="数量" width="150" align="center">
                <template #default="{ row }">
                  <div class="stm-qty-wrap">
                    <ElInputNumber
                      v-model="row.quantity"
                      :min="1"
                      :step="1"
                      :precision="0"
                      controls-position="right"
                    />
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="单位" width="150" align="center">
                <template #default="{ row }">
                  <ElSelect
                    v-model="row.unitCode"
                    placeholder="请选择"
                    clearable
                    class="w-full"
                    :disabled="!row.__unitOptions?.length"
                  >
                    <ElOption
                      v-for="opt in row.__unitOptions ?? []"
                      :key="opt.value"
                      :label="opt.label"
                      :value="String(opt.value)"
                    />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="90" align="center">
                <template #default="{ $index }">
                  <ElButton type="danger" link @click="removeConsumableRow($index)">删除</ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </section>
        </template>

        <ElEmpty v-else description="请在左侧选择检验设备以维护合管配置" />
      </div>
    </div>

    <SpecimenTubeMergeBatchAddActivityDialog
      v-model:visible="batchActivityDialogVisible"
      :exclude-activity-ids="boundActivityIdsForExclude"
      @confirm="handleBatchActivityConfirm"
    />
  </Page>
</template>

<style scoped>
.stm-qty-wrap {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.stm-qty-wrap :deep(.el-input-number) {
  width: 100%;
  max-width: 100%;
}

.stm-qty-wrap :deep(.el-input-number .el-input__wrapper) {
  padding-right: 42px;
  padding-left: 8px;
}
</style>
