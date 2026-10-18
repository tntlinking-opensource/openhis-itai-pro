<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import type { TableColumn } from '@zxplat-core/table-layout-ui';

import { computed, reactive, ref } from 'vue';

import { Plus, Refresh } from '@element-plus/icons-vue';
import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
  ElDatePicker,
} from 'element-plus';

import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';
import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({
  name: 'FullSpecValidationPage',
});

type TaskStatus = 'done' | 'draft' | 'processing' | 'revoked';

interface QueryFormModel {
  dateRange: [] | [string, string];
  department: string;
  doctorName: string;
  isUrgent: boolean;
  keyword: string;
  patientName: string;
  remark: string;
  status: '' | TaskStatus;
}

interface TaskRow {
  createdAt: string;
  department: string;
  doctorName: string;
  id: string;
  patientName: string;
  status: TaskStatus;
  taskNo: string;
  updatedAt: string;
}

interface PageQueryParams {
  pageNo: number;
  pageSize: number;
}

const statusOptions: Array<{ label: string; value: QueryFormModel['status'] }> = [
  { label: '全部', value: '' },
  { label: '处理中', value: 'processing' },
  { label: '已完成', value: 'done' },
  { label: '草稿', value: 'draft' },
  { label: '已撤回', value: 'revoked' },
];

const departmentOptions = [
  { label: '内科', value: '内科' },
  { label: '外科', value: '外科' },
  { label: '儿科', value: '儿科' },
  { label: '骨科', value: '骨科' },
];

