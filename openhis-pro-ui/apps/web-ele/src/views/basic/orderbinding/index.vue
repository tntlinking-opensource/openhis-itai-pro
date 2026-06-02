<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  ActivityDeviceBindingInitResponse,
  ActivityDeviceBindingItem,
  PageActivityDeviceBindingParams,
  PageActivityDeviceBindingResponse,
  SaveActivityDeviceBindingParams,
  UnitOptionsByTargetResponse,
} from '#/api/basic/activity-device-binding';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus';

import {
  deleteActivityDeviceBindingApi,
  initActivityDeviceBindingApi,
  pageActivityDeviceBindingApi,
  saveActivityDeviceBindingApi,
  unitOptionsByTargetApi,
} from '#/api/basic/activity-device-binding';

const queryParams = ref<PageActivityDeviceBindingParams>({
  pageNo: 1,
  pageSize: 50,
});

const tableData = ref<ActivityDeviceBindingItem[]>([]);
const loading = ref(false);
const total = ref(0);

const initOptions = ref<ActivityDeviceBindingInitResponse>({});
const sourceIdSearchKey = ref('');

interface OptionItem {
  label: string;
  value: string;
}

function toOptionItems(list: Array<{ label?: string; value?: number | string }>): OptionItem[] {
  return (list ?? [])
    .map((o) => ({
      label: String(o?.label ?? ''),
      value: String(o?.value ?? ''),
    }))
    .filter((o) => o.value.trim() !== '');
}

const currentSourceIdOptions = computed<OptionItem[]>(() => {
  const bindType = String(queryParams.value.bindType ?? '').trim();
  if (!bindType) {
    return [];
  }

  const code = normalizeBindTypeToCode(bindType);
  if (!code) {
    return [];
  }
  // 绑定类型编码映射（按需求）
  // 1=号源(adm_healthcare_service) -> 来源项下拉：sourceSourceOptions
  // 2=用法(ter_concept)           -> 来源项下拉：usageSourceOptions
  // 3=诊疗(wor_activity_definition)-> 来源项下拉：activitySourceOptions
  if (code === 1) {
    return toOptionItems(initOptions.value.sourceSourceOptions ?? []);
  }
  if (code === 2) {
    return toOptionItems(initOptions.value.usageSourceOptions ?? []);
  }
  if (code === 3) {
    return toOptionItems(initOptions.value.activitySourceOptions ?? []);
  }
  return [];
});

const filteredSourceIdOptions = computed<OptionItem[]>(() => {
  const options = currentSourceIdOptions.value;
  const query = sourceIdSearchKey.value.trim();
  if (!query) {
    return options;
  }

  const normalizedQuery = query.toLowerCase();
  const matched = options.filter((opt: OptionItem) => {
    const label = String(opt.label ?? '').toLowerCase();
    const value = String(opt.value ?? '').toLowerCase();
    return label.includes(normalizedQuery) || value.includes(normalizedQuery);
  });

  matched.sort((a: OptionItem, b: OptionItem) => {
    const aLabel = String(a.label ?? '');
    const bLabel = String(b.label ?? '');

    const aExact = aLabel === query ? 1 : 0;
    const bExact = bLabel === query ? 1 : 0;
    if (aExact !== bExact) {
      return bExact - aExact;
    }

    const aStartsWith = aLabel.startsWith(query) ? 1 : 0;
    const bStartsWith = bLabel.startsWith(query) ? 1 : 0;
    if (aStartsWith !== bStartsWith) {
      return bStartsWith - aStartsWith;
    }

    return aLabel.localeCompare(bLabel, 'zh-Hans-CN');
  });

  return matched;
});

type EditableRow = {
  __backup?: Partial<ActivityDeviceBindingItem>;
  __editing?: boolean;
  __rowKey?: string;
  __unitOptions?: Array<{ label: string; value: string }>;
  __unitRequestSeq?: number;
  /**
   * 后端有些接口返回 itemId，这里与 itemNo 兼容。
   */
  itemId?: number | string;
} & ActivityDeviceBindingItem;

