<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { CampusCollegeDto } from '#/api/campus/campus-college';
import type { CampusSiteDto } from '#/api/campus/campus-site';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  changeCollegeActiveApi,
  createCollegeApi,
  pageCollegesApi,
  updateCollegeApi,
} from '#/api/campus/campus-college';
import { listActiveCampusSitesApi } from '#/api/campus/campus-site';
import { toIdString, unwrapCampusPage, unwrapRecords } from '#/api/campus/types';
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

defineOptions({ name: 'CampusCollegeManage' });

const list = ref<CampusCollegeDto[]>([]);
const total = ref(0);
const campusSites = ref<CampusSiteDto[]>([]);
const editVisible = ref(false);
const editForm = ref<Record<string, unknown>>({});
const isCreate = ref(true);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  campusSiteId: undefined as string | undefined,
  activeFlag: undefined as boolean | undefined,
});

const formItems = computed<FormItemConfig[]>(() => [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
  {
    prop: 'campusSiteId',
    label: '校区',
    type: 'select',
    clearable: true,
    multiple: false,
    options: campusSites.value.map((s) => ({
      label: s.campusName || s.campusCode || '',
      value: toIdString(s.id),
    })),
  },
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
]);

const columns: TableColumn[] = [
  { prop: 'collegeName', label: '学院名称', minWidth: 140 },
  { prop: 'collegeCode', label: '编码', minWidth: 100 },
  { prop: 'campusName', label: '所属校区', minWidth: 120 },
  { prop: 'activeFlag', label: '状态', width: 90, slot: 'activeFlag' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

async function loadCampusSites() {
  const res = await listActiveCampusSitesApi();
  campusSites.value = unwrapRecords(res);
}

async function loadList() {
  const res = await pageCollegesApi({
    keyword: String(query.keyword || '').trim() || undefined,
    campusSiteId: query.campusSiteId || null,
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

function openEdit(row: CampusCollegeDto) {
  isCreate.value = false;
  editForm.value = {
    ...row,
    campusSiteId: row.campusSiteId ? toIdString(row.campusSiteId) : undefined,
  };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!String(form.collegeCode || '').trim() && isCreate.value) {
    ElMessage.warning('请填写学院编码');
    return;
  }
  if (!String(form.collegeName || '').trim()) {
    ElMessage.warning('请填写学院名称');
    return;
  }
  const campusSiteId = form.campusSiteId ? toIdString(form.campusSiteId) : null;
  if (isCreate.value) {
    await createCollegeApi({
      collegeCode: String(form.collegeCode).trim(),
      collegeName: String(form.collegeName).trim(),
      campusSiteId,
      sortOrder: Number(form.sortOrder ?? 0),
      remark: String(form.remark || '').trim() || undefined,
    });
  } else {
    await updateCollegeApi({
      id: form.id as string,
      collegeName: String(form.collegeName).trim(),
      campusSiteId,
      sortOrder: Number(form.sortOrder ?? 0),
      remark: String(form.remark || '').trim() || undefined,
    });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

async function toggleActive(row: CampusCollegeDto) {
  if (row.activeFlag) {
    try {
      await ElMessageBox.confirm(`确认停用学院「${row.collegeName}」？`, '停用学院', {
        type: 'warning',
      });
    } catch {
      return;
    }
    await changeCollegeActiveApi({ id: row.id!, activeFlag: false });
  } else {
    await changeCollegeActiveApi({ id: row.id!, activeFlag: true });
  }
  ElMessage.success('状态已更新');
  await runLoad();
}

onMounted(async () => {
  await loadCampusSites();
  await runLoad();
});
</script>

<template>
  <Page title="学院管理" description="维护教学院系/部，可绑定所属校区。">
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
      </template>
    </TableLayout>

    <ElDialog
      v-model="editVisible"
      :title="isCreate ? '新建学院' : '编辑学院'"
      width="520px"
      destroy-on-close
    >
      <ElForm label-width="90px">
        <ElFormItem label="编码" required>
          <ElInput v-model="editForm.collegeCode" :disabled="!isCreate" />
        </ElFormItem>
        <ElFormItem label="名称" required>
          <ElInput v-model="editForm.collegeName" />
        </ElFormItem>
        <ElFormItem label="所属校区">
          <ElSelect
            v-model="editForm.campusSiteId"
            clearable
            class="w-full"
            placeholder="可空=全校"
          >
            <ElOption
              v-for="s in campusSites"
              :key="toIdString(s.id)"
              :label="s.campusName"
              :value="toIdString(s.id)"
            />
          </ElSelect>
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
