<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  ArchiveChangeLogDto,
  ArchiveTransferDto,
  CampusStudentDto,
} from '#/api/campus/campus-student';
import type { CampusCollegeDto } from '#/api/campus/campus-college';
import type { CampusSiteDto } from '#/api/campus/campus-site';
import type { StudentDetailDto } from '#/api/campus/campus-student';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import { listActiveCollegesApi } from '#/api/campus/campus-college';
import { listActiveCampusSitesApi } from '#/api/campus/campus-site';
import {
  createStudentApi,
  disableStudentApi,
  getStudentDetailApi,
  pageStudentChangesApi,
  pageStudentsApi,
  pageStudentTransfersApi,
  saveStudentPeProfileApi,
  transferStudentApi,
  updateStudentApi,
} from '#/api/campus/campus-student';
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
  ElInputNumber,
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

defineOptions({ name: 'CampusStudentManage' });

const list = ref<CampusStudentDto[]>([]);
const total = ref(0);
const campusSites = ref<CampusSiteDto[]>([]);
const colleges = ref<CampusCollegeDto[]>([]);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  collegeId: undefined as string | undefined,
  campusCode: undefined as string | undefined,
  enrollTimeRange: undefined as string[] | undefined,
  activeFlag: undefined as boolean | undefined,
});