const editingRowKey = ref<string | undefined>(undefined);
const rowElMap = new Map<string, HTMLElement>();
const rowObjMap = new Map<string, EditableRow>();
let removeDocClickListener: (() => void) | undefined;
let unitOptionsRequestSeq = 0;

function ensureRowKey(row: EditableRow) {
  if (row.__rowKey) {
    return row.__rowKey;
  }
  // 已有记录优先用 id，避免刷新后 key 变化
  const key =
    row.id !== null && row.id !== undefined
      ? `id:${String(row.id)}`
      : `tmp:${Math.random().toString(36).slice(2)}`;
  row.__rowKey = key;
  return key;
}

function setRowEl(row: EditableRow, el: unknown) {
  const key = ensureRowKey(row);
  rowObjMap.set(key, row);
  const dom = el as HTMLElement | null;
  // 这里拿整行 tr，避免点击同一行其他单元格被误判为“行外点击”
  const tr = dom?.closest?.('tr') as HTMLElement | null;
  if (tr) {
    rowElMap.set(key, tr);
  } else if (dom) {
    rowElMap.set(key, dom);
  } else {
    rowElMap.delete(key);
  }
}

function clearRowSource(row: EditableRow) {
  row.itemNo = undefined;
  row.itemId = undefined;
}

function normalizeBindTypeToCode(value: number | string | undefined): number | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  const raw = String(value).trim();
  if (!raw) {
    return undefined;
  }

  // 兼容旧值：1/2/3
  if (raw === '1' || raw === '2' || raw === '3') {
    return Number(raw);
  }

  // 兼容表名值（按需求映射）
  if (raw === 'adm_healthcare_service') {
    return 1;
  }
  if (raw === 'ter_concept') {
    return 2;
  }
  if (raw === 'wor_activity_definition') {
    return 3;
  }

  // 兜底：根据 label 猜测（用法/诊疗/号源）
  const opt = (initOptions.value.bindTypeOptions ?? []).find(
    (o: { label?: string; value?: number | string }) => String(o.value) === raw,
  );
  const label = String(opt?.label ?? '');
  if (label.includes('用法')) {
    return 2;
  }
  if (label.includes('诊疗')) {
    return 3;
  }
  if (label.includes('号源')) {
    return 1;
  }
  return undefined;
}

function getSourceOptionsByBindType(bindType: number | string | undefined) {
  const code = normalizeBindTypeToCode(bindType);
  if (!code) {
    return [];
  }
  if (code === 1) {
    return initOptions.value.sourceSourceOptions ?? [];
  }
  if (code === 2) {
    return initOptions.value.usageSourceOptions ?? [];
  }
  if (code === 3) {
    return initOptions.value.activitySourceOptions ?? [];
  }
  return [];
}

function getTargetIdOptionsByTargetTable(targetTable: string | undefined) {
  const table = String(targetTable ?? '').trim();
  if (!table) {
    return [];
  }
  if (table === 'wor_activity_definition') {
    return initOptions.value.activitySourceOptions ?? [];
  }
  if (table === 'adm_device_definition') {
    return initOptions.value.deviceDefOptions ?? [];
  }
  return [];
}

function getOptionLabel(
  options: Array<{ label?: string; value?: number | string }>,
  value: unknown,
) {
  const v = value === null || value === undefined ? '' : String(value);
  if (!v) {
    return '';
  }
  const found = (options ?? []).find((o) => String(o?.value ?? '') === v);
  return String(found?.label ?? v);
}

function getSourceLabelByRow(row: EditableRow) {
  const typeCodeRaw = String((row as EditableRow).typeCode ?? '').trim();
  const typeCode = typeCodeRaw ? Number(typeCodeRaw) : undefined;

  // 行数据字段：后端/接口定义为 itemNo，这里兼容你说的 itemId
  const itemValue = row.itemId ?? row.itemNo;

  if (typeCode === 1) {
    return getOptionLabel(initOptions.value.sourceSourceOptions ?? [], itemValue);
  }
  if (typeCode === 2) {
    return getOptionLabel(initOptions.value.usageSourceOptions ?? [], itemValue);
  }
  if (typeCode === 3) {
    return getOptionLabel(initOptions.value.activitySourceOptions ?? [], itemValue);
  }
  return itemValue === null || itemValue === undefined ? '' : String(itemValue);
}

