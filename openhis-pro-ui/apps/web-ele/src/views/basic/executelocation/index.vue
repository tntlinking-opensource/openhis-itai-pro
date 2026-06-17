<script lang="ts" setup>
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { BatchAddConfirmPayload } from './batch-add-dialog.vue';

import type {
  CreateExecuteLocationParams,
  ExecuteLocationItem,
  PageActivityDefinitionParams,
  PageExecuteLocationParams,
  UpdateExecuteLocationParams,
} from '#/api/basic/execute-location';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElMessage } from 'element-plus';

import {
  createExecuteLocationApi,
  deleteExecuteLocationApi,
  initExecuteLocationApi,
  pageActivityDefinitionForExecuteLocationApi,
  pageExecuteLocationApi,
  updateExecuteLocationApi,
} from '#/api/basic/execute-location';
import { useRequest } from '#/hooks/use-request';

import BatchAddDialog from './batch-add-dialog.vue';

defineOptions({ name: 'BasicExecuteLocation' });

interface OrgTreeNode {
  id: string;
  name: string;
  children?: OrgTreeNode[];
}

interface ExecuteLocationOrgOption extends ApiOptionItem {
  children?: ExecuteLocationOrgOption[];
}

/** 表格行：未落库行用 _clientKey 作为稳定 row-key */
interface ExecuteLocationTableRow extends ExecuteLocationItem {
  _clientKey: string;
}

const orgTree = ref<OrgTreeNode[]>([]);
const currentOrgId = ref<string>('');
const treeLoading = ref(false);

const list = ref<ExecuteLocationTableRow[]>([]);
const loading = ref(false);
const total = ref(0);

const queryParams = reactive<PageExecuteLocationParams>({
  executionOrgId: '',
  pageNo: 1,
  pageSize: 20,
});

const activityCategoryOptions = ref<ApiOptionItem[]>([]);
const rowActivityDefinitionOptions = ref<Record<string, ApiOptionItem[]>>({});
const batchDialogVisible = ref(false);
let clientKeySeed = 0;
let listRequestSeq = 0;

/** Long/主键字段转 string，空值返回 undefined */
function toOptionalIdString(value: null | number | string | undefined): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return String(value);
}

/** 项目 ID 转 string，空值返回空串 */
function toActivityDefinitionIdString(value: null | number | string | undefined): string {
  if (value === undefined || value === null) {
    return '';
  }
  return String(value);
}

/** 生成未落库行的本地主键 */
function nextClientKey() {
  return `draft:${++clientKeySeed}`;
}

/** 规范化项目下拉 value/label，避免 ElSelect 因类型不一致无法回显名称 */
function normalizeActivityDefinitionOptions(options: ApiOptionItem[]): ApiOptionItem[] {
  return options.map((option) => ({
    label: String(option.label ?? option.value ?? ''),
    value: String(option.value ?? ''),
  }));
}

function setRowActivityDefinitionOptions(row: ExecuteLocationTableRow, options: ApiOptionItem[]) {
  rowActivityDefinitionOptions.value = {
    ...rowActivityDefinitionOptions.value,
    [row._clientKey]: normalizeActivityDefinitionOptions(options),
  };
}

function clearRowActivityDefinitionOptions(row: ExecuteLocationTableRow) {
  const { [row._clientKey]: _removed, ...rest } = rowActivityDefinitionOptions.value;
  rowActivityDefinitionOptions.value = rest;
}

function getActivityDefinitionOptionsForRow(row: ExecuteLocationTableRow) {
  return rowActivityDefinitionOptions.value[row._clientKey] || [];
}

/** 用列表行自带的 activityDefinitionName 预填项目下拉 */
function hydrateRowActivityDefinitionOption(row: ExecuteLocationTableRow) {
  const defId = toActivityDefinitionIdString(row.activityDefinitionId);
  if (!defId) {
    return;
  }
  const label = row.activityDefinitionName?.trim() || defId;
  setRowActivityDefinitionOptions(row, [{ value: defId, label }]);
}

/** 将后端 LocalTime / 字符串统一为 HH:mm:ss */
function formatExecuteLocationTime(value: unknown): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  if (typeof value === 'string') {
    return value;
  }
  if (Array.isArray(value) && value.length >= 2) {
    const [hour, minute, second = 0] = value;
    return [hour, minute, second].map((part) => String(part).padStart(2, '0')).join(':');
  }
  if (typeof value === 'object') {
    const parts = value as { hour?: number; minute?: number; second?: number };
    if (typeof parts.hour === 'number' && typeof parts.minute === 'number') {
      const second = typeof parts.second === 'number' ? parts.second : 0;
      return [parts.hour, parts.minute, second]
        .map((part) => String(part).padStart(2, '0'))
        .join(':');
    }
  }
  return String(value);
}

