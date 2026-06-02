<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { OrganizationOptionDto, QueueRow } from '#/api/emg/triage';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { getOrganizationListApi, getQueuePageApi } from '#/api/emg/triage';

defineOptions({
  name: 'QueuePanel',
});

const props = defineProps<{
  refreshToken?: number;
}>();

const orgOptions = ref<OrganizationOptionDto[]>([]);
const queryParams = ref({
  pageNo: 1,
  pageSize: 20,
  triageLevelEnum: null as null | number,
  statusEnum: null as null | number,
  organizationId: null as null | number,
});

const loading = ref(false);
const tableData = ref<QueueRow[]>([]);
const total = ref(0);

const selectedRows = ref<QueueRow[]>([]);

const detailVisible = ref(false);
const detailRow = ref<QueueRow | null>(null);

const actionVisible = ref(false);
const actionMode = ref<'modify' | 'reassign'>('modify');
const actionRow = ref<null | QueueRow>(null);
const actionReason = ref('');
const actionLevel = ref<null | number>(null);
const actionOrgId = ref<null | number>(null);

let pollTimer: null | ReturnType<typeof setInterval> = null;

// 根据分诊级别返回对应标签类型
function levelType(
  level: null | number | undefined,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  if (level === 1) {
    return 'danger';
  }
  if (level === 2) {
    return 'warning';
  }
  if (level === 3) {
    return 'warning';
  }
  if (level === 4) {
    return 'success';
  }
  return 'info';
}

// 判断患者等待是否超时
function isWaitTimeout(row: QueueRow): boolean {
  const w = row.waitMinutes ?? 0;
  const lv = row.triageLevelEnum ?? 4;
  if (row.statusEnum !== 1) {
    return false;
  }
  if (lv === 1) {
    return w > 10;
  }
  if (lv === 2) {
    return w > 20;
  }
  if (lv === 3) {
    return w > 30;
  }
  return w > 45;
}

const statsText = computed(() => {
  const waiting = tableData.value.filter((r) => r.statusEnum === 1);
  const c1 = waiting.filter((r) => r.triageLevelEnum === 1).length;
  const c2 = waiting.filter((r) => r.triageLevelEnum === 2).length;
  const c3 = waiting.filter((r) => r.triageLevelEnum === 3).length;
  const c4 = waiting.filter((r) => r.triageLevelEnum === 4).length;
  const timeout = waiting.filter((r) => isWaitTimeout(r)).length;
  const avg =
    waiting.length === 0
      ? 0
      : Math.round(waiting.reduce((s, r) => s + (r.waitMinutes ?? 0), 0) / waiting.length);
  return `待就诊 ${waiting.length} 人 · 一级 ${c1} / 二级 ${c2} / 三级 ${c3} / 四级 ${c4} · 平均等待 ${avg} 分钟 · 超时预警 ${timeout} 人`;
});

const levelOptions = [
  { label: '一级（危急）', value: 1 },
  { label: '二级（急重）', value: 2 },
  { label: '三级（急症）', value: 3 },
  { label: '四级（亚急症）', value: 4 },
];

const statusOptions = [
  { label: '待就诊', value: 1 },
  { label: '就诊中', value: 2 },
  { label: '已就诊', value: 3 },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'triageLevelEnum',
    label: '分诊级别',
    placeholder: '请选择分诊级别',
    clearable: true,
    multiple: false,
    options: levelOptions,
  },
  {
    type: 'select',
    prop: 'statusEnum',
    label: '就诊状态',
    placeholder: '请选择就诊状态',
    clearable: true,
    multiple: false,
    options: statusOptions,
  },
  {
    type: 'select',
    prop: 'organizationId',
    label: '科室',
    placeholder: '请选择科室',
    clearable: true,
    multiple: false,
    options: orgOptions.value.map((o) => ({ label: o.name ?? '', value: o.id })),
  },
]);

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    type: 'index',
    label: '序号',
  },
  {
    prop: 'patientName',
    label: '姓名',
    width: 100,
  },
  {
    prop: 'patientAge',
    label: '年龄',
    width: 70,
    align: 'center',
  },
  {
    prop: 'patientGender',
    label: '性别',
    width: 70,
    align: 'center',
  },
  {
    label: '分诊级别',
    slot: 'level',
    align: 'center',
    width: 120,
  },
  {
    prop: 'triageTime',
    label: '分诊时间',
    width: 170,
  },
  {
    label: '等待时长',
    slot: 'waitMinutes',
    align: 'center',
    width: 100,
  },
  {
    prop: 'chiefComplaint',
    label: '主诉摘要',
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'organizationName',
    label: '分配科室',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusName',
    label: '状态',
    width: 100,
    align: 'center',
  },
  {
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 220,
    align: 'center',
  },
];

// 加载科室列表
async function loadOrgs() {
  orgOptions.value = await getOrganizationListApi();
}

// 获取候诊队列数据
async function fetchQueue() {
  loading.value = true;
  try {
    const res = await getQueuePageApi(queryParams.value);
    tableData.value = res.content ?? [];
    total.value = res.totalElements ?? 0;
  } catch {
    ElMessage.error('加载候诊队列失败');
  } finally {
    loading.value = false;
  }
}

// 启动轮询获取最新数据
function startPoll() {
  stopPoll();
  pollTimer = setInterval(() => {
    fetchQueue();
  }, 8000);
}

// 停止轮询
function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

// 处理分页变化
const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  fetchQueue();
};