function getBindTypeLabelByRow(row: ActivityDeviceBindingItem & { __editing?: boolean }) {
  return getOptionLabel(initOptions.value.bindTypeOptions ?? [], (row as EditableRow).typeCode);
}

function getTargetTableLabelByRow(row: ActivityDeviceBindingItem & { __editing?: boolean }) {
  return getOptionLabel(
    initOptions.value.targetTableOptions ?? [],
    (row as EditableRow).devActTable,
  );
}

function getTargetIdLabelByRow(row: ActivityDeviceBindingItem & { __editing?: boolean }) {
  const opts = getTargetIdOptionsByTargetTable(String((row as EditableRow).devActTable ?? ''));
  return getOptionLabel(opts, (row as EditableRow).devActId);
}

function getUnitLabelByRow(row: EditableRow) {
  const opts = row.__unitOptions ?? initOptions.value.unitOptions ?? [];
  return getOptionLabel(opts, row.unitCode);
}

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60 },
  {
    prop: 'typeCode',
    label: '绑定类型',
    minWidth: 160,
    showOverflowTooltip: true,
    slot: 'rowBindType',
  },
  {
    prop: 'itemNo',
    label: '来源项编码',
    minWidth: 180,
    showOverflowTooltip: true,
    slot: 'rowSourceId',
  },
  {
    prop: 'devActTable',
    label: '目标表',
    minWidth: 160,
    showOverflowTooltip: true,
    slot: 'rowTargetTable',
  },
  {
    prop: 'devActId',
    label: '目标ID',
    minWidth: 120,
    slot: 'rowTargetId',
  },
  {
    prop: 'quantity',
    label: '数量',
    minWidth: 120,
    slot: 'quantity',
  },
  {
    prop: 'unitCode',
    label: '单位',
    minWidth: 140,
    slot: 'unitCode',
  },
  {
    prop: 'statusEnum',
    label: '状态',
    minWidth: 140,
    slot: 'statusEnum',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 160,
    align: 'center',
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'bindType',
    label: '绑定类型',
    options: initOptions.value.bindTypeOptions ?? [],
    multiple: false,
    clearable: true,
    width: '200px',
    style: { width: '200px' },
    checkBeforeChange: true,
    onChange: async (value) => {
      queryParams.value.bindType =
        value === null || value === undefined || String(value).trim() === ''
          ? undefined
          : String(value);
      queryParams.value.pageNo = 1;
      queryParams.value.sourceId = undefined;
      sourceIdSearchKey.value = '';
      await loadData();
      return true;
    },
  },
  {
    type: 'select',
    prop: 'sourceId',
    label: '来源项ID',
    placeholder: '请选择来源项',
    options: filteredSourceIdOptions.value,
    multiple: false,
    clearable: true,
    filterable: true,
    width: '200px',
    style: { width: '200px' },
    extraprops: {
      filterMethod: (query: string) => {
        sourceIdSearchKey.value = query ?? '';
        const q = String(query ?? '')
          .trim()
          .toLowerCase();
        if (!q) {
          return;
        }
        const exact = (currentSourceIdOptions.value ?? []).find(
          (o) =>
            String(o.label ?? '')
              .trim()
              .toLowerCase() === q,
        );
        if (exact?.value !== null && exact?.value !== undefined) {
          queryParams.value.sourceId = String(exact.value);
        }
      },
    },
    checkBeforeChange: true,
    onChange: async () => {
      queryParams.value.pageNo = 1;
      await loadData();
      return true;
    },
  },
  {
    type: 'select',
    prop: 'statusEnum',
    label: '状态',
    options: initOptions.value.statusOptions ?? [],
    multiple: false,
    clearable: true,
    checkBeforeChange: true,
    onChange: async (value) => {
      // 统一单选：仅取一个值传后端
      const v = Array.isArray(value) ? value[0] : value;
      queryParams.value.statusEnum =
        v === null || v === undefined || String(v).trim() === '' ? undefined : Number(v);
      queryParams.value.pageNo = 1;
      await loadData();
      return true;
    },
  },
]);