const sourceRows: TaskRow[] = [
  {
    id: '1',
    taskNo: 'NS-20260430-001',
    patientName: '张三',
    department: '内科',
    status: 'processing',
    doctorName: '李医生',
    createdAt: '2026-04-30 08:10:00',
    updatedAt: '2026-04-30 08:35:00',
  },
  {
    id: '2',
    taskNo: 'NS-20260430-002',
    patientName: '李四',
    department: '外科',
    status: 'done',
    doctorName: '王医生',
    createdAt: '2026-04-30 08:20:00',
    updatedAt: '2026-04-30 09:10:00',
  },
  {
    id: '3',
    taskNo: 'NS-20260430-003',
    patientName: '王五',
    department: '骨科',
    status: 'draft',
    doctorName: '刘医生',
    createdAt: '2026-04-30 08:30:00',
    updatedAt: '2026-04-30 08:32:00',
  },
  {
    id: '4',
    taskNo: 'NS-20260430-004',
    patientName: '赵六',
    department: '儿科',
    status: 'revoked',
    doctorName: '周医生',
    createdAt: '2026-04-30 08:40:00',
    updatedAt: '2026-04-30 08:50:00',
  },
  {
    id: '5',
    taskNo: 'NS-20260430-005',
    patientName: '孙七',
    department: '内科',
    status: 'processing',
    doctorName: '李医生',
    createdAt: '2026-04-30 08:50:00',
    updatedAt: '2026-04-30 09:20:00',
  },
  {
    id: '6',
    taskNo: 'NS-20260430-006',
    patientName: '周八',
    department: '外科',
    status: 'done',
    doctorName: '王医生',
    createdAt: '2026-04-30 09:00:00',
    updatedAt: '2026-04-30 09:30:00',
  },
  {
    id: '7',
    taskNo: 'NS-20260430-007',
    patientName: '吴九',
    department: '内科',
    status: 'processing',
    doctorName: '刘医生',
    createdAt: '2026-04-30 09:10:00',
    updatedAt: '2026-04-30 09:15:00',
  },
  {
    id: '8',
    taskNo: 'NS-20260430-008',
    patientName: '郑十',
    department: '儿科',
    status: 'draft',
    doctorName: '周医生',
    createdAt: '2026-04-30 09:20:00',
    updatedAt: '2026-04-30 09:23:00',
  },
  {
    id: '9',
    taskNo: 'NS-20260430-009',
    patientName: '钱一',
    department: '骨科',
    status: 'done',
    doctorName: '李医生',
    createdAt: '2026-04-30 09:30:00',
    updatedAt: '2026-04-30 09:40:00',
  },
  {
    id: '10',
    taskNo: 'NS-20260430-010',
    patientName: '钱二',
    department: '外科',
    status: 'processing',
    doctorName: '王医生',
    createdAt: '2026-04-30 09:40:00',
    updatedAt: '2026-04-30 09:50:00',
  },
  {
    id: '11',
    taskNo: 'NS-20260430-011',
    patientName: '钱三',
    department: '内科',
    status: 'revoked',
    doctorName: '刘医生',
    createdAt: '2026-04-30 09:45:00',
    updatedAt: '2026-04-30 10:00:00',
  },
  {
    id: '12',
    taskNo: 'NS-20260430-012',
    patientName: '钱四',
    department: '儿科',
    status: 'done',
    doctorName: '周医生',
    createdAt: '2026-04-30 09:48:00',
    updatedAt: '2026-04-30 10:10:00',
  },
  {
    id: '13',
    taskNo: 'NS-20260430-013',
    patientName: '钱五',
    department: '内科',
    status: 'draft',
    doctorName: '李医生',
    createdAt: '2026-04-30 10:00:00',
    updatedAt: '2026-04-30 10:05:00',
  },
  {
    id: '14',
    taskNo: 'NS-20260430-014',
    patientName: '钱六',
    department: '骨科',
    status: 'processing',
    doctorName: '王医生',
    createdAt: '2026-04-30 10:12:00',
    updatedAt: '2026-04-30 10:15:00',
  },
  {
    id: '15',
    taskNo: 'NS-20260430-015',
    patientName: '钱七',
    department: '外科',
    status: 'done',
    doctorName: '刘医生',
    createdAt: '2026-04-30 10:18:00',
    updatedAt: '2026-04-30 10:29:00',
  },
  {
    id: '16',
    taskNo: 'NS-20260430-016',
    patientName: '钱八',
    department: '儿科',
    status: 'processing',
    doctorName: '周医生',
    createdAt: '2026-04-30 10:22:00',
    updatedAt: '2026-04-30 10:35:00',
  },
  {
    id: '17',
    taskNo: 'NS-20260430-017',
    patientName: '钱九',
    department: '骨科',
    status: 'draft',
    doctorName: '李医生',
    createdAt: '2026-04-30 10:28:00',
    updatedAt: '2026-04-30 10:33:00',
  },
  {
    id: '18',
    taskNo: 'NS-20260430-018',
    patientName: '钱十',
    department: '外科',
    status: 'done',
    doctorName: '王医生',
    createdAt: '2026-04-30 10:40:00',
    updatedAt: '2026-04-30 10:55:00',
  },
  {
    id: '19',
    taskNo: 'NS-20260430-019',
    patientName: '赵一',
    department: '内科',
    status: 'processing',
    doctorName: '刘医生',
    createdAt: '2026-04-30 10:45:00',
    updatedAt: '2026-04-30 10:58:00',
  },
  {
    id: '20',
    taskNo: 'NS-20260430-020',
    patientName: '赵二',
    department: '儿科',
    status: 'revoked',
    doctorName: '周医生',
    createdAt: '2026-04-30 11:00:00',
    updatedAt: '2026-04-30 11:05:00',
  },
  {
    id: '21',
    taskNo: 'NS-20260430-021',
    patientName: '赵三',
    department: '外科',
    status: 'processing',
    doctorName: '李医生',
    createdAt: '2026-04-30 11:08:00',
    updatedAt: '2026-04-30 11:15:00',
  },
  {
    id: '22',
    taskNo: 'NS-20260430-022',
    patientName: '赵四',
    department: '骨科',
    status: 'done',
    doctorName: '王医生',
    createdAt: '2026-04-30 11:20:00',
    updatedAt: '2026-04-30 11:40:00',
  },
];

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  { prop: 'taskNo', label: '任务编号', minWidth: 150, align: 'center', headerAlign: 'center' },
  { prop: 'patientName', label: '患者姓名', minWidth: 110, align: 'center', headerAlign: 'center' },
  { prop: 'department', label: '科室', width: 100, align: 'center', headerAlign: 'center' },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  { prop: 'createdAt', label: '创建时间', minWidth: 160, align: 'center', headerAlign: 'center' },
  { prop: 'updatedAt', label: '更新时间', minWidth: 160, align: 'center', headerAlign: 'center' },
  {
    prop: 'action',
    label: '操作',
    minWidth: 250,
    slot: 'action',
    fixed: 'right',
    headerAlign: 'center',
  },
];