/** 规范化列表行中的 Long / 时间 / 名称字段 */
function normalizeExecuteLocationRow(item: ExecuteLocationItem): ExecuteLocationItem {
  const activityDefinitionName =
    item.activityDefinitionName?.trim() ||
    (
      item as ExecuteLocationItem & { activityDefinitionId_dictText?: string }
    ).activityDefinitionId_dictText?.trim();

  return {
    ...item,
    id: toOptionalIdString(item.id),
    activityDefinitionId: toOptionalIdString(item.activityDefinitionId),
    activityDefinitionName: activityDefinitionName || undefined,
    executionOrgId: toOptionalIdString(item.executionOrgId),
    executionLocationId: toOptionalIdString(item.executionLocationId),
    startTime: formatExecuteLocationTime(item.startTime),
    endTime: formatExecuteLocationTime(item.endTime),
  };
}

/** 接口行转为表格行（含稳定 row-key） */
function toTableRow(item: ExecuteLocationItem): ExecuteLocationTableRow {
  const normalized = normalizeExecuteLocationRow(item);
  return {
    ...normalized,
    _clientKey: normalized.id ? `persisted:${normalized.id}` : nextClientKey(),
  };
}

function parseTimeToSeconds(time: string | undefined): number | undefined {
  if (!time) {
    return undefined;
  }
  const match = /^(\d{2}):(\d{2}):(\d{2})$/.exec(time.trim());
  if (!match) {
    return undefined;
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  const second = Number(match[3]);
  if (hour > 23 || minute > 59 || second > 59) {
    return undefined;
  }
  return hour * 3600 + minute * 60 + second;
}

const { run: runPage, loading: pageLoading } = useRequest(pageExecuteLocationApi, {
  manual: true,
});
const { run: runCreate, loading: createLoading } = useRequest(createExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('保存成功');
    getList();
  },
});
const { run: runUpdate, loading: updateLoading } = useRequest(updateExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('保存成功');
    getList();
  },
});
const { run: runDelete, loading: deleteLoading } = useRequest(deleteExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

watch(
  [pageLoading, createLoading, updateLoading, deleteLoading],
  () => {
    loading.value =
      !!pageLoading.value ||
      !!createLoading.value ||
      !!updateLoading.value ||
      !!deleteLoading.value;
  },
  { immediate: true },
);

/** 当前科室已配置的项目 ID，供批量添加去重 */
const boundActivityDefinitionIds = computed(() =>
  list.value.map((row) => String(row.activityDefinitionId ?? '')).filter((id) => id.length > 0),
);

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: '6%',
    align: 'center',
  },
  {
    prop: 'activityCategoryCode',
    label: '诊疗大类',
    width: '20%',
    align: 'center',
    slot: 'activityCategory',
  },
  {
    prop: 'activityDefinitionId',
    label: '项目名称',
    width: '18%',
    align: 'center',
    slot: 'activityDefinition',
  },
  {
    prop: 'timeRange',
    label: '生效时段',
    width: '40%',
    align: 'center',
    slot: 'timeRange',
  },
  {
    label: '操作',
    width: '16%',
    align: 'center',
    slot: 'actions',
  },
];

async function initPage() {
  treeLoading.value = true;
  try {
    const initData = await initExecuteLocationApi();

    // 使用执行科室选项构造左侧树结构
    orgTree.value =
      ((initData.executionOrgOptions || []) as ExecuteLocationOrgOption[]).map((item) => ({
        id: String(item.value),
        name: item.label,
        children:
          item.children?.map((child) => ({
            id: String(child.value),
            name: child.label,
            children:
              child.children?.map((grand) => ({
                id: String(grand.value),
                name: grand.label,
                children: [],
              })) ?? [],
          })) ?? [],
      })) || [];

    activityCategoryOptions.value = initData.activityCategoryOptions || [];
  } finally {
    treeLoading.value = false;
  }
}

async function loadActivityDefinitionsForRow(row: ExecuteLocationTableRow) {
  if (!currentOrgId.value || !row.activityCategoryCode) {
    clearRowActivityDefinitionOptions(row);
    return;
  }
  const params: PageActivityDefinitionParams = {
    executionOrgId: currentOrgId.value,
    activityCategoryCode: row.activityCategoryCode,
    pageNo: 1,
    pageSize: 100,
  };
  const resp = await pageActivityDefinitionForExecuteLocationApi(params);
  const options = normalizeActivityDefinitionOptions(resp.content || []);
  const defId = toActivityDefinitionIdString(row.activityDefinitionId);
  // 当前选中项若不在分页结果中，用列表接口返回的名称回显
  if (defId && !options.some((item) => item.value === defId)) {
    options.unshift({
      value: defId,
      label: row.activityDefinitionName?.trim() || defId,
    });
  }
  setRowActivityDefinitionOptions(row, options);
}

