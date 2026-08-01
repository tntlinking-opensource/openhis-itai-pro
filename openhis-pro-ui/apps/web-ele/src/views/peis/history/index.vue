<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { listPatientExamHistoryApi, type ExamHistoryRowDto } from '#/api/peis/exam-history';
import {
  checkArchiveCompletenessApi,
  type ArchiveCompletenessDto,
} from '#/api/peis/health-archive';

defineOptions({ name: 'PeisExamHistory' });

const EXAM_KIND_LABEL: Record<string, string> = {
  freshman: '新生体检',
  annual: '在校年检',
  staff: '教职工体检',
  other: '其他',
};

const route = useRoute();
const mode = ref<'student' | 'staff'>('student');
const studentNo = ref('');
const employeeNo = ref('');
const patientId = ref('');
const rows = ref<ExamHistoryRowDto[]>([]);
const subjectName = ref('');
const subjectType = ref('');
const completeness = ref<ArchiveCompletenessDto | null>(null);
const loading = ref(false);

function kindLabel(kind?: string) {
  if (!kind) return '—';
  return EXAM_KIND_LABEL[kind] || kind;
}

function levelType(level?: string) {
  if (level === 'block') return 'error';
  if (level === 'warn') return 'warning';
  if (level === 'info') return 'info';
  return 'success';
}

async function searchByForm() {
  patientId.value = '';
  await search();
}

async function search() {
  loading.value = true;
  completeness.value = null;
  try {
    let req: { patientId?: string; studentNo?: string; employeeNo?: string };
    if (patientId.value.trim()) {
      req = { patientId: patientId.value.trim() };
    } else if (mode.value === 'student') {
      if (!studentNo.value.trim()) {
        ElMessage.warning('请输入学号');
        return;
      }
      req = { studentNo: studentNo.value.trim() };
    } else {
      if (!employeeNo.value.trim()) {
        ElMessage.warning('请输入工号');
        return;
      }
      req = { employeeNo: employeeNo.value.trim() };
    }
    const res = await listPatientExamHistoryApi(req);
    rows.value = res.rows || [];
    subjectName.value = res.subjectName || '';
    subjectType.value = res.subjectType || '';
    if (res.patientId != null) {
      patientId.value = String(res.patientId);
    }
    if (mode.value === 'student' || res.subjectType === 'STUDENT') {
      const check = await checkArchiveCompletenessApi({
        patientId: res.patientId,
        studentNo: res.studentNo || studentNo.value.trim() || undefined,
      });
      completeness.value = check.completeness || null;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  const qPatientId = String(route.query.patientId ?? '').trim();
  const qStudentNo = String(route.query.studentNo ?? '').trim();
  const qEmployeeNo = String(route.query.employeeNo ?? '').trim();
  if (qPatientId) {
    patientId.value = qPatientId;
    void search();
  } else if (qStudentNo) {
    mode.value = 'student';
    studentNo.value = qStudentNo;
    void search();
  } else if (qEmployeeNo) {
    mode.value = 'staff';
    employeeNo.value = qEmployeeNo;
    void search();
  }
});
</script>

<template>
  <Page
    auto-content-height
    content-class="p-4"
    title="体检历史"
    description="按学号查看学生历年体检，按工号查看教职工历年体检；学生同时提示档案是否完整"
  >
    <ElForm inline class="mb-4">
      <ElFormItem label="对象">
        <ElRadioGroup v-model="mode">
          <ElRadioButton value="student">学生</ElRadioButton>
          <ElRadioButton value="staff">教职工</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem v-if="mode === 'student'" label="学号">
        <ElInput v-model="studentNo" placeholder="输入学号" clearable @keyup.enter="searchByForm" />
      </ElFormItem>
      <ElFormItem v-else label="工号">
        <ElInput
          v-model="employeeNo"
          placeholder="输入工号"
          clearable
          @keyup.enter="searchByForm"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="loading" @click="searchByForm">查询</ElButton>
      </ElFormItem>
    </ElForm>

    <div v-if="subjectName || subjectType" class="mb-3 text-sm text-gray-600">
      {{ subjectName || '—' }}
      <ElTag v-if="subjectType" size="small" class="ml-2">{{ subjectType }}</ElTag>
    </div>

    <ElAlert
      v-if="completeness"
      class="mb-4"
      :type="levelType(completeness.level)"
      :title="completeness.summary || (completeness.complete ? '档案完整' : '档案不完整')"
      show-icon
      :closable="false"
    >
      <ul v-if="completeness.missingItems?.length" class="mt-1 list-disc pl-5">
        <li v-for="item in completeness.missingItems" :key="item.code">
          <ElTag size="small" :type="levelType(item.level)" class="mr-1">{{ item.level }}</ElTag>
          {{ item.message }}
        </li>
      </ul>
    </ElAlert>

    <ElTable :data="rows" border v-loading="loading" empty-text="暂无体检记录">
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn prop="examKind" label="体检类型" width="120">
        <template #default="{ row }">
          {{ kindLabel(row.examKind) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="packageName" label="套餐" min-width="140" />
      <ElTableColumn prop="calendarYear" label="年份" width="80" />
      <ElTableColumn prop="statusEnum" label="状态" width="100" />
      <ElTableColumn prop="createdAt" label="体检时间" min-width="160" />
      <ElTableColumn prop="finalReport" label="总检定稿" width="90">
        <template #default="{ row }">
          <ElTag :type="row.finalReport ? 'success' : 'info'" size="small">
            {{ row.finalReport ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
    </ElTable>
  </Page>
</template>