// 业务状态：筛选条件与当前选中行 ID，直接决定数据结果与操作目标。
const formModel = reactive<QueryFormModel>({
  keyword: '',
  patientName: '',
  status: '',
  department: '',
  dateRange: [],
  doctorName: '',
  remark: '',
  isUrgent: false,
});
const selectedTaskId = ref('');

// 视图状态：列表、分页、加载与错误态开关，仅影响展示与交互反馈。
const pageQueryParams = reactive<PageQueryParams>({
  pageNo: 1,
  pageSize: 10,
});
const tableData = ref<TaskRow[]>([]);
const total = ref(0);
const loading = ref(false);
const enableErrorMock = ref(false);

// 临时状态：短期交互态，不作为业务判断依据。
const formRef = ref<FormInstance>();
const formAreaRef = ref<HTMLElement | null>(null);
const hoveredTaskId = ref('');

const formRules: FormRules<QueryFormModel> = {
  keyword: [{ required: true, message: '请输入关键字', trigger: 'blur' }],
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  department: [{ required: true, message: '请选择科室', trigger: 'change' }],
  dateRange: [{ required: true, message: '请选择日期范围', trigger: 'change' }],
};

const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  // 备注是多行输入场景，Enter 需保持原生换行而不是跳焦点。
  shouldIgnoreEvent: (event, target) => target.tagName === 'TEXTAREA' && event.key === 'Enter',
});

const subtitleText = computed(() => {
  if (enableErrorMock.value) {
    return '当前处于错误态模拟开关开启状态，列表请求将返回失败提示。';
  }
  return '用于统一验证布局、分节、表格、按钮、键盘导航、复用与样式基线约束。';
});

// 按状态码映射状态标签样式，保持列表视觉一致性。
function getStatusTagType(status: TaskStatus): 'danger' | 'info' | 'success' | 'warning' {
  if (status === 'processing') return 'warning';
  if (status === 'done') return 'success';
  if (status === 'revoked') return 'danger';
  return 'info';
}

// 根据筛选条件与分页计算当前页数据，作为唯一列表数据来源。
function buildFilteredRows(): TaskRow[] {
  const keyword = formModel.keyword.trim();
  const patientName = formModel.patientName.trim();
  return sourceRows.filter((row) => {
    const matchKeyword =
      keyword.length === 0 ||
      row.taskNo.includes(keyword) ||
      row.patientName.includes(keyword) ||
      row.doctorName.includes(keyword);
    const matchPatient = patientName.length === 0 || row.patientName.includes(patientName);
    const matchStatus = formModel.status === '' || row.status === formModel.status;
    const matchDepartment = formModel.department === '' || row.department === formModel.department;
    const matchDoctor =
      formModel.doctorName.trim().length === 0 ||
      row.doctorName.includes(formModel.doctorName.trim());
    return matchKeyword && matchPatient && matchStatus && matchDepartment && matchDoctor;
  });
}

// 刷新列表：按当前筛选和分页刷新数据，形成操作后的统一闭环入口。
function refreshTableData() {
  loading.value = true;
  if (enableErrorMock.value) {
    loading.value = false;
    tableData.value = [];
    total.value = 0;
    ElMessage.error('模拟请求失败：请关闭错误态开关后重试');
    return;
  }
  const filteredRows = buildFilteredRows();
  total.value = filteredRows.length;
  const startIndex = (pageQueryParams.pageNo - 1) * pageQueryParams.pageSize;
  const endIndex = startIndex + pageQueryParams.pageSize;
  tableData.value = filteredRows.slice(startIndex, endIndex);
  loading.value = false;
}

// 查询动作：先校验必填筛选条件，再回到第一页刷新列表。
async function handleQuery() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    ElMessage.warning('请先补全查询条件');
    return;
  }
  pageQueryParams.pageNo = 1;
  refreshTableData();
}

// 重置动作：清空筛选字段并刷新列表，验证空结果与默认结果切换。
function handleReset() {
  formRef.value?.resetFields();
  formModel.status = '';
  formModel.department = '';
  formModel.isUrgent = false;
  pageQueryParams.pageNo = 1;
  selectedTaskId.value = '';
  refreshTableData();
}

