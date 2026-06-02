<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  BatchCreateExecuteLocationParams,
  CreateExecuteLocationParams,
  PageActivityDefinitionParams,
  PageExecuteLocationParams,
  UpdateExecuteLocationParams,
  ExecuteLocationItem,
} from '#/api/basic/execute-location';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  batchCreateExecuteLocationApi,
  createExecuteLocationApi,
  deleteExecuteLocationApi,
  initExecuteLocationApi,
  pageActivityDefinitionForExecuteLocationApi,
  pageExecuteLocationApi,
  updateExecuteLocationApi,
} from '#/api/basic/execute-location';
import { useRequest } from '#/hooks/use-request';

interface OrgTreeNode {
  id: string;
  name: string;
  children?: OrgTreeNode[];
}

interface TableLayoutFormModel {
  activityCategoryCode?: string;
  [key: string]: unknown;
}

const orgTree = ref<OrgTreeNode[]>([]);
const currentOrgId = ref<string>('');
const treeLoading = ref(false);

const list = ref<ExecuteLocationItem[]>([]);
const loading = ref(false);
const total = ref(0);

const queryParams = reactive<PageExecuteLocationParams>({
  executionOrgId: '',
  pageNo: 1,
  pageSize: 20,
});

const activityCategoryOptions = ref<ApiOptionItem[]>([]);
const rowActivityDefinitionOptions = ref<Record<string, ApiOptionItem[]>>({});
const batchActivityDefinitionOptions = ref<ApiOptionItem[]>([]);
const rowOptionKeyMap = new WeakMap<ExecuteLocationItem, string>();
let rowOptionKeySeed = 0;
let listRequestSeq = 0;

function getRowOptionKey(row: ExecuteLocationItem) {
  const cached = rowOptionKeyMap.get(row);
  if (cached) {
    return cached;
  }
  const key = `row:${++rowOptionKeySeed}`;
  rowOptionKeyMap.set(row, key);
  return key;
}

function setRowActivityDefinitionOptions(row: ExecuteLocationItem, options: ApiOptionItem[]) {
  const key = getRowOptionKey(row);
  rowActivityDefinitionOptions.value = {
    ...rowActivityDefinitionOptions.value,
    [key]: options,
  };
}

function clearRowActivityDefinitionOptions(row: ExecuteLocationItem) {
  const key = getRowOptionKey(row);
  const { [key]: _removed, ...rest } = rowActivityDefinitionOptions.value;
  rowActivityDefinitionOptions.value = rest;
}

function getActivityDefinitionOptionsForRow(row: ExecuteLocationItem) {
  const key = getRowOptionKey(row);
  return rowActivityDefinitionOptions.value[key] || [];
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
const { run: runBatchCreate, loading: batchLoading } = useRequest(batchCreateExecuteLocationApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('批量新增成功');
    batchDialogVisible.value = false;
    getList();
  },
});

watch(
  [pageLoading, createLoading, updateLoading, deleteLoading, batchLoading],
  () => {
    loading.value =
      !!pageLoading.value ||
      !!createLoading.value ||
      !!updateLoading.value ||
      !!deleteLoading.value ||
      !!batchLoading.value;
  },
  { immediate: true },
);

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '诊疗大类',
    prop: 'activityCategoryCode',
    type: 'select',
    options: activityCategoryOptions.value,
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'selection', width: 50, align: 'center' },
  {
    type: 'index',
    label: '序号',
    width: 60,
    align: 'center',
  },
  {
    prop: 'activityCategoryCode',
    label: '诊疗大类',
    width: 180,
    align: 'center',
    slot: 'activityCategory',
  },
  {
    prop: 'activityDefinitionId',
    label: '项目名称',
    minWidth: 260,
    align: 'center',
    slot: 'activityDefinition',
  },
  {
    prop: 'startTime',
    label: '开始时间',
    width: 140,
    align: 'center',
    slot: 'startTime',
  },
  {
    prop: 'endTime',
    label: '结束时间',
    width: 140,
    align: 'center',
    slot: 'endTime',
  },
  {
    prop: 'priority',
    label: '优先级',
    width: 100,
    align: 'center',
  },
  {
    prop: 'remark',
    label: '备注',
    minWidth: 180,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    label: '操作',
    minWidth: 160,
    fixed: 'right',
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
      (initData.executionOrgOptions || []).map((item) => ({
        id: String(item.value),
        name: item.label,
        children:
          item.children?.map((child: ApiOptionItem) => ({
            id: String(child.value),
            name: child.label,
            children:
              (child as ApiOptionItem & { children?: ApiOptionItem[] }).children?.map(
                (grand: ApiOptionItem) => ({
                  id: String(grand.value),
                  name: grand.label,
                  children: [],
                }),
              ) ?? [],
          })) ?? [],
      })) || [];

    activityCategoryOptions.value = initData.activityCategoryOptions || [];
  } finally {
    treeLoading.value = false;
  }
}