async function loadInit() {
  try {
    const res = await initActivityDeviceBindingApi();
    initOptions.value = res.data ?? {};
  } catch {
    initOptions.value = {};
  }
}

async function loadData() {
  // 只要选择了绑定类型就允许查询；来源项ID可空
  if (!queryParams.value.bindType) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const bindType = normalizeBindTypeToCode(queryParams.value.bindType);
    const params: PageActivityDeviceBindingParams = {
      ...queryParams.value,
      bindType,
    };
    const res = await pageActivityDeviceBindingApi(params);
    const data: PageActivityDeviceBindingResponse | undefined = res.data;
    const list = data?.content ?? [];
    const totalCount = data?.totalCount ?? data?.totalElements ?? 0;
    tableData.value = (Array.isArray(list) ? list : []).map((r) => {
      const row = r as unknown as EditableRow;
      ensureRowKey(row);
      row.__editing = false;
      row.__backup = undefined;
      // 统一下拉值类型为 string，避免进入编辑态 ElSelect 无法回显
      if (row.typeCode !== null && row.typeCode !== undefined) {
        row.typeCode = String(row.typeCode);
      }
      if (row.itemId !== null && row.itemId !== undefined) {
        row.itemId = String(row.itemId);
      }
      if (row.itemNo !== null && row.itemNo !== undefined) {
        row.itemNo = String(row.itemNo);
      }
      // 关键：后端行字段可能是 itemId，这里统一同步到 itemNo 供编辑态 v-model 使用
      if ((!row.itemNo || String(row.itemNo).trim() === '') && row.itemId) {
        row.itemNo = String(row.itemId);
      }
      if (row.devActTable !== null && row.devActTable !== undefined) {
        row.devActTable = String(row.devActTable);
      }
      if (row.devActId !== null && row.devActId !== undefined) {
        row.devActId = String(row.devActId);
      }
      if (row.unitCode !== null && row.unitCode !== undefined) {
        row.unitCode = String(row.unitCode);
      }
      rowObjMap.set(row.__rowKey as string, row);
      return row as unknown as ActivityDeviceBindingItem;
    });
    total.value = Number(totalCount);
  } catch {
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

const handleQuery = (params: Record<string, unknown>) => {
  const p = params as Record<string, unknown> & { statusEnum?: unknown };
  // 兜底：状态字段强制单选
  if (Array.isArray(p.statusEnum)) {
    p.statusEnum = p.statusEnum?.[0];
  }
  queryParams.value = {
    ...queryParams.value,
    ...p,
  } as PageActivityDeviceBindingParams;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
  };
  sourceIdSearchKey.value = '';
  tableData.value = [];
  total.value = 0;
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

function addRow() {
  const currentEditingKey = editingRowKey.value;
  if (currentEditingKey) {
    const editingRow = rowObjMap.get(currentEditingKey);
    if (editingRow?.__editing) {
      handleCancelEdit(editingRow);
    }
  }
  const row = {
    rangeCode: initOptions.value.rangeOptions?.[0]?.value ?? '',
    typeCode: queryParams.value.bindType ? String(queryParams.value.bindType) : undefined,
    itemNo: queryParams.value.sourceId ? String(queryParams.value.sourceId) : undefined,
    devActTable: undefined,
    devActId: undefined,
    quantity: 1,
    statusEnum: Number(initOptions.value.statusOptions?.[0]?.value ?? 1),
    __editing: true,
    __unitOptions: undefined,
  } as unknown as EditableRow;
  ensureRowKey(row);
  editingRowKey.value = row.__rowKey;
  rowObjMap.set(row.__rowKey as string, row);
  tableData.value.unshift(row as unknown as ActivityDeviceBindingItem);
}

async function loadUnitOptionsForRow(row: EditableRow) {
  if (!row.__editing) {
    return;
  }
  const targetTable = String(row.devActTable ?? '').trim();
  const targetIdRaw = row.devActId;
  if (
    !targetTable ||
    targetIdRaw === null ||
    targetIdRaw === undefined ||
    String(targetIdRaw).trim() === ''
  ) {
    row.__unitOptions = undefined;
    row.unitCode = undefined;
    return;
  }

  try {
    const requestSeq = ++unitOptionsRequestSeq;
    row.__unitRequestSeq = requestSeq;
    const requestTargetTable = targetTable;
    const requestTargetId = String(targetIdRaw).trim();
    const res = await unitOptionsByTargetApi({
      targetTable: requestTargetTable,
      targetId: requestTargetId,
    });
    const currentTargetTable = String(row.devActTable ?? '').trim();
    const currentTargetId = String(row.devActId ?? '').trim();
    if (
      row.__unitRequestSeq !== requestSeq ||
      !row.__editing ||
      currentTargetTable !== requestTargetTable ||
      currentTargetId !== requestTargetId
    ) {
      return;
    }
    const data: undefined | UnitOptionsByTargetResponse = res.data;
    row.__unitOptions = data?.unitOptions ?? [];
    // 回填默认 unitCode（如果当前值不在允许范围内也强制回填）
    const allowed = new Set(
      (row.__unitOptions ?? []).map((o: { label: string; value: string }) => String(o.value)),
    );
    const current = row.unitCode === null || row.unitCode === undefined ? '' : String(row.unitCode);
    const preferred = data?.unitCode ? String(data.unitCode) : '';
    if (!current || !allowed.has(current)) {
      row.unitCode = preferred || (row.__unitOptions?.[0]?.value ?? undefined);
    }
  } catch {
    row.__unitOptions = undefined;
    row.unitCode = undefined;
    ElMessage.error('获取单位信息失败，请稍后重试');
  }
}

async function handleSave(row: ActivityDeviceBindingItem) {
  const bindType =
    row.typeCode ?? (queryParams.value.bindType ? String(queryParams.value.bindType) : undefined);
  const sourceId =
    (row as EditableRow).itemNo ??
    (row as EditableRow).itemId ??
    (queryParams.value.sourceId ? String(queryParams.value.sourceId) : undefined);
  if (!bindType || !sourceId) {
    ElMessage.warning('请先填写绑定类型和来源项编码');
    return;
  }
  const bindTypeCode = normalizeBindTypeToCode(bindType);
  if (!bindTypeCode) {
    ElMessage.warning('绑定类型不合法，请重新选择');
    return;
  }
  if (!row.devActTable || !row.devActId) {
    ElMessage.warning('请填写绑定目标表与目标ID');
    return;
  }
  if (!row.quantity || row.quantity <= 0) {
    ElMessage.warning('数量必须大于0');
    return;
  }
  const payload: SaveActivityDeviceBindingParams = {
    id: row.id,
    rangeCode: row.rangeCode ?? initOptions.value.rangeOptions?.[0]?.value,
    bindType: bindTypeCode,
    sourceId,
    targetType: 0,
    targetId: row.devActId as number | string,
    targetTable: String(row.devActTable),
    quantity: row.quantity ?? 1,
    unitCode: row.unitCode,
    statusEnum: row.statusEnum ?? Number(initOptions.value.statusOptions?.[0]?.value ?? 1),
  };
  try {
    await saveActivityDeviceBindingApi(payload);
    ElMessage.success('保存成功');
    (row as EditableRow).__editing = false;
    (row as EditableRow).__backup = undefined;
    if (editingRowKey.value === (row as EditableRow).__rowKey) {
      editingRowKey.value = undefined;
    }
    loadData();
  } catch {
    ElMessage.error('保存失败，请稍后重试');
  }
}

function handleEdit(row: EditableRow) {
  ensureRowKey(row);
  const currentEditingKey = editingRowKey.value;
  if (currentEditingKey && currentEditingKey !== row.__rowKey) {
    const editingRow = rowObjMap.get(currentEditingKey);
    if (editingRow?.__editing) {
      handleCancelEdit(editingRow);
    }
  }
  // 进入编辑态前统一类型，保证 ElSelect 能正确回显当前值
  if (row.typeCode !== null && row.typeCode !== undefined) {
    row.typeCode = String(row.typeCode);
  }
  if (row.itemId !== null && row.itemId !== undefined) {
    row.itemId = String(row.itemId);
  }
  if (row.itemNo !== null && row.itemNo !== undefined) {
    row.itemNo = String(row.itemNo);
  }
  // 关键：把 itemId 同步到 itemNo，避免编辑态来源项下拉为空
  if ((!row.itemNo || String(row.itemNo).trim() === '') && row.itemId) {
    row.itemNo = String(row.itemId);
  }
  if (row.devActTable !== null && row.devActTable !== undefined) {
    row.devActTable = String(row.devActTable);
  }
  if (row.devActId !== null && row.devActId !== undefined) {
    row.devActId = String(row.devActId);
  }
  if (row.unitCode !== null && row.unitCode !== undefined) {
    row.unitCode = String(row.unitCode);
  }
  row.__backup = {
    rangeCode: row.rangeCode,
    typeCode: row.typeCode,
    itemNo: row.itemNo,
    devActTable: row.devActTable,
    devActId: row.devActId,
    quantity: row.quantity,
    unitCode: row.unitCode,
    statusEnum: row.statusEnum,
  };
  row.__editing = true;
  editingRowKey.value = row.__rowKey;
  loadUnitOptionsForRow(row);
}

function handleCancelEdit(row: EditableRow) {
  // 新增未保存行：直接移除
  if (!row.id) {
    const idx = tableData.value.indexOf(row as unknown as ActivityDeviceBindingItem);
    if (idx !== -1) {
      tableData.value.splice(idx, 1);
    }
    return;
  }
  // 已存在行：回滚到编辑前快照
  if (row.__backup) {
    Object.assign(row, row.__backup);
  }
  row.__editing = false;
  row.__backup = undefined;
  if (editingRowKey.value === row.__rowKey) {
    editingRowKey.value = undefined;
  }
}

async function handleDelete(row: ActivityDeviceBindingItem) {
  if (!row.id) {
    const idx = tableData.value.indexOf(row);
    if (idx !== -1) {
      tableData.value.splice(idx, 1);
    }
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该绑定记录吗？', '提示', {
      type: 'warning',
    });
    await deleteActivityDeviceBindingApi(String(row.id));
    ElMessage.success('删除成功');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试');
    }
  }
}