// 分页动作：写回分页参数后刷新数据，避免隐式 watch 联动。
function handlePagination(pagination: { page: number; limit: number }) {
  pageQueryParams.pageNo = pagination.page;
  pageQueryParams.pageSize = pagination.limit;
  refreshTableData();
}

// 新增动作：模拟前置校验与成功反馈，并触发列表刷新闭环。
function handleAdd() {
  if (enableErrorMock.value) {
    ElMessage.error('错误态开启时不可新增');
    return;
  }
  ElMessage.success('新增成功（模拟）');
  refreshTableData();
}

// 刷新动作：作为中性操作按钮入口，直接复用列表刷新逻辑。
function handleRefresh() {
  refreshTableData();
  ElMessage.success('已刷新');
}

// 撤回动作：仅处理中的任务允许撤回，并反馈结果后刷新列表。
function handleWithdrawTop() {
  if (!selectedTaskId.value) {
    ElMessage.warning('请先在操作列点击“查看/编辑”选中任务');
    return;
  }
  const row = sourceRows.find((item) => item.id === selectedTaskId.value);
  if (!row || row.status !== 'processing') {
    ElMessage.error('仅处理中任务可撤回');
    return;
  }
  row.status = 'revoked';
  ElMessage.success('撤回成功');
  refreshTableData();
}

// 删除动作：仅草稿任务可删除，保证操作前置校验明确。
function handleDeleteTop() {
  if (!selectedTaskId.value) {
    ElMessage.warning('请先在操作列点击“查看/编辑”选中任务');
    return;
  }
  const row = sourceRows.find((item) => item.id === selectedTaskId.value);
  if (!row || row.status !== 'draft') {
    ElMessage.error('仅草稿任务可删除');
    return;
  }
  const index = sourceRows.findIndex((item) => item.id === selectedTaskId.value);
  sourceRows.splice(index, 1);
  selectedTaskId.value = '';
  ElMessage.success('删除成功');
  refreshTableData();
}

// 查看动作：写入当前选中任务，用于后续顶部操作前置校验。
function handleView(row: TaskRow) {
  selectedTaskId.value = row.id;
  ElMessage.success(`已查看任务 ${row.taskNo}`);
}

// 编辑动作：仅草稿与处理中状态允许编辑，编辑后刷新列表。
function handleEdit(row: TaskRow) {
  if (row.status !== 'draft' && row.status !== 'processing') {
    ElMessage.error('当前状态不可编辑');
    return;
  }
  selectedTaskId.value = row.id;
  ElMessage.success(`编辑成功（模拟）: ${row.taskNo}`);
  refreshTableData();
}