const formItems = computed<FormItemConfig[]>(() => [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '姓名/学号/身份证' },
  {
    prop: 'collegeId',
    label: '学院',
    type: 'select',
    clearable: true,
    multiple: false,
    filterable: true,
    options: colleges.value.map((c) => ({
      label: c.collegeName || '',
      value: toIdString(c.id),
    })),
  },
  {
    prop: 'campusCode',
    label: '校区',
    type: 'select',
    clearable: true,
    multiple: false,
    options: campusSites.value.map((s) => ({
      label: s.campusName || s.campusCode || '',
      value: s.campusCode || '',
    })),
  },
  {
    prop: 'enrollTimeRange',
    label: '入学时间',
    type: 'daterange',
    clearable: true,
    startPlaceholder: '开始',
    endPlaceholder: '结束',
    valueFormat: 'YYYY-MM-DD',
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
  { prop: 'studentNo', label: '学号', minWidth: 120 },
  { prop: 'collegeName', label: '学院', minWidth: 120 },
  { prop: 'campusCode', label: '校区', width: 90 },
  { prop: 'phone', label: '手机', minWidth: 110 },
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
const detail = ref<StudentDetailDto | null>(null);
const changes = ref<ArchiveChangeLogDto[]>([]);
const transfers = ref<ArchiveTransferDto[]>([]);
const peForm = ref<Record<string, unknown>>({});

async function loadOptions() {
  const [sitesRes, collegesRes] = await Promise.all([
    listActiveCampusSitesApi(),
    listActiveCollegesApi(),
  ]);
  campusSites.value = unwrapRecords(sitesRes);
  colleges.value = unwrapRecords(collegesRes);
}

async function loadList() {
  const res = await pageStudentsApi({
    keyword: String(query.keyword || '').trim() || undefined,
    collegeId: query.collegeId || null,
    campusCode: query.campusCode || null,
    activeFlag: query.activeFlag ?? null,
    enrollTimeFrom: query.enrollTimeRange?.[0] || null,
    enrollTimeTo: query.enrollTimeRange?.[1] || null,
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

function openEdit(row: CampusStudentDto) {
  isCreate.value = false;
  editForm.value = {
    ...row,
    collegeId: toIdString(row.collegeId),
    enrollTime: row.enrollTime ? String(row.enrollTime).slice(0, 10) : undefined,
  };
  editVisible.value = true;
}

function toEnrollTimePayload(value: unknown): string | undefined {
  const raw = String(value || '').trim();
  if (!raw) return undefined;
  if (raw.length === 10) return `${raw}T00:00:00`;
  return raw;
}

async function handleSave() {
  const form = editForm.value;
  if (!String(form.name || '').trim()) {
    ElMessage.warning('请填写姓名');
    return;
  }
  if (!String(form.studentNo || '').trim()) {
    ElMessage.warning('请填写学号');
    return;
  }
  if (!form.collegeId) {
    ElMessage.warning('请选择学院');
    return;
  }
  if (!String(form.campusCode || '').trim()) {
    ElMessage.warning('请选择校区');
    return;
  }
  const studyYearsRaw = form.studyYears;
  const studyYears =
    studyYearsRaw === null || studyYearsRaw === undefined || studyYearsRaw === ''
      ? undefined
      : Number(studyYearsRaw);
  const base = {
    name: String(form.name).trim(),
    studentNo: String(form.studentNo).trim(),
    collegeId: toIdString(form.collegeId),
    campusCode: String(form.campusCode).trim(),
    idCard: String(form.idCard || '').trim() || undefined,
    phone: String(form.phone || '').trim() || undefined,
    genderEnum: String(form.genderEnum || '').trim() || undefined,
    grade: String(form.grade || '').trim() || undefined,
    className: String(form.className || '').trim() || undefined,
    major: String(form.major || '').trim() || undefined,
    studyYears: Number.isFinite(studyYears) ? studyYears : undefined,
    enrollTime: toEnrollTimePayload(form.enrollTime),
    activeFlag: form.activeFlag !== false,
  };
  if (isCreate.value) {
    await createStudentApi(base);
  } else {
    await updateStudentApi({ id: form.id as string, ...base });
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  query.pageNo = 1;
  await runLoad();
}

async function toggleActive(row: CampusStudentDto) {
  if (row.activeFlag) {
    try {
      await ElMessageBox.confirm(`确认停用学生「${row.name}」？`, '停用档案', { type: 'warning' });
    } catch {
      return;
    }
    await disableStudentApi({ id: row.id! });
  } else {
    await updateStudentApi({
      id: row.id!,
      name: row.name!,
      studentNo: row.studentNo!,
      collegeId: row.collegeId!,
      campusCode: row.campusCode!,
      activeFlag: true,
    });
  }
  ElMessage.success('状态已更新');
  await runLoad();
}

function openTransfer(row: CampusStudentDto) {
  transferForm.value = {
    studentId: row.id,
    toCampusCode: row.campusCode,
    toCollegeId: toIdString(row.collegeId),
  };
  transferVisible.value = true;
}

async function handleTransfer() {
  const form = transferForm.value;
  if (!form.toCollegeId || !form.toCampusCode) {
    ElMessage.warning('请填写目标学院与校区');
    return;
  }
  await transferStudentApi({
    studentId: form.studentId as string,
    toCollegeId: toIdString(form.toCollegeId),
    toCampusCode: String(form.toCampusCode),
    effectiveDate: form.effectiveDate as string | undefined,
    reason: String(form.reason || '').trim() || undefined,
  });
  ElMessage.success('转院已登记');
  transferVisible.value = false;
  await runLoad();
}

async function loadDetailTabs(studentId: string) {
  const [changesRes, transfersRes] = await Promise.all([
    pageStudentChangesApi({ studentId, pageNo: 1, pageSize: 50 }),
    pageStudentTransfersApi({ studentId, pageNo: 1, pageSize: 50 }),
  ]);
  changes.value = unwrapCampusPage(changesRes).list;
  transfers.value = unwrapCampusPage(transfersRes).list;
}

async function openDetail(row: CampusStudentDto) {
  detailTab.value = 'basic';
  const res = await getStudentDetailApi({ id: row.id! });
  detail.value = res;
  peForm.value = { ...(res.peProfile || {}), studentId: row.id };
  drawerVisible.value = true;
  await loadDetailTabs(toIdString(row.id));
}

async function savePeProfile() {
  if (!peForm.value.studentId) return;
  await saveStudentPeProfileApi({
    studentId: toIdString(peForm.value.studentId),
    cardNo: String(peForm.value.cardNo || '').trim() || undefined,
    medicalCode: String(peForm.value.medicalCode || '').trim() || undefined,
    gfStatusEnum: String(peForm.value.gfStatusEnum || '').trim() || undefined,
    categoryEnum: String(peForm.value.categoryEnum || '').trim() || undefined,
    eligibleFlag: Boolean(peForm.value.eligibleFlag),
    remark: String(peForm.value.remark || '').trim() || undefined,
  });
  ElMessage.success('公费资格已保存');
  if (detail.value?.student?.id) {
    detail.value = await getStudentDetailApi({ id: detail.value.student.id });
    peForm.value = { ...(detail.value.peProfile || {}), studentId: detail.value.student.id };
  }
}

onMounted(async () => {
  await loadOptions();
  await runLoad();
});
</script>

<template>
  <Page title="学生档案" description="学籍与公费资格维护；支持转院登记与变更/调动记录查看。">
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
        <ElButton link type="primary" @click="openTransfer(row)">转院</ElButton>
      </template>
    </TableLayout>

    <ElDialog
      v-model="editVisible"
      :title="isCreate ? '新建学生' : '编辑学生'"
      width="640px"
      destroy-on-close
    >
      <ElForm label-width="90px">
        <ElFormItem label="姓名" required>
          <ElInput v-model="editForm.name" />
        </ElFormItem>
        <ElFormItem label="学号" required>
          <ElInput v-model="editForm.studentNo" :disabled="!isCreate" />
        </ElFormItem>
        <ElFormItem label="性别">
          <ElSelect v-model="editForm.genderEnum" class="w-full" clearable>
            <ElOption label="男" value="MALE" />
            <ElOption label="女" value="FEMALE" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="学院" required>
          <ElSelect v-model="editForm.collegeId" class="w-full" filterable>
            <ElOption
              v-for="c in colleges"
              :key="toIdString(c.id)"
              :label="c.collegeName"
              :value="toIdString(c.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="校区" required>
          <ElSelect v-model="editForm.campusCode" class="w-full">
            <ElOption
              v-for="s in campusSites"
              :key="s.campusCode"
              :label="s.campusName"
              :value="s.campusCode"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="班级">
          <ElInput v-model="editForm.className" placeholder="行政班，如 计科2401" />
        </ElFormItem>
        <ElFormItem label="身份证">
          <ElInput v-model="editForm.idCard" />
        </ElFormItem>
        <ElFormItem label="手机">
          <ElInput v-model="editForm.phone" />
        </ElFormItem>
        <ElFormItem label="年级">
          <ElInput v-model="editForm.grade" />
        </ElFormItem>
        <ElFormItem label="专业">
          <ElInput v-model="editForm.major" />
        </ElFormItem>
        <ElFormItem label="学制">
          <ElInputNumber
            v-model="editForm.studyYears"
            :min="1"
            :max="10"
            :step="1"
            controls-position="right"
            class="w-full"
            placeholder="年，如 4"
          />
        </ElFormItem>
        <ElFormItem label="入学时间">
          <ElDatePicker
            v-model="editForm.enrollTime"
            type="date"
            value-format="YYYY-MM-DD"
            class="w-full"
            placeholder="选择入学日期"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="transferVisible" title="学生转院" width="480px" destroy-on-close>
      <ElForm label-width="90px">
        <ElFormItem label="目标学院" required>
          <ElSelect v-model="transferForm.toCollegeId" class="w-full" filterable>
            <ElOption
              v-for="c in colleges"
              :key="toIdString(c.id)"
              :label="c.collegeName"
              :value="toIdString(c.id)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="目标校区" required>
          <ElSelect v-model="transferForm.toCampusCode" class="w-full">
            <ElOption
              v-for="s in campusSites"
              :key="s.campusCode"
              :label="s.campusName"
              :value="s.campusCode"
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

    <ElDrawer v-model="drawerVisible" title="学生详情" size="720px" destroy-on-close>
      <ElTabs v-model="detailTab">
        <ElTabPane label="基本信息" name="basic">
          <ElDescriptions v-if="detail?.student" :column="2" border size="small" class="mb-3">
            <ElDescriptionsItem label="姓名">{{ detail.student.name }}</ElDescriptionsItem>
            <ElDescriptionsItem label="学号">{{ detail.student.studentNo }}</ElDescriptionsItem>
            <ElDescriptionsItem label="性别">{{
              detail.student.genderEnum === 'MALE'
                ? '男'
                : detail.student.genderEnum === 'FEMALE'
                  ? '女'
                  : detail.student.genderEnum || '—'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="学院">{{ detail.student.collegeName }}</ElDescriptionsItem>
            <ElDescriptionsItem label="校区">{{ detail.student.campusCode }}</ElDescriptionsItem>
            <ElDescriptionsItem label="班级">{{
              detail.student.className || '—'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="手机">{{ detail.student.phone || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="身份证">{{
              detail.student.idCard || '—'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="年级">{{ detail.student.grade || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="专业">{{ detail.student.major || '—' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="学制">{{
              detail.student.studyYears != null ? `${detail.student.studyYears} 年` : '—'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="入学时间">{{
              detail.student.enrollTime ? String(detail.student.enrollTime).slice(0, 10) : '—'
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
              <ElInput v-model="peForm.gfStatusEnum" placeholder="如 ELIGIBLE" />
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
            <ElTableColumn prop="fromCampusCode" label="原校区" width="90" />
            <ElTableColumn prop="toCampusCode" label="新校区" width="90" />
            <ElTableColumn prop="reason" label="原因" min-width="120" />
            <ElTableColumn prop="operatorName" label="操作人" width="100" />
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElDrawer>
  </Page>
</template>
