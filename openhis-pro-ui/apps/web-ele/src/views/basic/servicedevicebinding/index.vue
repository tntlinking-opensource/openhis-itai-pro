<script lang="ts" setup>
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  ServiceDeviceBindingInitResponse,
  ServiceDeviceBindingItem,
  PageServiceDeviceBindingParams,
  SaveServiceDeviceBindingParams,
} from '#/api/basic/service-device-binding';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  SERVICE_DEVICE_BIND_TYPE_ACTIVITY,
  SERVICE_DEVICE_BIND_TYPE_USAGE,
  SERVICE_DEVICE_STATUS_ACTIVE,
  SERVICE_DEVICE_TARGET_TABLE_ACTIVITY,
  SERVICE_DEVICE_TARGET_TABLE_DEVICE,
  deleteServiceDeviceBindingApi,
  initServiceDeviceBindingApi,
  pageServiceDeviceBindingApi,
  saveServiceDeviceBindingApi,
  serviceDeviceBindingUnitOptionsByTargetApi,
} from '#/api/basic/service-device-binding';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ServiceDeviceBindingPage' });

interface SourceOptionItem {
  label: string;
  value: string;
}

type EditableRow = {
  __backup?: Partial<ServiceDeviceBindingItem>;
  __editing?: boolean;
  __rowKey?: string;
  __unitOptions?: ApiOptionItem[];
  __unitRequestSeq?: number;
} & ServiceDeviceBindingItem;

const activeBindType = ref<string>(SERVICE_DEVICE_BIND_TYPE_USAGE);
const selectedSourceId = ref<string>('');
const sourceKeyword = ref('');

const initOptions = ref<ServiceDeviceBindingInitResponse>({});
const tableData = ref<ServiceDeviceBindingItem[]>([]);
const total = ref(0);
const loading = ref(false);

const queryParams = ref<PageServiceDeviceBindingParams>({
  pageNo: 1,
  pageSize: 50,
});

const editingRowKey = ref<string | undefined>(undefined);
const rowElMap = new Map<string, HTMLElement>();
const rowObjMap = new Map<string, EditableRow>();
let removeDocClickListener: (() => void) | undefined;
let unitOptionsRequestSeq = 0;

const { run: runInit } = useRequest(initServiceDeviceBindingApi, { manual: true });
const { run: runPage, loading: pageLoading } = useRequest(pageServiceDeviceBindingApi, {
  manual: true,
});
const { run: runSave, loading: saveLoading } = useRequest(saveServiceDeviceBindingApi, {
  manual: true,
});
const { run: runDelete, loading: deleteLoading } = useRequest(deleteServiceDeviceBindingApi, {
  manual: true,
});

watch([pageLoading, saveLoading, deleteLoading], () => {
  loading.value = !!(pageLoading.value || saveLoading.value || deleteLoading.value);
});

/** 当前 Tab 对应的来源项候选列表 */
const currentSourceOptions = computed<SourceOptionItem[]>(() => {
  let list: ApiOptionItem[] = [];
  if (activeBindType.value === SERVICE_DEVICE_BIND_TYPE_USAGE) {
    list = initOptions.value.usageSourceOptions ?? [];
  } else if (activeBindType.value === SERVICE_DEVICE_BIND_TYPE_ACTIVITY) {
    list = initOptions.value.activitySourceOptions ?? [];
  }
  return (list ?? [])
    .map((o) => ({
      label: String(o.label ?? ''),
      value: String(o.value ?? ''),
    }))
    .filter((o) => o.value.trim() !== '');
});

/** 左侧来源项关键字过滤 */
const filteredSourceOptions = computed<SourceOptionItem[]>(() => {
  const keyword = sourceKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return currentSourceOptions.value;
  }
  return currentSourceOptions.value.filter((opt) => {
    return opt.label.toLowerCase().includes(keyword) || opt.value.toLowerCase().includes(keyword);
  });
});