// 行内撤回：二次确认后执行撤回并刷新，形成完整反馈闭环。
async function handleWithdrawRow(row: TaskRow) {
  if (row.status !== 'processing') {
    ElMessage.error('仅处理中任务可撤回');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认撤回任务 ${row.taskNo} 吗？`, '撤回确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
    row.status = 'revoked';
    ElMessage.success('撤回成功');
    refreshTableData();
  } catch {
    ElMessage.info('已取消撤回');
  }
}

// 行内删除：仅草稿允许删除，删除后刷新保证展示数据一致。
async function handleDeleteRow(row: TaskRow) {
  if (row.status !== 'draft') {
    ElMessage.error('仅草稿任务可删除');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除任务 ${row.taskNo} 吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
    const index = sourceRows.findIndex((item) => item.id === row.id);
    sourceRows.splice(index, 1);
    ElMessage.success('删除成功');
    refreshTableData();
  } catch {
    ElMessage.info('已取消删除');
  }
}

refreshTableData();
</script>

<template>
  <Page auto-content-height class="full-spec-validation-page" content-class="h-full">
    <div class="page-head">
      <SectionTitle title="综合规范验证页" />
      <p class="subtitle">{{ subtitleText }}</p>
    </div>

    <section ref="formAreaRef" class="section-card" @keydown="handleFormKeydown">
      <SectionTitle title="查询条件" />
      <ElForm
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        class="query-form"
        label-width="88px"
        label-position="right"
        require-asterisk-position="left"
      >
        <div class="form-grid">
          <ElFormItem label="关键字" prop="keyword">
            <ElInput v-model="formModel.keyword" clearable placeholder="任务编号/患者姓名/医生" />
          </ElFormItem>
          <ElFormItem label="患者姓名" prop="patientName">
            <ElInput v-model="formModel.patientName" clearable placeholder="请输入患者姓名" />
          </ElFormItem>
          <ElFormItem label="状态" prop="status">
            <ElSelect v-model="formModel.status" placeholder="请选择状态">
              <ElOption
                v-for="item in statusOptions"
                :key="`${item.value}`"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="科室" prop="department">
            <ElSelect v-model="formModel.department" clearable placeholder="请选择科室">
              <ElOption
                v-for="item in departmentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem class="col-span-2" label="日期范围" prop="dateRange">
            <ElDatePicker
              v-model="formModel.dateRange"
              clearable
              type="daterange"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
            />
          </ElFormItem>
          <ElFormItem label="医生" prop="doctorName">
            <ElInput v-model="formModel.doctorName" clearable placeholder="请输入医生姓名" />
          </ElFormItem>
          <ElFormItem label="是否加急">
            <ElSwitch
              v-model="formModel.isUrgent"
              inline-prompt
              active-text="是"
              inactive-text="否"
            />
          </ElFormItem>
          <ElFormItem class="col-span-3" label="备注" prop="remark">
            <ElInput
              v-model="formModel.remark"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="用于验证 textarea 回车换行行为"
            />
          </ElFormItem>
        </div>
        <div class="query-action-row">
          <div class="flex items-center gap-2">
            <ElButton type="primary" @click="handleQuery">查询</ElButton>
            <ElButton @click="handleReset">重置</ElButton>
          </div>
          <ElSwitch
            v-model="enableErrorMock"
            active-text="错误态模拟"
            inactive-text="正常态"
            @change="handleRefresh"
          />
        </div>
      </ElForm>
    </section>

    <section class="section-card mt-3">
      <SectionTitle title="结果列表" />
      <TableLayout
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="pageQueryParams"
        :table-columns="tableColumns"
        :show-top-query="false"
        :show-pagination="true"
        border
        stripe
        @pagination="handlePagination"
      >
        <template #toolbar-actions>
          <div class="mb-2 flex w-full items-center gap-1">
            <ElButton type="primary" :icon="Plus" @click="handleAdd">新增</ElButton>
            <ElButton plain :icon="Refresh" @click="handleRefresh">刷新</ElButton>
            <ElButton type="warning" plain @click="handleWithdrawTop">撤回</ElButton>
            <ElButton type="danger" plain @click="handleDeleteTop">删除</ElButton>
          </div>
        </template>
        <template #status="{ row }">
          <ElTag :type="getStatusTagType(row.status)" effect="light">{{ row.status }}</ElTag>
        </template>
        <template #action="{ row }">
          <div
            class="flex items-center justify-center gap-2"
            @mouseenter="hoveredTaskId = row.id"
            @mouseleave="hoveredTaskId = ''"
          >
            <ElButton link type="primary" @click="handleView(row)">查看</ElButton>
            <ElButton link type="primary" @click="handleEdit(row)">编辑</ElButton>
            <ElButton link type="warning" @click="handleWithdrawRow(row)">撤回</ElButton>
            <ElButton link type="danger" @click="handleDeleteRow(row)">删除</ElButton>
          </div>
        </template>
      </TableLayout>
    </section>
  </Page>
</template>

<style scoped>
.full-spec-validation-page {
  min-height: 100%;
  background-color: var(--el-fill-color-lighter);
}

.page-head {
  margin-bottom: 12px;
}

.subtitle {
  margin: 6px 0 0 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.section-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background-color: var(--el-bg-color);
  padding: 12px;
}

.query-form {
  margin-top: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
}

.col-span-2 {
  grid-column: span 2;
}

.col-span-3 {
  grid-column: span 3;
}

:deep(.el-form-item) {
  margin-bottom: 2px;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper),
:deep(.el-textarea__inner),
:deep(.el-date-editor) {
  min-height: 32px;
}

:deep(.el-select),
:deep(.el-date-editor) {
  width: 100%;
}

.query-action-row {
  margin-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
