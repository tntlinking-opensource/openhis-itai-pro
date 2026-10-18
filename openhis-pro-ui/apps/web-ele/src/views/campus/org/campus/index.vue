<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { CampusSiteDto } from '#/api/campus/campus-site';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  changeCampusSiteActiveApi,
  createCampusSiteApi,
  pageCampusSitesApi,
  setPrimaryCampusSiteApi,
  updateCampusSiteApi,
} from '#/api/campus/campus-site';
import { unwrapCampusPage } from '#/api/campus/types';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'CampusSiteManage' });

const list = ref<CampusSiteDto[]>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Record<string, unknown>>({});
const isCreate = ref(true);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  activeFlag: undefined as boolean | undefined,
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
  {
    prop: 'activeFlag',
    label: '状态',
    type: 'select',
    clearable: true,
    multiple: false,
    options: [
      { label: '启用', value: true },
      { label: '停用', value: false },
    ],
  },
];

const columns: TableColumn[] = [
  { prop: 'campusName', label: '校区名称', minWidth: 140 },
  { prop: 'campusCode', label: '编码', minWidth: 100 },
  { prop: 'shortName', label: '简称', minWidth: 90 },
  { prop: 'address', label: '地址', minWidth: 160, showOverflowTooltip: true },
  { prop: 'isPrimary', label: '主校区', width: 90, slot: 'isPrimary' },
  { prop: 'activeFlag', label: '状态', width: 90, slot: 'activeFlag' },
  { prop: 'actions', label: '操作', width: 200, slot: 'actions' },
];

async function loadList() {
  const res = await pageCampusSitesApi({
    keyword: String(query.keyword || '').trim() || undefined,
    activeFlag: query.activeFlag ?? null,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
  const page = unwrapCampusPage(res);
  list.value = page.list;
  total.value = page.total;
}

const { run: runLoad, loading } = useRequest(loadList, { manual: true });

function handleSearch() {
  query.pageNo = 1;
  void runLoad();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void runLoad();
}

function openCreate() {
  isCreate.value = true;
  editForm.value = { sortOrder: 0 };
  editVisible.value = true;
}

function openEdit(row: CampusSiteDto) {
  isCreate.value = false;
  editForm.value = { ...row };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!String(form.campusCode || '').trim() && isCreate.value) {
    ElMessage.warning('请填写校区编码');
    return;
  }
  if (!String(form.campusName || '').trim()) {
    ElMessage.warning('请填写校区名称');
    return;
  }
  if (isCreate.value) {
    await createCampusSiteApi({
      campusCode: String(form.campusCode).trim(),
      campusName: String(form.campusName).trim(),
      shortName: String(form.shortName || '').trim() || undefined,
      address: String(form.address || '').trim() || undefined,
      sortOrder: Number(form.sortOrder ?? 0),
      remark: String(form.remark || '').trim() || undefined,
    });
  } else {
    await updateCampusSiteApi({
      id: form.id as string,
      campusName: String(form.campusName).trim(),
      shortName: String(form.shortName || '').trim() || undefined,
      address: String(form.address || '').trim() || undefined,
      sortOrder: Number(form.sortOrder ?? 0),
      remark: String(form.remark || '').trim() || undefined,
    });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

async function toggleActive(row: CampusSiteDto) {
  if (row.activeFlag) {
    try {
      await ElMessageBox.confirm(`确认停用校区「${row.campusName}」？`, '停用校区', {
        type: 'warning',
      });
    } catch {
      return;
    }
    await changeCampusSiteActiveApi({ id: row.id!, activeFlag: false });
  } else {
    await changeCampusSiteActiveApi({ id: row.id!, activeFlag: true });
  }
  ElMessage.success('状态已更新');
  await runLoad();
}

async function handleSetPrimary(row: CampusSiteDto) {
  if (row.isPrimary) return;
  try {
    await ElMessageBox.confirm(`确认将「${row.campusName}」设为主校区？`, '设为主校区', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await setPrimaryCampusSiteApi({ id: row.id! });
  ElMessage.success('已设为主校区');
  await runLoad();
}

onMounted(() => {
  void runLoad();
});
</script>

<template>
  <Page title="校区管理" description="维护多校区编码、名称与启用状态；可指定主校区。">
    <TableLayout
      v-model:query-params="query"
      :table-columns="columns"
      :table-data="list"
      :form-items="formItems"
      :loading="loading"
      :total="total"
      @pagination="handlePagination"
      @query="handleSearch"
      @reset="handleSearch"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新建</ElButton>
      </template>
      <template #isPrimary="{ row }">
        <ElTag v-if="row.isPrimary" type="warning">主校区</ElTag>
        <span v-else>—</span>
      </template>
      <template #activeFlag="{ row }">
        <ElTag :type="row.activeFlag ? 'success' : 'warning'">
          {{ row.activeFlag ? '启用' : '停用' }}
        </ElTag>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton link type="primary" @click="toggleActive(row)">
          {{ row.activeFlag ? '停用' : '启用' }}
        </ElButton>
        <ElButton v-if="!row.isPrimary" link type="primary" @click="handleSetPrimary(row)">
          设为主校区
        </ElButton>
      </template>
    </TableLayout>

    <ElDialog
      v-model="editVisible"
      :title="isCreate ? '新建校区' : '编辑校区'"
      width="520px"
      destroy-on-close
    >
      <ElForm label-width="90px">
        <ElFormItem label="编码" required>
          <ElInput v-model="editForm.campusCode" :disabled="!isCreate" placeholder="如 BENBU" />
        </ElFormItem>
        <ElFormItem label="名称" required>
          <ElInput v-model="editForm.campusName" />
        </ElFormItem>
        <ElFormItem label="简称">
          <ElInput v-model="editForm.shortName" />
        </ElFormItem>
        <ElFormItem label="地址">
          <ElInput v-model="editForm.address" />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber v-model="editForm.sortOrder" :min="0" class="w-full" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="editForm.remark" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