/** 当前 Tab 下左侧列表标题 */
const sourcePanelLabel = computed(() =>
  activeBindType.value === SERVICE_DEVICE_BIND_TYPE_USAGE ? '给药用法' : '诊疗项目',
);

/** 用法/诊疗 Tab 均可选诊疗或耗材目标 */
const targetTableOptionsForTab = computed<ApiOptionItem[]>(() => {
  return initOptions.value.targetTableOptions ?? [];
});

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center' },
  {
    prop: 'devActTableName',
    label: '绑定目标类型',
    minWidth: 140,
    showOverflowTooltip: true,
    slot: 'targetTable',
  },
  {
    prop: 'devActName',
    label: '绑定目标',
    minWidth: 220,
    showOverflowTooltip: true,
    slot: 'targetId',
  },
  {
    prop: 'quantity',
    label: '数量',
    width: 150,
    align: 'center',
    slot: 'quantity',
  },
  {
    prop: 'unitName',
    label: '单位',
    width: 130,
    align: 'center',
    slot: 'unitCode',
  },
  {
    prop: 'statusName',
    label: '状态',
    width: 120,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    width: 160,
    align: 'center',
    slot: 'action',
  },
];

/** 为行生成稳定 key，避免编辑态切换导致 DOM 复用错乱 */
function ensureRowKey(row: EditableRow) {
  if (row.__rowKey) {
    return row.__rowKey;
  }
  const key =
    row.id !== null && row.id !== undefined
      ? `id:${String(row.id)}`
      : `tmp:${Math.random().toString(36).slice(2)}`;
  row.__rowKey = key;
  return key;
}

/** 记录行 DOM，用于行外点击取消编辑 */
function setRowEl(row: EditableRow, el: unknown) {
  const key = ensureRowKey(row);
  rowObjMap.set(key, row);
  const dom = el as HTMLElement | null;
  const tr = dom?.closest?.('tr') as HTMLElement | null;
  if (tr) {
    rowElMap.set(key, tr);
  } else if (dom) {
    rowElMap.set(key, dom);
  } else {
    rowElMap.delete(key);
  }
}

/** 按目标表解析绑定目标下拉 */
function getTargetIdOptions(targetTable: string | undefined): SourceOptionItem[] {
  const table = String(targetTable ?? '').trim();
  if (table === SERVICE_DEVICE_TARGET_TABLE_ACTIVITY) {
    return (initOptions.value.activitySourceOptions ?? []).map((o) => ({
      label: String(o.label ?? ''),
      value: String(o.value ?? ''),
    }));
  }
  if (table === SERVICE_DEVICE_TARGET_TABLE_DEVICE) {
    return (initOptions.value.deviceDefOptions ?? []).map((o) => ({
      label: String(o.label ?? ''),
      value: String(o.value ?? ''),
    }));
  }
  return [];
}

/** 加载页面初始化下拉 */
async function loadInit() {
  try {
    initOptions.value = (await runInit()) ?? {};
  } catch {
    initOptions.value = {};
  }
}

/** 按当前 Tab 与选中来源项加载右侧绑定表 */
async function loadData() {
  if (!selectedSourceId.value) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  try {
    const data = await runPage({
      ...queryParams.value,
      bindType: activeBindType.value,
      sourceId: selectedSourceId.value,
      rangeCode: initOptions.value.rangeOptions?.[0]?.value,
    });
    const list = data?.content ?? [];
    total.value = Number(data?.totalCount ?? 0);
    tableData.value = list.map((item) => {
      const row = item as EditableRow;
      ensureRowKey(row);
      row.__editing = false;
      row.__backup = undefined;
      if (row.devActId !== null && row.devActId !== undefined) {
        row.devActId = String(row.devActId);
      }
      if (row.unitCode !== null && row.unitCode !== undefined) {
        row.unitCode = String(row.unitCode);
      }
      if (row.statusEnum !== null && row.statusEnum !== undefined) {
        row.statusEnum = String(row.statusEnum);
      }
      if (row.quantity !== null && row.quantity !== undefined) {
        row.quantity = Math.trunc(row.quantity);
      }
      rowObjMap.set(row.__rowKey as string, row);
      return row;
    });
  } catch {
    tableData.value = [];
    total.value = 0;
  }
}

