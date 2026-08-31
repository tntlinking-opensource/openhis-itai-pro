<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  EXAM_KIND_LABEL,
  cancelPhysicalExamApi,
  listPatientExamHistoryApi,
  type ExamHistoryRowDto,
} from '#/api/peis/exam-history';
import {
  checkArchiveCompletenessApi,
  type ArchiveCompletenessDto,
} from '#/api/peis/health-archive';
import {
  getCheckOrderDetailApi,
  searchCustomerApi,
  type CheckOrderDetailResponse,
  type CustomerRowDto,
} from '#/api/peis/personal-registration';
import { extractRequestErrorMessage } from '#/utils/http/stale-cross-role-error';

defineOptions({ name: 'PeisExamHistory' });

type SubjectMode = 'patient' | 'student' | 'staff';
/** 全部 / 个人体检 / 团体体检 */
type KindScope = 'all' | 'personal' | 'group';

const route = useRoute();
const mode = ref<SubjectMode>('patient');
const kindScope = ref<KindScope>('all');
const customerKeyword = ref('');
const studentNo = ref('');
const employeeNo = ref('');
const patientId = ref('');
const customerRows = ref<CustomerRowDto[]>([]);
const selectedCustomer = ref<CustomerRowDto | null>(null);
const customerLoading = ref(false);
const rows = ref<ExamHistoryRowDto[]>([]);
const subjectName = ref('');
const subjectType = ref('');
const completeness = ref<ArchiveCompletenessDto | null>(null);
const loading = ref(false);
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailRow = ref<ExamHistoryRowDto | null>(null);
const orderDetail = ref<CheckOrderDetailResponse | null>(null);
const cancelLoadingId = ref('');

const execStatusLabel: Record<string, string> = {
  pending: '待检',
  checked: '已检',
  abandoned: '弃检',
  deferred: '延检',
};

const displayRows = computed(() => {
  if (kindScope.value === 'all') return rows.value;
  if (kindScope.value === 'group') {
    return rows.value.filter((r) => r.groupFlag === true || r.examKind === 'group');
  }
  return rows.value.filter((r) => r.groupFlag !== true && r.examKind !== 'group');
});

function kindLabel(kind?: string) {
  if (!kind) return '—';
  return EXAM_KIND_LABEL[kind] || kind;
}

function subjectTypeLabel(type?: string) {
  if (!type) return '';
  if (type === 'STUDENT') return '学生';
  if (type === 'STAFF') return '教职工';
  if (type === 'PATIENT') return '客户';
  return type;
}

function levelType(level?: string) {
  if (level === 'block') return 'error';
  if (level === 'warn') return 'warning';
  if (level === 'info') return 'info';
  return 'success';
}

function isGroupRow(row: ExamHistoryRowDto) {
  return row.groupFlag === true || row.examKind === 'group';
}

function labelExec(code?: string) {
  if (!code) return '待检';
  return execStatusLabel[code] || code;
}

function execTagType(code?: string) {
  if (code === 'checked') return 'success';
  if (code === 'abandoned') return 'danger';
  if (code === 'deferred') return 'warning';
  return 'info';
}

function encounterKey(row?: ExamHistoryRowDto | null) {
  return row?.encounterId == null ? '' : String(row.encounterId);
}

async function openDetail(row: ExamHistoryRowDto) {
  const encounterId = encounterKey(row);
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识');
    return;
  }
  detailRow.value = row;
  orderDetail.value = null;
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    orderDetail.value = await getCheckOrderDetailApi({ encounterId });
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error) || '加载体检单失败');
  } finally {
    detailLoading.value = false;
  }
}

async function cancelExam(row?: ExamHistoryRowDto | null) {
  const encounterId = encounterKey(row);
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确认取消该次体检？取消后记录不再展示，未结算费用将一并作废。',
      '取消体检',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  cancelLoadingId.value = encounterId;
  try {
    await cancelPhysicalExamApi({ encounterId });
    ElMessage.success('已取消');
    if (detailVisible.value && encounterKey(detailRow.value) === encounterId) {
      detailVisible.value = false;
    }
    await search();
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error) || '取消失败');
  } finally {
    cancelLoadingId.value = '';
  }
}

