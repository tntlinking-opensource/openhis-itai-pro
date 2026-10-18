<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { ArchiveChangeLogDto, ArchiveTransferDto } from '#/api/campus/campus-student';
import type { CampusDepartmentDto } from '#/api/campus/campus-department';
import type { CampusSiteDto } from '#/api/campus/campus-site';
import type { CampusStaffDto, StaffDetailDto } from '#/api/campus/campus-staff';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import { listDepartmentsApi } from '#/api/campus/campus-department';
import { listActiveCampusSitesApi } from '#/api/campus/campus-site';
import {
  createStaffApi,
  disableStaffApi,
  getStaffDetailApi,
  pageStaffApi,
  pageStaffChangesApi,
  pageStaffTransfersApi,
  saveStaffPeProfileApi,
  transferStaffApi,
  updateStaffApi,
} from '#/api/campus/campus-staff';
import { toIdString, unwrapCampusPage, unwrapRecords } from '#/api/campus/types';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'CampusStaffManage' });

const list = ref<CampusStaffDto[]>([]);
const total = ref(0);
const campusSites = ref<CampusSiteDto[]>([]);
const departments = ref<CampusDepartmentDto[]>([]);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  departmentId: undefined as string | undefined,
  campusSiteId: undefined as string | undefined,
  activeFlag: undefined as boolean | undefined,
});