function onActivityCategoryChange(row: ExecuteLocationTableRow) {
  row.activityDefinitionId = undefined;
  clearRowActivityDefinitionOptions(row);
}

/** 加载当前科室执行规则列表 */
async function getList() {
  const requestSeq = ++listRequestSeq;
  if (!currentOrgId.value) {
    list.value = [];
    total.value = 0;
    return;
  }
  queryParams.executionOrgId = currentOrgId.value;
  const params: PageExecuteLocationParams = {
    executionOrgId: queryParams.executionOrgId,
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  };

  try {
    const resp = await runPage(params);
    if (requestSeq !== listRequestSeq) {
      return;
    }
    list.value = (resp.content || []).map((item) => toTableRow(item));
    total.value = resp.totalCount || 0;
    rowActivityDefinitionOptions.value = {};
    for (const row of list.value) {
      hydrateRowActivityDefinitionOption(row);
    }
  } catch {
    if (requestSeq !== listRequestSeq) {
      return;
    }
    list.value = [];
    total.value = 0;
    ElMessage.error('加载执行规则失败，请稍后重试');
  }
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

function isOrgTreeLeafNode(data: OrgTreeNode, treeNode: { isLeaf?: boolean }): boolean {
  if (treeNode.isLeaf === false) {
    return false;
  }
  const children = data.children;
  return !children || children.length === 0;
}

function handleOrgNodeClick(data: OrgTreeNode, treeNode: { isLeaf?: boolean }) {
  if (!isOrgTreeLeafNode(data, treeNode)) {
    return;
  }
  currentOrgId.value = data.id;
  queryParams.pageNo = 1;
  getList();
}

function validateRow(row: ExecuteLocationTableRow): boolean {
  if (!row.activityCategoryCode) {
    ElMessage.error('诊疗大类不能为空');
    return false;
  }
  if (!row.startTime || !row.endTime) {
    ElMessage.error('开始/结束时间不能为空');
    return false;
  }
  const startSeconds = parseTimeToSeconds(row.startTime);
  const endSeconds = parseTimeToSeconds(row.endTime);
  if (startSeconds === undefined || endSeconds === undefined) {
    ElMessage.error('开始/结束时间格式不正确');
    return false;
  }
  if (startSeconds > endSeconds) {
    ElMessage.error('开始时间不能大于结束时间');
    return false;
  }
  return true;
}

function handleAddRow() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择一个具体科室');
    return;
  }
  list.value.unshift({
    _clientKey: nextClientKey(),
    activityCategoryCode: undefined,
    activityDefinitionId: undefined,
    executionOrgId: currentOrgId.value,
    startTime: '00:00:00',
    endTime: '23:59:59',
    priority: 1,
  });
}

async function handleSaveRow(row: ExecuteLocationTableRow) {
  if (!currentOrgId.value) {
    ElMessage.warning('请先选择执行科室');
    return;
  }
  row.executionOrgId = currentOrgId.value;

  if (!validateRow(row)) {
    return;
  }

  // 只有当 id 是纯数字（后端生成的 Long）时，才认为是已存在记录需要走更新
  const normalizedId = isPersistedRowId(row.id) ? String(row.id) : undefined;

  const activityCategoryCode = String(row.activityCategoryCode ?? '');
  const executionOrgId = String(row.executionOrgId ?? '');
  const startTime = String(row.startTime ?? '');
  const endTime = String(row.endTime ?? '');

  const payload: CreateExecuteLocationParams | UpdateExecuteLocationParams = {
    ...(normalizedId ? { id: normalizedId } : {}),
    activityDefinitionId: row.activityDefinitionId,
    activityCategoryCode,
    executionOrgId,
    executionLocationId: row.executionLocationId,
    startTime,
    endTime,
    priority: 1,
    displayOrder: row.displayOrder,
  };

  await (normalizedId
    ? runUpdate(payload as UpdateExecuteLocationParams)
    : runCreate(payload as CreateExecuteLocationParams));
}

function isPersistedRowId(id: ExecuteLocationItem['id']): id is string {
  return id !== undefined && id !== null && /^\d+$/.test(String(id));
}

async function handleDeleteRow(row: ExecuteLocationTableRow, index: number) {
  const persistedId = isPersistedRowId(row.id) ? String(row.id) : undefined;

  if (persistedId) {
    await runDelete(persistedId);
    return;
  }

  list.value.splice(index, 1);
  clearRowActivityDefinitionOptions(row);
}

/** 打开批量添加弹窗 */
function openBatchDialog() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先在左侧选择一个具体科室');
    return;
  }
  batchDialogVisible.value = true;
}