async function loadActivityDefinitionsForRow(row: ExecuteLocationItem) {
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
  setRowActivityDefinitionOptions(row, resp.content || []);
}

function onActivityCategoryChange(row: ExecuteLocationItem) {
  row.activityDefinitionId = undefined;
  clearRowActivityDefinitionOptions(row);
}

async function getList(formModel?: TableLayoutFormModel) {
  const requestSeq = ++listRequestSeq;
  if (!currentOrgId.value) {
    list.value = [];
    total.value = 0;
    return;
  }
  queryParams.executionOrgId = currentOrgId.value;
  // TableLayout 顶部查询条件：诊疗大类
  if (formModel && Object.prototype.hasOwnProperty.call(formModel, 'activityCategoryCode')) {
    queryParams.activityCategoryCode =
      (formModel.activityCategoryCode as string | undefined) || undefined;
  }
  const params: PageExecuteLocationParams = {
    executionOrgId: queryParams.executionOrgId,
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    activityCategoryCode: queryParams.activityCategoryCode,
  };

  try {
    const resp = await runPage(params);
    if (requestSeq !== listRequestSeq) {
      return;
    }
    list.value = (resp.content || []) as ExecuteLocationItem[];
    total.value = resp.totalCount || 0;
  } catch {
    if (requestSeq !== listRequestSeq) {
      return;
    }
    list.value = [];
    total.value = 0;
    ElMessage.error('加载执行规则失败，请稍后重试');
  }
}

function handleReset() {
  getList();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

function handleOrgNodeClick(node: OrgTreeNode, meta: { children?: OrgTreeNode[] }) {
  if (meta?.children && meta.children?.length > 0) {
    return;
  }
  currentOrgId.value = node.id;
  queryParams.pageNo = 1;
  getList();
}

function validateRow(row: ExecuteLocationItem): boolean {
  if (!row.activityCategoryCode) {
    ElMessage.error('诊疗大类不能为空');
    return false;
  }
  if (!row.activityDefinitionId) {
    ElMessage.error('项目名称不能为空');
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
    activityCategoryCode: undefined,
    activityDefinitionId: undefined,
    executionOrgId: currentOrgId.value,
    startTime: '00:00:00',
    endTime: '23:59:59',
    priority: 1,
  });
}

async function handleSaveRow(row: ExecuteLocationItem) {
  if (!currentOrgId.value) {
    ElMessage.warning('请先选择执行科室');
    return;
  }
  row.executionOrgId = currentOrgId.value;

  if (!validateRow(row)) {
    return;
  }

  // 只有当 id 是纯数字（后端生成的 Long）时，才认为是已存在记录需要走更新
  const normalizedId = row.id && /^\d+$/.test(String(row.id)) ? String(row.id) : undefined;

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
    priority: row.priority ?? 1,
    displayOrder: row.displayOrder,
    remark: row.remark,
  };

  await (normalizedId
    ? runUpdate(payload as UpdateExecuteLocationParams)
    : runCreate(payload as CreateExecuteLocationParams));
}

async function handleDeleteRow(row: ExecuteLocationItem, index: number) {
  try {
    await ElMessageBox.confirm('确认删除该执行规则吗？', '提示', {
      type: 'warning',
    });
    if (row.id) {
      await runDelete(row.id);
    } else {
      list.value.splice(index, 1);
      clearRowActivityDefinitionOptions(row);
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试');
    }
  }
}

const batchDialogVisible = ref(false);
const batchSelectedDefinitions = ref<string[]>([]);
const batchForm = reactive<{
  activityCategoryCode?: string;
  endTime: string;
  startTime: string;
}>({
  activityCategoryCode: undefined,
  startTime: '00:00:00',
  endTime: '23:59:59',
});

async function loadBatchActivityDefinitions() {
  if (!currentOrgId.value || !batchForm.activityCategoryCode) {
    batchActivityDefinitionOptions.value = [];
    return;
  }
  const params: PageActivityDefinitionParams = {
    executionOrgId: currentOrgId.value,
    activityCategoryCode: batchForm.activityCategoryCode,
    pageNo: 1,
    pageSize: 200,
  };
  const resp = await pageActivityDefinitionForExecuteLocationApi(params);
  batchActivityDefinitionOptions.value = resp.content || [];
}

