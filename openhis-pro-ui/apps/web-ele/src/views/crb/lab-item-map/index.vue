<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { CrbCodeMapPageParams, LabItemMapRowDto, SaveLabItemMapRequest } from '#/api/crb';

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
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import {
  importLabItemMapApi,
  pageLabItemMapApi,
  parseCrbCsvRows,
  resolveCrbTenantId,
  saveLabItemMapApi,
  softDeleteLabItemMapApi,
} from '#/api/crb';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'CrbLabItemMap' });

const RESULT_SHAPE_OPTIONS = [
  { label: '定性(QUAL)', value: 'QUAL' },
  { label: '定量(QUANT)', value: 'QUANT' },
];

const ANNEX2_SCOPE_OPTIONS = [
  { label: '病原体(PATHOGEN)', value: 'PATHOGEN' },
  { label: '临床化学(CLIN_CHEM)', value: 'CLIN_CHEM' },
  { label: '药敏(DRUG_SENS)', value: 'DRUG_SENS' },
];

const tableData = ref<LabItemMapRowDto[]>([]);
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

const editForm = reactive<SaveLabItemMapRequest>({
  id: undefined,
  itemBusNo: '',
  itemName: '',
  nationalItemCode: '',
  resultShape: undefined,
  annex2Scope: undefined,
  pushEnabled: true,
});

const formItems: FormItemConfig[] = [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '项目编码/名称',
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
  { prop: 'itemBusNo', label: '院内项目编码', width: 130, showOverflowTooltip: true },
  { prop: 'itemName', label: '院内项目名称', minWidth: 130, showOverflowTooltip: true },
  { prop: 'nationalItemCode', label: '国家项目编码', width: 130, showOverflowTooltip: true },
  { prop: 'resultShape', label: '结果形态', width: 100, align: 'center' },
  { prop: 'annex2Scope', label: '附件二范围', width: 120, align: 'center' },
  { prop: 'pushEnabled', label: '启用推送', width: 90, align: 'center', slot: 'pushEnabled' },
  { prop: 'mapped', label: '对照状态', width: 100, align: 'center', slot: 'mapped' },
  { label: '操作', width: 140, fixed: 'right', slot: 'actions', align: 'center' },
];

const { run: runPage, loading: pageLoading } = useRequest(pageLabItemMapApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<LabItemMapRowDto>(data);
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

/** 拉取检验对照列表。 */
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
function resetEditForm(row?: LabItemMapRowDto) {
  editForm.id = preserveApiIdForRequest(row?.id);
  editForm.itemBusNo = row?.itemBusNo ?? '';
  editForm.itemName = row?.itemName ?? '';
  editForm.nationalItemCode = row?.nationalItemCode ?? '';
  editForm.resultShape = row?.resultShape ?? undefined;
  editForm.annex2Scope = row?.annex2Scope ?? undefined;
  editForm.pushEnabled = row?.pushEnabled ?? true;
}

/** 打开新增弹窗。 */
function handleAdd() {
  resetEditForm();
  dialogVisible.value = true;
}

/** 打开编辑弹窗。 */
function handleEdit(row: LabItemMapRowDto) {
  resetEditForm(row);
  dialogVisible.value = true;
}

/** 提交保存检验对照。 */
async function handleSave() {
  const itemBusNo = editForm.itemBusNo.trim();
  if (!itemBusNo) {
    ElMessage.warning('院内项目编码不能为空');
    return;
  }
  saving.value = true;
  try {
    const saved = await saveLabItemMapApi(resolveCrbTenantId(), {
      id: editForm.id,
      itemBusNo,
      itemName: editForm.itemName?.trim() || undefined,
      nationalItemCode: editForm.nationalItemCode?.trim() || undefined,
      resultShape: editForm.resultShape || undefined,
      annex2Scope: editForm.annex2Scope || undefined,
      pushEnabled: editForm.pushEnabled,
    });
    // 附件二范围外被强制关推送时提示实际结果
    if (saved?.pushForcedOff && saved.hint) {
      ElMessage.warning(saved.hint);
    } else {
      ElMessage.success('保存成功');
    }
    dialogVisible.value = false;
    getList();
  } finally {
    saving.value = false;
  }
}

/** 软删检验对照行。 */
async function handleDelete(row: LabItemMapRowDto) {
  const id = preserveApiIdForRequest(row.id);
  if (!id) {
    ElMessage.warning('主键缺失');
    return;
  }
  try {
    await ElMessageBox.confirm('确定删除该检验对照吗？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await softDeleteLabItemMapApi(resolveCrbTenantId(), id);
  ElMessage.success('删除成功');
  getList();
}

/** 打开 CSV 文件选择。 */
function handleImportClick() {
  fileInputRef.value?.click();
}

/** 读取检验对照 CSV 并导入。 */
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
    const rows: SaveLabItemMapRequest[] = [];
    for (const raw of rawRows) {
      const itemBusNo = raw.itemBusNo?.trim();
      if (!itemBusNo) {
        continue;
      }
      rows.push({
        itemBusNo,
        itemName: raw.itemName || undefined,
        nationalItemCode: raw.nationalItemCode || undefined,
        resultShape: raw.resultShape || undefined,
        annex2Scope: raw.annex2Scope || undefined,
        pushEnabled: raw.pushEnabled === 'false' ? false : true,
      });
    }
    if (rows.length === 0) {
      ElMessage.warning('CSV 无有效行');
      return;
    }
    const result = await importLabItemMapApi(resolveCrbTenantId(), rows);
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
      <template #pushEnabled="{ row }">
        <ElTag :type="row.pushEnabled ? 'success' : 'info'" size="small">
          {{ row.pushEnabled ? '是' : '否' }}
        </ElTag>
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
      :title="editForm.id ? '编辑检验对照' : '新增检验对照'"
      width="560px"
    >
      <ElForm label-width="120px">
        <ElFormItem label="院内项目编码" required>
          <ElInput v-model="editForm.itemBusNo" :disabled="!!editForm.id" />
        </ElFormItem>
        <ElFormItem label="院内项目名称">
          <ElInput v-model="editForm.itemName" />
        </ElFormItem>
        <ElFormItem label="国家项目编码">
          <ElInput v-model="editForm.nationalItemCode" />
        </ElFormItem>
        <ElFormItem label="结果形态">
          <ElSelect v-model="editForm.resultShape" clearable placeholder="请选择">
            <ElOption
              v-for="opt in RESULT_SHAPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="附件二范围">
          <ElSelect v-model="editForm.annex2Scope" clearable placeholder="请选择">
            <ElOption
              v-for="opt in ANNEX2_SCOPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="启用推送">
          <ElSwitch v-model="editForm.pushEnabled" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
        <ElButton @click="dialogVisible = false">取消</ElButton>
      </template>
    </AppDialog>
  </Page>
</template>
