<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { ChargeContrastDto, PageContrastsRequest } from '#/api/einvoice-boss/contrast';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { useAccess } from '@zxplat/access';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import { deleteContrastApi, pageContrastsApi, saveContrastApi } from '#/api/einvoice-boss/contrast';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'EinvoiceContrast',
});

const { hasAccessByCodes } = useAccess();
const canManage = computed(() => hasAccessByCodes(['einvoice-boss.contrast.manage']));

const tableData = ref<ChargeContrastDto[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const editingId = ref<string | undefined>();

const queryParams = reactive<PageContrastsRequest>({
  pageNo: 1,
  pageSize: 20,
});

const formModel = reactive({
  internalCode: '',
  bossChargeCode: '',
  bossChargeName: '',
  orgId: '',
});

const { run: runList, loading: listLoading } = useRequest(pageContrastsApi, { manual: true });
const { run: runSave, loading: saveLoading } = useRequest(saveContrastApi, { manual: true });
const { run: runDelete, loading: deleteLoading } = useRequest(deleteContrastApi, { manual: true });

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '院内编码 / 博思编码 / 名称',
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, fixed: 'left' },
  { prop: 'internalCode', label: '院内编码', minWidth: 120, showOverflowTooltip: true },
  { prop: 'bossChargeCode', label: '博思收费编码', minWidth: 140, showOverflowTooltip: true },
  { prop: 'bossChargeName', label: '博思收费名称', minWidth: 160, showOverflowTooltip: true },
  { prop: 'orgId', label: '机构', width: 100, slot: 'orgId' },
  { prop: 'updatedAt', label: '更新时间', minWidth: 150, slot: 'updatedAt' },
  { prop: 'action', label: '操作', fixed: 'right', width: 140, align: 'center', slot: 'action' },
];

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function resetFormModel() {
  formModel.internalCode = '';
  formModel.bossChargeCode = '';
  formModel.bossChargeName = '';
  formModel.orgId = '';
}

async function getList(params?: PageContrastsRequest) {
  const p = { ...queryParams, ...(params ?? {}) };
  const result = await runList({
    keyword: p.keyword?.trim() || undefined,
    pageNo: p.pageNo,
    pageSize: p.pageSize,
  });
  tableData.value = result?.rows ?? [];
  total.value = Number(result?.total ?? 0);
}

function handleQuery(params: PageContrastsRequest) {
  Object.assign(queryParams, params);
  void getList(params);
}

function handleReset() {
  queryParams.keyword = '';
  queryParams.pageNo = 1;
  void getList();
}

/** 分页变更（TableLayout 契约为 page/limit） */
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  void getList();
}

function openCreate() {
  editingId.value = undefined;
  resetFormModel();
  dialogVisible.value = true;
}

function openEdit(row: ChargeContrastDto) {
  editingId.value = row.id;
  formModel.internalCode = row.internalCode ?? '';
  formModel.bossChargeCode = row.bossChargeCode ?? '';
  formModel.bossChargeName = row.bossChargeName ?? '';
  formModel.orgId = row.orgId ?? '';
  dialogVisible.value = true;
}

async function handleSave() {
  const internalCode = formModel.internalCode.trim();
  const bossChargeCode = formModel.bossChargeCode.trim();
  const bossChargeName = formModel.bossChargeName.trim();
  if (!internalCode || !bossChargeCode) {
    ElMessage.warning('请填写院内编码和博思收费编码');
    return;
  }
  const orgId = formModel.orgId.trim();
  await runSave({
    id: editingId.value,
    orgId: orgId || undefined,
    internalCode,
    bossChargeCode,
    bossChargeName: bossChargeName || undefined,
  });
  ElMessage.success(editingId.value ? '已更新' : '已新增');
  dialogVisible.value = false;
  await getList();
}

async function handleDelete(row: ChargeContrastDto) {
  if (!row.id) {
    return;
  }
  await ElMessageBox.confirm(
    `确定删除对照「${row.internalCode ?? ''} → ${row.bossChargeCode ?? ''}」？`,
    '删除确认',
    { type: 'warning' },
  );
  await runDelete(row.id);
  ElMessage.success('已删除');
  await getList();
}

onMounted(() => {
  void getList();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      v-model:query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :form-items="formItems"
      :loading="listLoading"
      :total="total"
      show-pagination
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template v-if="canManage" #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新增对照</ElButton>
      </template>

      <template #orgId="{ row }">
        {{ row.orgId || '租户默认' }}
      </template>

      <template #updatedAt="{ row }">
        {{ formatDateTime(row.updatedAt) }}
      </template>

      <template #action="{ row }">
        <template v-if="canManage">
          <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
          <ElButton link type="danger" :loading="deleteLoading" @click="handleDelete(row)">
            删除
          </ElButton>
        </template>
        <span v-else class="text-[var(--el-text-color-placeholder)]">-</span>
      </template>
    </TableLayout>

    <ElDialog
      v-model="dialogVisible"
      :title="editingId ? '编辑收费对照' : '新增收费对照'"
      width="520px"
      destroy-on-close
      align-center
    >
      <ElForm label-width="120px">
        <ElFormItem label="院内编码" required>
          <ElInput
            v-model="formModel.internalCode"
            placeholder="如 REG_FEE"
            :disabled="!!editingId"
          />
        </ElFormItem>
        <ElFormItem label="博思收费编码" required>
          <ElInput v-model="formModel.bossChargeCode" placeholder="博思平台收费项目编码" />
        </ElFormItem>
        <ElFormItem label="博思收费名称">
          <ElInput v-model="formModel.bossChargeName" placeholder="可选，便于识别" />
        </ElFormItem>
        <ElFormItem label="机构 ID">
          <ElInput v-model="formModel.orgId" placeholder="留空表示租户级默认" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saveLoading" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