function openBatchDialog() {
  if (!currentOrgId.value) {
    ElMessage.warning('请先选择执行科室');
    return;
  }
  batchSelectedDefinitions.value = [];
  batchForm.activityCategoryCode = undefined;
  batchForm.startTime = '00:00:00';
  batchForm.endTime = '23:59:59';
  batchActivityDefinitionOptions.value = [];
  batchDialogVisible.value = true;
}

async function handleBatchSubmit() {
  if (!batchForm.activityCategoryCode) {
    ElMessage.error('诊疗大类不能为空');
    return;
  }
  if (batchSelectedDefinitions.value.length === 0) {
    ElMessage.error('请至少选择一个诊疗项目');
    return;
  }
  const startSeconds = parseTimeToSeconds(batchForm.startTime);
  const endSeconds = parseTimeToSeconds(batchForm.endTime);
  if (startSeconds === undefined || endSeconds === undefined) {
    ElMessage.error('时间格式不正确');
    return;
  }
  if (startSeconds > endSeconds) {
    ElMessage.error('开始时间不能大于结束时间');
    return;
  }

  const params: BatchCreateExecuteLocationParams = {
    executionOrgId: currentOrgId.value,
    activityCategoryCode: batchForm.activityCategoryCode,
    activityDefinitionIds: batchSelectedDefinitions.value,
    startTime: batchForm.startTime,
    endTime: batchForm.endTime,
    priority: 1,
  };

  await runBatchCreate(params);
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
            @node-click="handleOrgNodeClick"
          />
        </div>
      </div>

      <div class="min-w-0 flex-1">
        <TableLayout
          :table-data="list"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :form-items="formItems"
          :table-columns="tableColumns"
          :show-top-query="true"
          :show-pagination="true"
          row-key="id"
          @query="getList"
          @reset="handleReset"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <ElButton type="primary" @click="handleAddRow" :disabled="!currentOrgId">
              添加新项目
            </ElButton>
            <ElButton type="primary" plain @click="openBatchDialog" :disabled="!currentOrgId">
              批量新增项目配置
            </ElButton>
          </template>

          <template #activityCategory="{ row }">
            <ElSelect
              v-model="row.activityCategoryCode"
              placeholder="请选择诊疗大类"
              clearable
              style="width: 160px"
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
              placeholder="请选择项目"
              style="width: 260px"
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

          <template #startTime="{ row }">
            <ElTimePicker
              v-model="row.startTime"
              placeholder="开始时间"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
            />
          </template>
          <template #endTime="{ row }">
            <ElTimePicker
              v-model="row.endTime"
              placeholder="结束时间"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
            />
          </template>

          <template #actions="{ row, rowIndex }">
            <ElButton link type="primary" @click="handleSaveRow(row)">保存</ElButton>
            <ElButton link type="danger" @click="handleDeleteRow(row, rowIndex)">删除</ElButton>
          </template>
        </TableLayout>
      </div>
    </div>

    <ElDialog
      v-model="batchDialogVisible"
      title="批量新增执行科室配置"
      width="600px"
      destroy-on-close
    >
      <ElForm label-width="90px">
        <ElFormItem label="诊疗大类">
          <ElSelect
            v-model="batchForm.activityCategoryCode"
            placeholder="请选择诊疗大类"
            style="width: 100%"
            @change="
              () => {
                batchSelectedDefinitions = [];
                loadBatchActivityDefinitions();
              }
            "
          >
            <ElOption
              v-for="item in activityCategoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="项目列表">
          <ElSelect
            v-model="batchSelectedDefinitions"
            multiple
            filterable
            style="width: 100%"
            placeholder="请选择诊疗项目"
          >
            <ElOption
              v-for="item in batchActivityDefinitionOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="时间段">
          <ElTimePicker
            v-model="batchForm.startTime"
            format="HH:mm:ss"
            value-format="HH:mm:ss"
            placeholder="开始时间"
          />
          <span style="margin: 0 8px">~</span>
          <ElTimePicker
            v-model="batchForm.endTime"
            format="HH:mm:ss"
            value-format="HH:mm:ss"
            placeholder="结束时间"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="batchDialogVisible = false">取 消</ElButton>
          <ElButton type="primary" @click="handleBatchSubmit">确 定</ElButton>
        </span>
      </template>
    </ElDialog>
  </Page>
</template>