/** 选中左侧来源项并刷新右侧绑定表 */
function handleSelectSource(sourceId: string) {
  if (selectedSourceId.value === sourceId) {
    return;
  }
  cancelEditingIfAny();
  selectedSourceId.value = sourceId;
  queryParams.value.pageNo = 1;
  loadData();
}

/** 选中首个来源项并加载右侧表（无候选项时清空） */
function selectFirstSource() {
  const first = filteredSourceOptions.value[0];
  if (first) {
    selectedSourceId.value = first.value;
    queryParams.value.pageNo = 1;
    loadData();
    return;
  }
  selectedSourceId.value = '';
  tableData.value = [];
  total.value = 0;
}

/** Tab 切换：重置筛选并自动选中首个来源项 */
function handleTabChange() {
  cancelEditingIfAny();
  sourceKeyword.value = '';
  queryParams.value.pageNo = 1;
  selectFirstSource();
}

/** 取消当前编辑行（若有） */
function cancelEditingIfAny() {
  const key = editingRowKey.value;
  if (!key) {
    return;
  }
  const row = rowObjMap.get(key);
  if (row?.__editing) {
    handleCancelEdit(row);
  }
}

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

/** 在当前来源项下新增一行绑定 */
function addRow() {
  if (!selectedSourceId.value) {
    ElMessage.warning('请先在左侧选择来源项');
    return;
  }
  cancelEditingIfAny();
  const row = {
    rangeCode: initOptions.value.rangeOptions?.[0]?.value ?? '',
    typeCode: activeBindType.value,
    itemNo: selectedSourceId.value,
    devActTable: undefined,
    devActId: undefined,
    quantity: 1,
    statusEnum: SERVICE_DEVICE_STATUS_ACTIVE,
    __editing: true,
    __unitOptions: undefined,
  } as EditableRow;
  ensureRowKey(row);
  editingRowKey.value = row.__rowKey;
  rowObjMap.set(row.__rowKey as string, row);
  tableData.value.unshift(row);
}