async function searchCustomers() {
  const raw = customerKeyword.value.trim();
  if (!raw) {
    ElMessage.warning('请输入身份证号或姓名');
    return;
  }
  customerLoading.value = true;
  try {
    const isIdCard = /^\d{15}(\d{2}[\dXx])?$/.test(raw);
    const res = await searchCustomerApi({
      idCardNo: isIdCard ? raw : undefined,
      nameKeyword: isIdCard ? undefined : raw,
      pageNo: 1,
      pageSize: 20,
    });
    customerRows.value = res.rows ?? [];
    if (customerRows.value.length === 0) {
      ElMessage.info('未找到客户');
      return;
    }
    if (customerRows.value.length === 1) {
      await pickCustomerAndSearch(customerRows.value[0]!);
    }
  } finally {
    customerLoading.value = false;
  }
}

async function pickCustomerAndSearch(row: CustomerRowDto) {
  selectedCustomer.value = row;
  patientId.value = row.patientId != null ? String(row.patientId) : '';
  customerRows.value = [];
  await search();
}

async function searchByForm() {
  if (mode.value === 'patient') {
    if (selectedCustomer.value?.patientId != null) {
      patientId.value = String(selectedCustomer.value.patientId);
      await search();
      return;
    }
    await searchCustomers();
    return;
  }
  patientId.value = '';
  selectedCustomer.value = null;
  await search();
}