/** 批量添加确认：写入表格草稿行，由用户逐条保存 */
function handleBatchConfirm(payload: BatchAddConfirmPayload) {
  const startSeconds = parseTimeToSeconds(payload.startTime);
  const endSeconds = parseTimeToSeconds(payload.endTime);
  if (startSeconds === undefined || endSeconds === undefined) {
    ElMessage.error('生效时段格式不正确');
    return;
  }
  if (startSeconds > endSeconds) {
    ElMessage.error('开始时间不能大于结束时间');
    return;
  }

  for (const item of payload.items) {
    const option = normalizeActivityDefinitionOptions([item])[0];
    if (!option) {
      continue;
    }
    const row: ExecuteLocationTableRow = {
      _clientKey: nextClientKey(),
      activityCategoryCode: payload.activityCategoryCode,
      activityDefinitionId: option.value,
      activityDefinitionName: option.label,
      executionOrgId: currentOrgId.value,
      startTime: payload.startTime,
      endTime: payload.endTime,
      priority: 1,
    };
    list.value.unshift(row);
    setRowActivityDefinitionOptions(row, [option]);
  }
  ElMessage.success(`已添加 ${payload.items.length} 条项目配置，请核对后逐条保存`);
}

onMounted(async () => {
  await initPage();
});

onActivated(() => {
  if (currentOrgId.value) {
    getList();
  }
});
</script>

<template>
  <Page>
    <div class="flex h-full gap-3">
      <div
        class="flex w-64 min-w-[220px] flex-col border-r border-[var(--el-border-color-light)] pr-2"
      >
        <div class="mb-2 font-semibold">执行科室</div>
        <div class="min-h-0 flex-1 overflow-auto" v-loading="treeLoading">
          <ElTree
            :data="orgTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            @node-click="handleOrgNodeClick"
          />
        </div>
      </div>

      <div class="execute-location-panel min-w-0 flex-1">
        <TableLayout
          class="execute-location-table"
          :table-data="list"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :table-columns="tableColumns"
          :show-top-query="false"
          :show-pagination="true"
          row-key="_clientKey"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <ElButton type="primary" @click="handleAddRow" :disabled="!currentOrgId">
              添加新项目
            </ElButton>
            <ElButton @click="openBatchDialog" :disabled="!currentOrgId"> 批量添加项目 </ElButton>
          </template>

          <template #activityCategory="{ row }">
            <ElSelect
              v-model="row.activityCategoryCode"
              placeholder="请选择诊疗大类"
              clearable
              class="cell-control"
              @change="() => onActivityCategoryChange(row)"
            >
              <ElOption
                v-for="item in activityCategoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #activityDefinition="{ row }">
            <ElSelect
              v-model="row.activityDefinitionId"
              filterable
              clearable
              placeholder="可选，不选表示大类默认"
              class="cell-control"
              @visible-change="(visible: boolean) => visible && loadActivityDefinitionsForRow(row)"
            >
              <ElOption
                v-for="item in getActivityDefinitionOptionsForRow(row)"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>

          <template #timeRange="{ row }">
            <div class="execute-time-row">
              <ElTimePicker
                v-model="row.startTime"
                placeholder="开始"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="execute-time-input"
              />
              <span class="execute-time-sep">~</span>
              <ElTimePicker
                v-model="row.endTime"
                placeholder="结束"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="execute-time-input"
              />
            </div>
          </template>

          <template #actions="{ row, rowIndex }">
            <ElButton link type="primary" @click="handleSaveRow(row)">保存</ElButton>
            <ElButton link type="danger" @click="handleDeleteRow(row, rowIndex)">删除</ElButton>
          </template>
        </TableLayout>
      </div>
    </div>

    <BatchAddDialog
      v-model:visible="batchDialogVisible"
      :execution-org-id="currentOrgId"
      :activity-category-options="activityCategoryOptions"
      :bound-activity-definition-ids="boundActivityDefinitionIds"
      @confirm="handleBatchConfirm"
    />
  </Page>
</template>

<style scoped lang="scss">
.execute-location-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.execute-location-table {
  flex: 1;
  min-width: 0;
  width: 100%;

  :deep(.vxe-grid),
  :deep(.vxe-table),
  :deep(.vxe-table--render-wrapper),
  :deep(.vxe-table--main-wrapper),
  :deep(.vxe-table--header),
  :deep(.vxe-table--body),
  :deep(.vxe-table--header-wrapper table),
  :deep(.vxe-table--body-wrapper table) {
    width: 100% !important;
  }

  :deep(.cell-control) {
    width: 100%;
  }

  :deep(.vxe-body--column),
  :deep(.vxe-header--column) {
    overflow: visible;
  }
}

.execute-time-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding-inline: 8px;
  box-sizing: border-box;
}

.execute-time-input {
  flex: 1;
  min-width: 0;
  max-width: 168px;
}

.execute-time-sep {
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}
</style>