onMounted(async () => {
  await loadInit();

  const onDocClick = (e: MouseEvent) => {
    const key = editingRowKey.value;
    if (!key) {
      return;
    }
    const rowEl = rowElMap.get(key);
    const row = rowObjMap.get(key);
    if (!row || !row.__editing) {
      editingRowKey.value = undefined;
      return;
    }
    const target = e.target as Node | null;

    // ElementPlus 下拉/弹层是 teleport 到 body 的，点选会被误判为“行外点击”
    // 命中这些弹层区域时，不触发自动取消编辑
    if (target && target instanceof Element) {
      const popperEl = target.closest(
        [
          // popper/下拉/tooltip
          '.el-popper',
          '.el-select__popper',
          '.el-tooltip__popper',
          '.el-dropdown__popper',
          '.el-select-dropdown',
          // overlay/弹层容器（不同版本 class 可能不同）
          '.el-overlay',
          '.el-overlay-dialog',
          '.el-overlay-message-box',
          '.el-dialog',
          '.el-message-box',
          '.el-drawer',
          '.el-picker-panel',
          // 通用：有 data-popper-placement 基本都是浮层
          '[data-popper-placement]',
          '[role="listbox"]',
        ].join(', '),
      );
      if (popperEl) {
        return;
      }
    }

    // 点击在当前编辑行内：不处理
    if (rowEl && target && rowEl.contains(target)) {
      return;
    }
    // 点击在行外：自动取消编辑并回滚/移除
    handleCancelEdit(row);
  };

  // 捕获阶段，优先于组件内部 click 触发
  document.addEventListener('click', onDocClick, true);
  removeDocClickListener = () => document.removeEventListener('click', onDocClick, true);
});

