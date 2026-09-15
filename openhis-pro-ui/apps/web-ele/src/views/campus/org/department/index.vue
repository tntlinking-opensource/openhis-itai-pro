<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { CampusCollegeDto } from '#/api/campus/campus-college';
import type { CampusDepartmentDto } from '#/api/campus/campus-department';
import type { CampusSiteDto } from '#/api/campus/campus-site';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { TableLayout } from '@vben-core/table-layout-ui';

import {
  changeDepartmentActiveApi,
  createDepartmentApi,
  pageDepartmentsApi,
  updateDepartmentApi,
} from '#/api/campus/campus-department';
import { listActiveCollegesApi } from '#/api/campus/campus-college';
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

defineOptions({ name: 'CampusDepartmentManage' });

const list = ref<CampusDepartmentDto[]>([]);
const total = ref(0);
const campusSites = ref<CampusSiteDto[]>([]);
const collegeOptions = ref<CampusCollegeDto[]>([]);
const editVisible = ref(false);
const editForm = ref<Record<string, unknown>>({});
const isCreate = ref(true);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  campusSiteId: undefined as string | undefined,
  collegeId: undefined as string | undefined,
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
    prop: 'collegeId',
    label: '学院',
    type: 'select',
    clearable: true,
    multiple: false,
    filterable: true,
    options: collegeOptions.value.map((c) => ({
      label: c.collegeName || c.collegeCode || '',
      value: toIdString(c.id),
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
  { prop: 'departmentName', label: '部门名称', minWidth: 140 },
  { prop: 'departmentCode', label: '编码', minWidth: 100 },
  { prop: 'deptTypeEnum', label: '类型', width: 100 },
  { prop: 'activeFlag', label: '状态', width: 90, slot: 'activeFlag' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

async function loadCampusSites() {
  const res = await listActiveCampusSitesApi();
  campusSites.value = unwrapRecords(res);
}

async function loadCollegesForFilter(campusSiteId?: string) {
  const site = campusSites.value.find((s) => toIdString(s.id) === campusSiteId);
  const res = await listActiveCollegesApi({ campusCode: site?.campusCode ?? null });
  collegeOptions.value = unwrapRecords(res);
}

async function loadEditColleges(campusSiteId: string) {
  const site = campusSites.value.find((s) => toIdString(s.id) === campusSiteId);
  const res = await listActiveCollegesApi({ campusCode: site?.campusCode ?? null });
  return unwrapRecords(res);
}

async function loadList() {
  const res = await pageDepartmentsApi({
    keyword: String(query.keyword || '').trim() || undefined,
    campusSiteId: query.campusSiteId || null,
    collegeId: query.collegeId || null,
    activeFlag: query.activeFlag ?? null,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
  const page = unwrapCampusPage(res);
  list.value = page.list;
  total.value = page.total;
}

const { run: runLoad, loading } = useRequest(loadList, { manual: true });

const editColleges = ref<CampusCollegeDto[]>([]);

watch(
  () => query.campusSiteId,
  (id) => {
    query.collegeId = undefined;
    void loadCollegesForFilter(id);
  },
);

watch(
  () => editForm.value.campusSiteId,
  async (id) => {
    if (id) {
      editColleges.value = await loadEditColleges(String(id));
    } else {
      editColleges.value = [];
    }
  },
);

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

function openEdit(row: CampusDepartmentDto) {
  isCreate.value = false;
  editForm.value = {
    ...row,
    campusSiteId: toIdString(row.campusSiteId),
    collegeId: row.collegeId ? toIdString(row.collegeId) : undefined,
  };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!String(form.departmentCode || '').trim() && isCreate.value) {
    ElMessage.warning('请填写部门编码');
    return;
  }
  if (!String(form.departmentName || '').trim()) {
    ElMessage.warning('请填写部门名称');
    return;
  }
  if (!form.campusSiteId) {
    ElMessage.warning('请选择所属校区');
    return;
  }
  const payload = {
    departmentName: String(form.departmentName).trim(),
    campusSiteId: toIdString(form.campusSiteId),
    collegeId: form.collegeId ? toIdString(form.collegeId) : null,
    deptTypeEnum: String(form.deptTypeEnum || '').trim() || undefined,
    sortOrder: Number(form.sortOrder ?? 0),
    remark: String(form.remark || '').trim() || undefined,
  };
  if (isCreate.value) {
    await createDepartmentApi({
      departmentCode: String(form.departmentCode).trim(),
      ...payload,
    });
  } else {
    await updateDepartmentApi({
      id: form.id as string,
      ...payload,
    });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

async function toggleActive(row: CampusDepartmentDto) {
  if (row.activeFlag) {
    try {
      await ElMessageBox.confirm(`确认停用部门「${row.departmentName}」？`, '停用部门', {
        type: 'warning',
      });
    } catch {
      return;
    }
    await changeDepartmentActiveApi({ id: row.id!, activeFlag: false });
  } else {
    await changeDepartmentActiveApi({ id: row.id!, activeFlag: true });
  }
  ElMessage.success('状态已更新');
  await runLoad();
}

onMounted(async () => {
  await loadCampusSites();
  await loadCollegesForFilter();
  await runLoad();
});
</script>

<template>
  <Page title="部门管理" description="维护教职工所在单位/人事部门；校区必填，学院可选。">
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
        <ElTag :type="row.activeFlag ? 'success' : 'info'">
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
      :title="isCreate ? '新建部门' : '编辑部门'"
      width="520px"
      destroy-on-close
    >
      <ElForm label-width="90px">
        <ElFormItem label="编码" required>
          <ElInput v-model="editForm.departmentCode" :disabled="!isCreate" />
        </ElFormItem>
        <ElFormItem label="名称" required>
          <ElInput v-model="editForm.departmentName" />
        </ElFormItem>
        <ElFormItem label="所属校区" required>
          <ElSelect v-model="editForm.campusSiteId" class="w-full" placeholder="请选择校区">
            <ElOption
              v-for="s in campusSites"
              :key="toIdString(s.id)"
              :label="s.campusName"
              :value="toIdString(s.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="所属学院">
          <ElSelect v-model="editForm.collegeId" clearable class="w-full" placeholder="可选">
            <ElOption
              v-for="c in editColleges"
              :key="toIdString(c.id)"
              :label="c.collegeName"
              :value="toIdString(c.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="部门类型">
          <ElInput v-model="editForm.deptTypeEnum" placeholder="如 admin / teaching" />
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