const formItems = computed<FormItemConfig[]>(() => [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '姓名/工号/身份证' },
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
    onChange: (value) => {
      query.departmentId = undefined;
      void reloadDepartments(value ? String(value) : undefined);
    },
  },
  {
    prop: 'departmentId',
    label: '部门',
    type: 'select',
    clearable: true,
    multiple: false,
    filterable: true,
    options: departments.value.map((d) => ({
      label: d.departmentName || '',
      value: toIdString(d.id),
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
  { prop: 'name', label: '姓名', minWidth: 100 },
  { prop: 'employeeNo', label: '工号', minWidth: 120 },
  { prop: 'phone', label: '手机', minWidth: 110 },
  { prop: 'medicalCode', label: '医疗证', minWidth: 110 },
  { prop: 'activeFlag', label: '状态', width: 90, slot: 'activeFlag' },
  { prop: 'actions', label: '操作', width: 200, slot: 'actions' },
];

const editVisible = ref(false);
const transferVisible = ref(false);
const isCreate = ref(true);
const editForm = ref<Record<string, unknown>>({});
const transferForm = ref<Record<string, unknown>>({});

const drawerVisible = ref(false);
const detailTab = ref('basic');
const detail = ref<StaffDetailDto | null>(null);
const changes = ref<ArchiveChangeLogDto[]>([]);
const transfers = ref<ArchiveTransferDto[]>([]);
const peForm = ref<Record<string, unknown>>({});

async function loadOptions() {
  const sitesRes = await listActiveCampusSitesApi();
  campusSites.value = unwrapRecords(sitesRes);
  await reloadDepartments();
}

async function reloadDepartments(campusSiteId?: string) {
  const res = await listDepartmentsApi({
    campusSiteId: campusSiteId || null,
    activeOnly: true,
  });
  departments.value = unwrapRecords(res);
}

async function loadList() {
  const res = await pageStaffApi({
    keyword: String(query.keyword || '').trim() || undefined,
    departmentId: query.departmentId || null,
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
  editForm.value = { activeFlag: true };
  editVisible.value = true;
}

function openEdit(row: CampusStaffDto) {
  isCreate.value = false;
  editForm.value = {
    ...row,
    departmentId: toIdString(row.departmentId),
    campusSiteId: toIdString(row.campusSiteId),
  };
  editVisible.value = true;
}

async function handleSave() {
  const form = editForm.value;
  if (!String(form.name || '').trim()) {
    ElMessage.warning('请填写姓名');
    return;
  }
  if (!String(form.employeeNo || '').trim()) {
    ElMessage.warning('请填写工号');
    return;
  }
  if (!form.departmentId || !form.campusSiteId) {
    ElMessage.warning('请选择部门与校区');
    return;
  }
  const base = {
    name: String(form.name).trim(),
    employeeNo: String(form.employeeNo).trim(),
    departmentId: toIdString(form.departmentId),
    campusSiteId: toIdString(form.campusSiteId),
    idCard: String(form.idCard || '').trim() || undefined,
    phone: String(form.phone || '').trim() || undefined,
    medicalCode: String(form.medicalCode || '').trim() || undefined,
    activeFlag: form.activeFlag !== false,
  };
  if (isCreate.value) {
    await createStaffApi(base);
  } else {
    await updateStaffApi({ id: form.id as string, ...base });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

async function toggleActive(row: CampusStaffDto) {
  if (row.activeFlag) {
    try {
      await ElMessageBox.confirm(`确认停用教职工「${row.name}」？`, '停用档案', {
        type: 'warning',
      });
    } catch {
      return;
    }
    await disableStaffApi({ id: row.id! });
  } else {
    await updateStaffApi({
      id: row.id!,
      name: row.name!,
      employeeNo: row.employeeNo!,
      departmentId: row.departmentId!,
      campusSiteId: row.campusSiteId!,
      activeFlag: true,
    });
  }
  ElMessage.success('状态已更新');
  await runLoad();
}

function openTransfer(row: CampusStaffDto) {
  transferForm.value = {
    staffId: row.id,
    toDepartmentId: toIdString(row.departmentId),
    toCampusSiteId: toIdString(row.campusSiteId),
  };
  transferVisible.value = true;
}

async function handleTransfer() {
  const form = transferForm.value;
  if (!form.toDepartmentId || !form.toCampusSiteId) {
    ElMessage.warning('请填写目标部门与校区');
    return;
  }
  await transferStaffApi({
    staffId: form.staffId as string,
    toDepartmentId: toIdString(form.toDepartmentId),
    toCampusSiteId: toIdString(form.toCampusSiteId),
    effectiveDate: form.effectiveDate as string | undefined,
    reason: String(form.reason || '').trim() || undefined,
  });
  ElMessage.success('调岗已登记');
  transferVisible.value = false;
  await runLoad();
}

async function loadDetailTabs(staffId: string) {
  const [changesRes, transfersRes] = await Promise.all([
    pageStaffChangesApi({ staffId, pageNo: 1, pageSize: 50 }),
    pageStaffTransfersApi({ staffId, pageNo: 1, pageSize: 50 }),
  ]);
  changes.value = unwrapCampusPage(changesRes).list;
  transfers.value = unwrapCampusPage(transfersRes).list;
}

async function openDetail(row: CampusStaffDto) {
  detailTab.value = 'basic';
  const res = await getStaffDetailApi({ id: row.id! });
  detail.value = res;
  peForm.value = { ...(res.peProfile || {}), staffId: row.id };
  drawerVisible.value = true;
  await loadDetailTabs(toIdString(row.id));
}

async function savePeProfile() {
  if (!peForm.value.staffId) return;
  await saveStaffPeProfileApi({
    staffId: toIdString(peForm.value.staffId),
    cardNo: String(peForm.value.cardNo || '').trim() || undefined,
    medicalCode: String(peForm.value.medicalCode || '').trim() || undefined,
    gfStatusEnum: String(peForm.value.gfStatusEnum || '').trim() || undefined,
    categoryEnum: String(peForm.value.categoryEnum || '').trim() || undefined,
    eligibleFlag: Boolean(peForm.value.eligibleFlag),
    remark: String(peForm.value.remark || '').trim() || undefined,
  });
  ElMessage.success('公费资格已保存');
  if (detail.value?.staff?.id) {
    detail.value = await getStaffDetailApi({ id: detail.value.staff.id });
    peForm.value = { ...(detail.value.peProfile || {}), staffId: detail.value.staff.id };
  }
}

onMounted(async () => {
  await loadOptions();
  await runLoad();
});
</script>

<template>
  <Page title="教职工档案" description="教职工名册与公费资格；支持调岗登记与变更/调动记录查看。">
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
        <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton link type="primary" @click="toggleActive(row)">
          {{ row.activeFlag ? '停用' : '启用' }}
        </ElButton>
        <ElButton link type="primary" @click="openTransfer(row)">调岗</ElButton>
      </template>
    </TableLayout>

    <ElDialog
      v-model="editVisible"
      :title="isCreate ? '新建教职工' : '编辑教职工'"
      width="560px"
      destroy-on-close
    >
      <ElForm label-width="90px">
        <ElFormItem label="姓名" required>
          <ElInput v-model="editForm.name" />
        </ElFormItem>
        <ElFormItem label="工号" required>
          <ElInput v-model="editForm.employeeNo" :disabled="!isCreate" />
        </ElFormItem>
        <ElFormItem label="校区" required>
          <ElSelect
            v-model="editForm.campusSiteId"
            class="w-full"
            @change="reloadDepartments(String(editForm.campusSiteId || ''))"
          >
            <ElOption
              v-for="s in campusSites"
              :key="toIdString(s.id)"
              :label="s.campusName"
              :value="toIdString(s.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="部门" required>
          <ElSelect v-model="editForm.departmentId" class="w-full" filterable>
            <ElOption
              v-for="d in departments"
              :key="toIdString(d.id)"
              :label="d.departmentName"
              :value="toIdString(d.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="身份证">
          <ElInput v-model="editForm.idCard" />
        </ElFormItem>
        <ElFormItem label="手机">
          <ElInput v-model="editForm.phone" />
        </ElFormItem>
        <ElFormItem label="医疗证">
          <ElInput v-model="editForm.medicalCode" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="transferVisible" title="教职工调岗" width="480px" destroy-on-close>
      <ElForm label-width="90px">
        <ElFormItem label="目标校区" required>
          <ElSelect
            v-model="transferForm.toCampusSiteId"
            class="w-full"
            @change="reloadDepartments(String(transferForm.toCampusSiteId || ''))"
          >
            <ElOption
              v-for="s in campusSites"
              :key="toIdString(s.id)"
              :label="s.campusName"
              :value="toIdString(s.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="目标部门" required>
          <ElSelect v-model="transferForm.toDepartmentId" class="w-full" filterable>
            <ElOption
              v-for="d in departments"
              :key="toIdString(d.id)"
              :label="d.departmentName"
              :value="toIdString(d.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="生效日期">
          <ElDatePicker
            v-model="transferForm.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            class="w-full"
          />
        </ElFormItem>
        <ElFormItem label="原因">
          <ElInput v-model="transferForm.reason" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="transferVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleTransfer">提交</ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="drawerVisible" title="教职工详情" size="720px" destroy-on-close>
      <ElTabs v-model="detailTab">
        <ElTabPane label="基本信息" name="basic">
          <ElDescriptions v-if="detail?.staff" :column="2" border size="small" class="mb-3">
            <ElDescriptionsItem label="姓名">{{ detail.staff.name }}</ElDescriptionsItem>
            <ElDescriptionsItem label="工号">{{ detail.staff.employeeNo }}</ElDescriptionsItem>
            <ElDescriptionsItem label="手机">{{ detail.staff.phone || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="医疗证">{{
              detail.staff.medicalCode || '—'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="身份证">{{ detail.staff.idCard || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="在职状态">{{
              detail.staff.jobStatusEnum || '—'
            }}</ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>
        <ElTabPane label="公费资格" name="pe">
          <ElForm label-width="100px" class="max-w-lg">
            <ElFormItem label="公费卡号">
              <ElInput v-model="peForm.cardNo" />
            </ElFormItem>
            <ElFormItem label="医疗证号">
              <ElInput v-model="peForm.medicalCode" />
            </ElFormItem>
            <ElFormItem label="公费状态">
              <ElInput v-model="peForm.gfStatusEnum" />
            </ElFormItem>
            <ElFormItem label="类别">
              <ElInput v-model="peForm.categoryEnum" />
            </ElFormItem>
            <ElFormItem label="符合资格">
              <ElSwitch v-model="peForm.eligibleFlag" />
            </ElFormItem>
            <ElFormItem label="备注">
              <ElInput v-model="peForm.remark" type="textarea" :rows="2" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="savePeProfile">保存公费资格</ElButton>
            </ElFormItem>
          </ElForm>
        </ElTabPane>
        <ElTabPane label="变更记录" name="changes">
          <ElTable :data="changes" size="small" border>
            <ElTableColumn prop="changeTime" label="时间" width="160" />
            <ElTableColumn prop="fieldName" label="字段" width="100" />
            <ElTableColumn prop="oldValue" label="改前" min-width="100" />
            <ElTableColumn prop="newValue" label="改后" min-width="100" />
            <ElTableColumn prop="operatorName" label="操作人" width="100" />
          </ElTable>
        </ElTabPane>
        <ElTabPane label="调动记录" name="transfers">
          <ElTable :data="transfers" size="small" border>
            <ElTableColumn prop="effectiveDate" label="生效日" width="110" />
            <ElTableColumn prop="reason" label="原因" min-width="120" />
            <ElTableColumn prop="operatorName" label="操作人" width="100" />
            <ElTableColumn prop="operateTime" label="操作时间" width="160" />
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElDrawer>
  </Page>
</template>