/** 目标变更后联动单位下拉 */
async function loadUnitOptionsForRow(row: EditableRow) {
  if (!row.__editing) {
    return;
  }
  const targetTable = String(row.devActTable ?? '').trim();
  const targetId = String(row.devActId ?? '').trim();
  if (!targetTable || !targetId) {
    row.__unitOptions = undefined;
    row.unitCode = undefined;
    return;
  }
  try {
    const requestSeq = ++unitOptionsRequestSeq;
    row.__unitRequestSeq = requestSeq;
    const data = await serviceDeviceBindingUnitOptionsByTargetApi({
      targetTable,
      targetId,
    });
    if (row.__unitRequestSeq !== requestSeq || !row.__editing) {
      return;
    }
    row.__unitOptions = data?.unitOptions ?? [];
    const allowed = new Set((row.__unitOptions ?? []).map((o) => String(o.value)));
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

/** 保存单行绑定 */
async function handleSave(row: EditableRow) {
  if (!selectedSourceId.value) {
    ElMessage.warning('请先在左侧选择来源项');
    return;
  }
  if (!row.devActTable || !row.devActId) {
    ElMessage.warning('请选择绑定目标');
    return;
  }
  if (!row.quantity || row.quantity <= 0 || !Number.isInteger(row.quantity)) {
    ElMessage.warning('数量必须为正整数');
    return;
  }
  if (!row.unitCode) {
    ElMessage.warning('请选择单位');
    return;
  }
  if (!row.statusEnum) {
    ElMessage.warning('请选择状态');
    return;
  }
  const payload: SaveServiceDeviceBindingParams = {
    id: row.id ? String(row.id) : undefined,
    rangeCode: row.rangeCode ?? initOptions.value.rangeOptions?.[0]?.value,
    bindType: activeBindType.value,
    sourceId: selectedSourceId.value,
    targetId: String(row.devActId),
    targetTable: String(row.devActTable),
    quantity: row.quantity,
    unitCode: String(row.unitCode),
    statusEnum: String(row.statusEnum),
  };
  try {
    await runSave(payload);
    ElMessage.success('保存成功');
    row.__editing = false;
    row.__backup = undefined;
    if (editingRowKey.value === row.__rowKey) {
      editingRowKey.value = undefined;
    }
    await loadData();
  } catch {
    // useRequest 已提示
  }
}

/** 进入行编辑 */
function handleEdit(row: EditableRow) {
  cancelEditingIfAny();
  if (row.devActId !== null && row.devActId !== undefined) {
    row.devActId = String(row.devActId);
  }
  if (row.unitCode !== null && row.unitCode !== undefined) {
    row.unitCode = String(row.unitCode);
  }
  if (row.statusEnum !== null && row.statusEnum !== undefined) {
    row.statusEnum = String(row.statusEnum);
  }
  row.__backup = {
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

/** 取消编辑：新行移除，已存行回滚 */
function handleCancelEdit(row: EditableRow) {
  if (!row.id) {
    const idx = tableData.value.indexOf(row);
    if (idx !== -1) {
      tableData.value.splice(idx, 1);
    }
  } else if (row.__backup) {
    Object.assign(row, row.__backup);
  }
  row.__editing = false;
  row.__backup = undefined;
  if (editingRowKey.value === row.__rowKey) {
    editingRowKey.value = undefined;
  }
}

/** 删除绑定行 */
async function handleDelete(row: EditableRow) {
  if (!row.id) {
    const idx = tableData.value.indexOf(row);
    if (idx !== -1) {
      tableData.value.splice(idx, 1);
    }
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该绑定记录吗？', '提示', { type: 'warning' });
    await runDelete(String(row.id));
    ElMessage.success('删除成功');
    await loadData();
  } catch (error) {
    if (error !== 'cancel') {
      // useRequest 已提示
    }
  }
}

/** 目标表变更时清空目标与单位 */
function handleTargetTableChange(row: EditableRow) {
  row.devActId = undefined;
  row.unitCode = undefined;
  row.__unitOptions = undefined;
}

onMounted(async () => {
  await loadInit();
  selectFirstSource();

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
    if (target && target instanceof Element) {
      const popperEl = target.closest(
        [
          '.el-popper',
          '.el-select__popper',
          '.el-select-dropdown',
          '.el-overlay',
          '.el-dialog',
          '.el-message-box',
          '[data-popper-placement]',
        ].join(', '),
      );
      if (popperEl) {
        return;
      }
    }
    if (rowEl && target && rowEl.contains(target)) {
      return;
    }
    handleCancelEdit(row);
  };

  document.addEventListener('click', onDocClick, true);
  removeDocClickListener = () => document.removeEventListener('click', onDocClick, true);
});

onBeforeUnmount(() => {
  removeDocClickListener?.();
  removeDocClickListener = undefined;
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full min-h-0 gap-3">
      <aside
        class="flex w-72 shrink-0 flex-col border-r border-[var(--el-border-color-light)] pr-2"
      >
        <ElTabs v-model="activeBindType" @tab-change="handleTabChange">
          <ElTabPane label="用法绑定" :name="SERVICE_DEVICE_BIND_TYPE_USAGE" />
          <ElTabPane label="诊疗绑定" :name="SERVICE_DEVICE_BIND_TYPE_ACTIVITY" />
        </ElTabs>

        <ElInput
          v-model="sourceKeyword"
          class="mb-2"
          clearable
          :placeholder="`搜索${sourcePanelLabel}`"
        />

        <div class="min-h-0 flex-1 overflow-y-auto">
          <div
            v-for="opt in filteredSourceOptions"
            :key="opt.value"
            class="mb-1 cursor-pointer rounded px-2 py-2 text-sm transition-colors"
            :class="
              selectedSourceId === opt.value
                ? 'bg-[var(--el-color-primary-light-9)] text-[var(--el-color-primary)]'
                : 'text-[var(--el-text-color-primary)] hover:bg-[var(--el-fill-color-light)]'
            "
            @click="handleSelectSource(opt.value)"
          >
            {{ opt.label }}
          </div>
          <div
            v-if="filteredSourceOptions.length === 0"
            class="py-8 text-center text-sm text-[var(--el-text-color-secondary)]"
          >
            暂无{{ sourcePanelLabel }}
          </div>
        </div>
      </aside>

      <div class="min-w-0 flex-1">
        <TableLayout
          v-if="selectedSourceId"
          :table-data="tableData"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :form-items="[]"
          :table-columns="tableColumns"
          :show-top-query="false"
          :show-side-query="false"
          :show-pagination="true"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <ElButton type="primary" @click="addRow">添加绑定</ElButton>
          </template>

          <template #targetTable="{ row }">
            <span :ref="(el) => setRowEl(row, el)"></span>
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.devActTable"
                placeholder="请选择"
                clearable
                filterable
                class="w-full"
                @change="() => handleTargetTableChange(row)"
              >
                <ElOption
                  v-for="opt in targetTableOptionsForTab"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ row.devActTableName || row.devActTable || '' }}</span>
            </template>
          </template>

          <template #targetId="{ row }">
            <template v-if="row.__editing">
              <ElSelect
                v-model="row.devActId"
                placeholder="请选择"
                clearable
                filterable
                class="w-full"
                :disabled="!row.devActTable"
                @change="() => loadUnitOptionsForRow(row)"
              >
                <ElOption
                  v-for="opt in getTargetIdOptions(String(row.devActTable ?? ''))"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ row.devActName || row.devActId || '' }}</span>
            </template>
          </template>

          <template #quantity="{ row }">
            <template v-if="row.__editing">
              <div class="sdb-qty-wrap">
                <ElInputNumber
                  v-model="row.quantity"
                  :min="1"
                  :step="1"
                  :precision="0"
                  controls-position="right"
                />
              </div>
            </template>
            <template v-else>
              <span>{{ row.quantity ?? '' }}</span>
            </template>
          </template>

          <template #unitCode="{ row }">
            <template v-if="row.__editing">
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
            <template v-else>
              <span>{{ row.unitName || row.unitCode || '' }}</span>
            </template>
          </template>

          <template #statusEnum="{ row }">
            <template v-if="row.__editing">
              <ElSelect v-model="row.statusEnum" placeholder="请选择" class="w-full">
                <ElOption
                  v-for="opt in initOptions.statusOptions ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </template>
            <template v-else>
              <span>{{ row.statusName || row.statusEnum || '' }}</span>
            </template>
          </template>

          <template #action="{ row }">
            <template v-if="row.__editing">
              <ElButton type="primary" link @click="handleSave(row)">保存</ElButton>
              <ElButton type="info" link @click="handleCancelEdit(row)">取消</ElButton>
            </template>
            <template v-else>
              <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
              <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
            </template>
          </template>
        </TableLayout>

        <ElEmpty v-else :description="`请在左侧选择${sourcePanelLabel}以维护绑定关系`" />
      </div>
    </div>
  </Page>
</template>

<style scoped>
.sdb-qty-wrap {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.sdb-qty-wrap :deep(.el-input-number) {
  width: 100%;
  max-width: 100%;
}

.sdb-qty-wrap :deep(.el-input-number .el-input__wrapper) {
  padding-right: 42px;
  padding-left: 8px;
}
</style>