async function search() {
  loading.value = true;
  completeness.value = null;
  try {
    let req: {
      patientId?: string;
      studentNo?: string;
      employeeNo?: string;
    };
    if (patientId.value.trim()) {
      req = { patientId: patientId.value.trim() };
    } else if (mode.value === 'student') {
      if (!studentNo.value.trim()) {
        ElMessage.warning('请输入学号');
        return;
      }
      req = { studentNo: studentNo.value.trim() };
    } else if (mode.value === 'staff') {
      if (!employeeNo.value.trim()) {
        ElMessage.warning('请输入工号');
        return;
      }
      req = { employeeNo: employeeNo.value.trim() };
    } else {
      ElMessage.warning('请先检索并选择客户');
      return;
    }
    const res = await listPatientExamHistoryApi(req);
    rows.value = res.rows || [];
    subjectName.value = res.subjectName || selectedCustomer.value?.name || '';
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
    mode.value = 'patient';
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
    description="按客户（身份证/姓名）、学号或工号查询历年体检；支持个人体检与团体体检"
  >
    <ElForm inline class="mb-4">
      <ElFormItem label="对象">
        <ElRadioGroup v-model="mode">
          <ElRadioButton value="patient">客户</ElRadioButton>
          <ElRadioButton value="student">学生</ElRadioButton>
          <ElRadioButton value="staff">教职工</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem v-if="mode === 'patient'" label="客户">
        <ElInput
          v-model="customerKeyword"
          placeholder="身份证号 / 姓名"
          clearable
          style="width: 220px"
          @keyup.enter="searchByForm"
        />
      </ElFormItem>
      <ElFormItem v-else-if="mode === 'student'" label="学号">
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
      <ElFormItem label="类型">
        <ElRadioGroup v-model="kindScope">
          <ElRadioButton value="all">全部</ElRadioButton>
          <ElRadioButton value="personal">个人体检</ElRadioButton>
          <ElRadioButton value="group">团体体检</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="loading || customerLoading" @click="searchByForm">
          查询
        </ElButton>
      </ElFormItem>
    </ElForm>

    <div
      v-if="mode === 'patient' && customerRows.length > 0"
      class="mb-3 max-h-40 overflow-auto rounded border border-[var(--el-border-color-lighter)]"
    >
      <button
        v-for="row in customerRows"
        :key="String(row.patientId)"
        type="button"
        class="flex w-full items-center gap-3 border-b border-[var(--el-border-color-extra-light)] px-3 py-2 text-left text-sm last:border-b-0 hover:bg-[var(--el-fill-color-light)]"
        @click="pickCustomerAndSearch(row)"
      >
        <span class="font-medium">{{ row.name }}</span>
        <span class="text-[var(--el-text-color-secondary)]">{{ row.idCardNo || '—' }}</span>
        <span class="text-[var(--el-text-color-secondary)]">{{ row.phone || '' }}</span>
      </button>
    </div>

    <div v-if="subjectName || subjectType || selectedCustomer" class="mb-3 text-sm text-gray-600">
      {{ subjectName || selectedCustomer?.name || '—' }}
      <ElTag v-if="subjectType || mode === 'patient'" size="small" class="ml-2">
        {{ subjectTypeLabel(subjectType) || '客户' }}
      </ElTag>
      <span v-if="selectedCustomer?.idCardNo" class="ml-2 text-[var(--el-text-color-secondary)]">
        {{ selectedCustomer.idCardNo }}
      </span>
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

    <ElTable :data="displayRows" border v-loading="loading" empty-text="暂无体检记录">
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn label="个检/团检" width="100">
        <template #default="{ row }">
          <ElTag :type="isGroupRow(row) ? 'warning' : 'success'" size="small" effect="plain">
            {{ isGroupRow(row) ? '团检' : '个检' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="examKind" label="体检类型" width="120">
        <template #default="{ row }">
          {{ kindLabel(row.examKind) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="packageName" label="套餐" min-width="140" />
      <ElTableColumn prop="calendarYear" label="年份" width="80" />
      <ElTableColumn label="状态" width="100">
        <template #default="{ row }">
          {{ row.statusText || row.statusEnum || '—' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createdAt" label="体检时间" min-width="160" />
      <ElTableColumn prop="finalReport" label="总检定稿" width="90">
        <template #default="{ row }">
          <ElTag :type="row.finalReport ? 'success' : 'info'" size="small">
            {{ row.finalReport ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="收费" width="80">
        <template #default="{ row }">
          <ElTag :type="row.paid ? 'success' : 'info'" size="small">
            {{ row.paid ? '已收' : '未收' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <ElButton type="primary" link @click="openDetail(row)">查看</ElButton>
          <ElButton
            v-if="row.cancelable"
            type="danger"
            link
            :loading="cancelLoadingId === encounterKey(row)"
            @click="cancelExam(row)"
          >
            取消
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDrawer v-model="detailVisible" title="体检详情" size="520px">
      <div v-loading="detailLoading">
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem label="流水号">
            {{ orderDetail?.busNo || detailRow?.busNo || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="套餐">
            {{ detailRow?.packageName || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="类型">
            {{ kindLabel(detailRow?.examKind) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            {{ detailRow?.statusText || detailRow?.statusEnum || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="收费">
            {{ detailRow?.paid ? '已收' : '未收' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="总检定稿">
            {{ detailRow?.finalReport ? '是' : '否' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="体检时间">
            {{ detailRow?.createdAt || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>
        <ElTable
          class="mt-4"
          :data="orderDetail?.serviceRequestLines ?? []"
          border
          empty-text="暂无项目"
        >
          <ElTableColumn prop="display" label="项目" min-width="180" />
          <ElTableColumn label="执行状态" width="100">
            <template #default="{ row }">
              <ElTag size="small" :type="execTagType(row.execStatusCode)">
                {{ labelExec(row.execStatusCode) }}
              </ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
        <div v-if="detailRow?.cancelable" class="mt-4">
          <ElButton
            type="danger"
            :loading="cancelLoadingId === encounterKey(detailRow)"
            @click="cancelExam(detailRow)"
          >
            取消体检
          </ElButton>
        </div>
      </div>
    </ElDrawer>
  </Page>
</template>