onBeforeUnmount(() => {
  removeDocClickListener?.();
  removeDocClickListener = undefined;
});
</script>

<template>
  <Page>
    <div class="flex h-full gap-3">
      <div class="flex min-w-0 flex-1 flex-col">
        <div class="mb-2 flex flex-wrap items-center gap-3">
          <span class="font-semibold">诊疗耗材绑定</span>
        </div>

        <TableLayout
          :table-data="tableData"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :form-items="formItems"
          :table-columns="tableColumns"
          :show-side-query="false"
          :show-pagination="true"
          @query="handleQuery"
          @reset="handleReset"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <ElButton type="primary" @click="addRow">添加绑定项目</ElButton>
          </template>

          <template #rowBindType="{ row }">
            <span :ref="(el) => setRowEl(row, el)"></span>
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.typeCode"
                placeholder="请选择"
                clearable
                filterable
                class="w-full"
                @change="
                  () => {
                    clearRowSource(row);
                  }
                "
              >
                <ElOption
                  v-for="opt in initOptions.bindTypeOptions ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ getBindTypeLabelByRow(row) }}</span>
            </template>
          </template>

          <template #rowSourceId="{ row }">
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.itemNo"
                placeholder="请选择"
                clearable
                filterable
                default-first-option
                class="w-full"
              >
                <ElOption
                  v-for="opt in getSourceOptionsByBindType(row.typeCode)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ getSourceLabelByRow(row) }}</span>
            </template>
          </template>

          <template #rowTargetTable="{ row }">
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.devActTable"
                placeholder="请选择"
                clearable
                filterable
                class="w-full"
                @change="
                  () => {
                    row.devActId = undefined;
                    row.unitCode = undefined;
                    row.__unitOptions = undefined;
                  }
                "
              >
                <ElOption
                  v-for="opt in initOptions.targetTableOptions ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ getTargetTableLabelByRow(row) }}</span>
            </template>
          </template>

          <template #rowTargetId="{ row }">
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.devActId"
                placeholder="请选择"
                clearable
                filterable
                default-first-option
                class="w-full"
                :disabled="!row.devActTable"
                @change="() => loadUnitOptionsForRow(row)"
              >
                <ElOption
                  v-for="opt in getTargetIdOptionsByTargetTable(String(row.devActTable ?? ''))"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ getTargetIdLabelByRow(row) }}</span>
            </template>
          </template>

          <template #quantity="{ row }">
            <template v-if="row.__editing">
              <ElInputNumber v-model="row.quantity" :min="0" />
            </template>
            <template v-else>
              <span>{{ row.quantity ?? '' }}</span>
            </template>
          </template>

          <template #unitCode="{ row }">
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.unitCode"
                placeholder="请选择单位"
                clearable
                class="w-[120px]"
                :disabled="!row.__unitOptions?.length"
              >
                <ElOption
                  v-for="opt in row.__unitOptions ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ getUnitLabelByRow(row) }}</span>
            </template>
          </template>

          <template #statusEnum="{ row }">
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.statusEnum"
                placeholder="请选择状态"
                clearable
                class="w-[120px]"
              >
                <ElOption
                  v-for="opt in initOptions.statusOptions ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="Number(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{
                getOptionLabel(initOptions.statusOptions ?? [], String(row.statusEnum ?? ''))
              }}</span>
            </template>
          </template>

          <template #action="{ row }">
            <template v-if="row.__editing">
              <ElButton type="primary" text @click="handleSave(row)">保存</ElButton>
              <ElButton type="info" text @click="handleCancelEdit(row)">取消</ElButton>
              <ElButton type="danger" text @click="handleDelete(row)">删除</ElButton>
            </template>
            <template v-else>
              <ElButton type="primary" text @click="handleEdit(row)">编辑</ElButton>
              <ElButton type="danger" text @click="handleDelete(row)">删除</ElButton>
            </template>
          </template>
        </TableLayout>
      </div>
    </div>
  </Page>
</template>