// 处理查询条件变化
const handleQuery = (params: any) => {
  if (params.triageLevelEnum !== undefined) {
    queryParams.value.triageLevelEnum = params.triageLevelEnum;
  }
  if (params.statusEnum !== undefined) {
    queryParams.value.statusEnum = params.statusEnum;
  }
  if (params.organizationId !== undefined) {
    queryParams.value.organizationId = params.organizationId;
  }
  queryParams.value.pageNo = 1;
  fetchQueue();
};

// 重置查询条件
const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 20,
    triageLevelEnum: null,
    statusEnum: null,
    organizationId: null,
  };
  fetchQueue();
};

// 处理表格选择变化
const handleSelectionChange = (selection: QueueRow[] | Record<string, unknown>[]) => {
  selectedRows.value = selection as QueueRow[];
};

// 组件挂载时初始化数据
onMounted(() => {
  loadOrgs().catch(() => {});
  fetchQueue();
  startPoll();
});

// 组件卸载时停止轮询
onUnmounted(() => {
  stopPoll();
});

// 监听刷新令牌变化，触发数据刷新
watch(
  () => props.refreshToken,
  () => {
    fetchQueue();
  },
);

// 打开分诊记录详情
function openDetail(row: QueueRow) {
  detailRow.value = row;
  detailVisible.value = true;
}

// 打开修改/重新分诊对话框
function openAction(row: QueueRow, mode: 'modify' | 'reassign') {
  if (row.statusEnum === 3) {
    ElMessage.warning('已就诊患者不可修改分诊');
    return;
  }
  actionRow.value = row;
  actionMode.value = mode;
  actionReason.value = '';
  actionLevel.value = row.triageLevelEnum ?? null;
  actionOrgId.value = orgOptions.value.find((o) => o.name === row.organizationName)?.id ?? null;
  actionVisible.value = true;
}

// 确认修改/重新分诊
async function confirmAction() {
  if (!actionReason.value.trim()) {
    ElMessage.warning('请填写原因');
    return;
  }
  try {
    await ElMessageBox.confirm(
      actionMode.value === 'reassign'
        ? '确认重新分诊？原记录将标记为已重新分诊并生成新分诊。'
        : '确认修改分诊？将要求填写修改原因并留痕。',
      '二次确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  ElMessage.success('已提交（演示环境未调用后端，请对接 reassignTriage / modifyTriage）');
  actionVisible.value = false;
  fetchQueue();
}

// 根据行数据返回对应的行样式类名
function rowClassName({ row }: { row: QueueRow }) {
  return isWaitTimeout(row) ? 'row-timeout' : '';
}
</script>

<template>
  <Page skeleton>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :row-class-name="rowClassName"
      :show-side-query="false"
      :show-pagination="true"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <div class="stats-banner mt-4">{{ statsText }}</div>
        <ElButton type="primary" @click="fetchQueue">刷新</ElButton>
      </template>

      <template #level="{ row }">
        <ElTag :type="levelType(row.triageLevelEnum)" effect="dark">
          {{ row.triageLevelName ?? '—' }}
        </ElTag>
      </template>

      <template #waitMinutes="{ row }">
        <span :class="{ 'text-danger font-semibold': isWaitTimeout(row) }">
          {{ row.waitMinutes ?? 0 }} 分钟
        </span>
      </template>

      <template #action="{ row }">
        <ElButton type="warning" link @click="openAction(row, 'reassign')">重新分诊</ElButton>
        <ElButton type="primary" link @click="openAction(row, 'modify')">修改分诊</ElButton>
        <ElButton link @click="openDetail(row)">详情</ElButton>
      </template>
    </TableLayout>

    <el-dialog v-model="detailVisible" title="分诊记录详情" width="560px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="患者">{{ detailRow.patientName }}</el-descriptions-item>
        <el-descriptions-item label="分诊级别">
          {{ detailRow.triageLevelName }}
        </el-descriptions-item>
        <el-descriptions-item label="分诊时间">{{ detailRow.triageTime }}</el-descriptions-item>
        <el-descriptions-item label="主诉">{{ detailRow.chiefComplaint }}</el-descriptions-item>
        <el-descriptions-item label="科室">{{ detailRow.organizationName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailRow.statusName }}</el-descriptions-item>
        <el-descriptions-item label="就诊ID">
          {{ detailRow.encounterId ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="说明">
          修改/重新分诊历史需对接详情接口后展示
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog
      v-model="actionVisible"
      :title="actionMode === 'reassign' ? '重新分诊' : '修改分诊'"
      width="480px"
      destroy-on-close
      @close="actionRow = null"
    >
      <el-form v-if="actionRow" label-width="100px">
        <el-form-item label="患者">
          {{ actionRow.patientName }}
        </el-form-item>
        <el-form-item label="分诊级别">
          <el-select v-model="actionLevel" class="w-full">
            <el-option :value="1" label="一级（危急）" />
            <el-option :value="2" label="二级（急重）" />
            <el-option :value="3" label="三级（急症）" />
            <el-option :value="4" label="四级（亚急症）" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="actionOrgId" class="w-full" filterable>
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.name ?? ''" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="actionMode === 'reassign' ? '重新评估说明' : '修改原因'" required>
          <el-input
            v-model="actionReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <ElButton @click="actionVisible = false">取消</ElButton>
        <ElButton type="primary" @click="confirmAction">确定</ElButton>
      </template>
    </el-dialog>
  </Page>
</template>

<style scoped>
.stats-banner {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.text-danger {
  color: var(--el-color-danger);
}

:deep(.row-timeout) {
  background-color: var(--el-color-danger-light-9) !important;
}
</style>
