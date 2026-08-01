<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { CrbCodeMapPageParams, DrugCodeMapRowDto, SaveDrugCodeMapRequest } from '#/api/crb';

import { onActivated, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import {
  importDrugCodeMapApi,
  pageDrugCodeMapApi,
  parseCrbCsvRows,
  resolveCrbTenantId,
  saveDrugCodeMapApi,
  softDeleteDrugCodeMapApi,
} from '#/api/crb';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'CrbDrugMap' });

const tableData = ref<DrugCodeMapRowDto[]>([]);
const total = ref(0);
const loading = ref(false);
const dialogVisible = ref(false);
const saving = ref(false);
const importing = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

const queryParams = reactive<CrbCodeMapPageParams>({
  pageNo: 1,
  pageSize: 20,
  keyword: undefined,
  unmappedOnly: undefined,
});

const editForm = reactive<SaveDrugCodeMapRequest>({
  id: undefined,
  medBusNo: '',
  medName: '',
  nationalDrugCode: '',
  nationalDrugName: '',
});

const formItems: FormItemConfig[] = [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '药品编码/名称',
    clearable: true,
  },
  {
    label: '对照状态',
    prop: 'unmappedOnly',
    type: 'select',
    clearable: true,
    placeholder: '全部',
    options: [{ label: '仅未对照', value: 'true' }],
  },
];

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center' },
  { prop: 'medBusNo', label: '院内药品编码', width: 140, showOverflowTooltip: true },
  { prop: 'medName', label: '院内药品名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'nationalDrugCode', label: '国家药品编码', width: 140, showOverflowTooltip: true },
  { prop: 'nationalDrugName', label: '国家药品名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'mapped', label: '对照状态', width: 100, align: 'center', slot: 'mapped' },
  { label: '操作', width: 140, fixed: 'right', slot: 'actions', align: 'center' },
];

const { run: runPage, loading: pageLoading } = useRequest(pageDrugCodeMapApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<DrugCodeMapRowDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

/** 组装分页查询参数。 */
function buildQuery(): CrbCodeMapPageParams {
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    unmappedOnly: queryParams.unmappedOnly === 'true' ? true : undefined,
  };
}

/** 拉取药品对照列表。 */
async function getList() {
  await runPage(resolveCrbTenantId(), buildQuery());
}

/** 重置筛选条件。 */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.keyword = undefined;
  queryParams.unmappedOnly = undefined;
  getList();
}

/** 分页变更。 */
function handlePagination(payload: { pageNo: number; pageSize: number }) {
  queryParams.pageNo = payload.pageNo;
  queryParams.pageSize = payload.pageSize;
  getList();
}

/** 重置编辑表单。 */
function resetEditForm(row?: DrugCodeMapRowDto) {
  editForm.id = preserveApiIdForRequest(row?.id);
  editForm.medBusNo = row?.medBusNo ?? '';
  editForm.medName = row?.medName ?? '';
  editForm.nationalDrugCode = row?.nationalDrugCode ?? '';
  editForm.nationalDrugName = row?.nationalDrugName ?? '';
}

/** 打开新增弹窗。 */
function handleAdd() {
  resetEditForm();
  dialogVisible.value = true;
}

/** 打开编辑弹窗。 */
function handleEdit(row: DrugCodeMapRowDto) {
  resetEditForm(row);
  dialogVisible.value = true;
}

/** 提交保存药品对照。 */
async function handleSave() {
  const medBusNo = editForm.medBusNo.trim();
  if (!medBusNo) {
    ElMessage.warning('院内药品编码不能为空');
    return;
  }
  saving.value = true;
  try {
    await saveDrugCodeMapApi(resolveCrbTenantId(), {
      id: editForm.id,
      medBusNo,
      medName: editForm.medName?.trim() || undefined,
      nationalDrugCode: editForm.nationalDrugCode?.trim() || undefined,
      nationalDrugName: editForm.nationalDrugName?.trim() || undefined,
    });
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    getList();
  } finally {
    saving.value = false;
  }
}

/** 软删药品对照行。 */
async function handleDelete(row: DrugCodeMapRowDto) {
  const id = preserveApiIdForRequest(row.id);
  if (!id) {
    ElMessage.warning('主键缺失');
    return;
  }
  try {
    await ElMessageBox.confirm('确定删除该药品对照吗？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await softDeleteDrugCodeMapApi(resolveCrbTenantId(), id);
  ElMessage.success('删除成功');
  getList();
}

/** 打开 CSV 文件选择。 */
function handleImportClick() {
  fileInputRef.value?.click();
}

/** 读取药品对照 CSV 并导入。 */
async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  importing.value = true;
  try {
    const text = await file.text();
    const rawRows = parseCrbCsvRows(text);
    const rows: SaveDrugCodeMapRequest[] = [];
    for (const raw of rawRows) {
      const medBusNo = raw.medBusNo?.trim();
      if (!medBusNo) {
        continue;
      }
      rows.push({
        medBusNo,
        medName: raw.medName || undefined,
        nationalDrugCode: raw.nationalDrugCode || undefined,
        nationalDrugName: raw.nationalDrugName || undefined,
      });
    }
    if (rows.length === 0) {
      ElMessage.warning('CSV 无有效行');
      return;
    }
    const result = await importDrugCodeMapApi(resolveCrbTenantId(), rows);
    ElMessage.success(
      `导入完成：成功 ${result?.successCount ?? 0}，跳过 ${result?.skipCount ?? 0}，失败 ${result?.failCount ?? 0}`,
    );
    getList();
  } finally {
    importing.value = false;
  }
}

onMounted(() => {
  getList();
});

onActivated(() => {
  getList();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams as Record<string, unknown>"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="id"
      :show-top-query="true"
      :show-pagination="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton :loading="importing" @click="handleImportClick">导入 CSV</ElButton>
        <input
          ref="fileInputRef"
          type="file"
          accept=".csv,text/csv"
          class="hidden"
          @change="handleImportFile"
        />
      </template>
      <template #mapped="{ row }">
        <ElTag :type="row.mapped ? 'success' : 'warning'" size="small">
          {{ row.mapped ? '已对照' : '未对照' }}
        </ElTag>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="handleEdit(row)">编辑</ElButton>
        <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>

    <AppDialog
      v-model="dialogVisible"
      :title="editForm.id ? '编辑药品对照' : '新增药品对照'"
      width="520px"
    >
      <ElForm label-width="120px">
        <ElFormItem label="院内药品编码" required>
          <ElInput v-model="editForm.medBusNo" :disabled="!!editForm.id" />
        </ElFormItem>
        <ElFormItem label="院内药品名称">
          <ElInput v-model="editForm.medName" />
        </ElFormItem>
        <ElFormItem label="国家药品编码">
          <ElInput v-model="editForm.nationalDrugCode" />
        </ElFormItem>
        <ElFormItem label="国家药品名称">
          <ElInput v-model="editForm.nationalDrugName" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
        <ElButton @click="dialogVisible = false">取消</ElButton>
      </template>
    </AppDialog>
  </Page>
</template>
