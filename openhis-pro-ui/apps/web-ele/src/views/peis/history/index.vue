<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@zxplat/common-ui';

import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  EXAM_KIND_LABEL,
  cancelPhysicalExamApi,
  pageExamRecordsApi,
  type ExamHistoryRowDto,
} from '#/api/peis/exam-history';
import {
  getCheckOrderDetailApi,
  type CheckOrderDetailResponse,
} from '#/api/peis/personal-registration';
import { extractRequestErrorMessage } from '#/utils/http/stale-cross-role-error';

defineOptions({ name: 'PeisExamRecords' });

type GroupScope = 'all' | 'personal' | 'group';

const route = useRoute();

function defaultDateRange(): [string, string] {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 90);
  const fmt = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };
  return [fmt(start), fmt(end)];
}

const query = reactive({
  keyword: '',
  groupScope: 'all' as GroupScope,
  dateRange: defaultDateRange() as string[] | undefined,
  patientId: undefined as string | undefined,
  pageNo: 1,
  pageSize: 20,
});

const rows = ref<ExamHistoryRowDto[]>([]);
const total = ref(0);
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

function kindLabel(kind?: string) {
  if (!kind) return '—';
  return EXAM_KIND_LABEL[kind] || kind;
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

async function loadList() {
  loading.value = true;
  try {
    const range = query.dateRange;
    const res = await pageExamRecordsApi({
      keyword: query.keyword.trim() || undefined,
      groupScope: query.groupScope || 'all',
      dateFrom: range?.[0] || null,
      dateTo: range?.[1] || null,
      patientId: query.patientId || null,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    });
    rows.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  query.pageNo = 1;
  void loadList();
}

function handlePageChange(page: number) {
  query.pageNo = page;
  void loadList();
}

function handleSizeChange(size: number) {
  query.pageSize = size;
  query.pageNo = 1;
  void loadList();
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
    await loadList();
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error) || '取消失败');
  } finally {
    cancelLoadingId.value = '';
  }
}

onMounted(() => {
  const qPatientId = String(route.query.patientId ?? '').trim();
  if (qPatientId) {
    query.patientId = qPatientId;
    // 从登记页跳入：看该人全部历次，不限近 90 天
    query.dateRange = undefined;
  }
  void loadList();
});
</script>

<template>
  <Page
    auto-content-height
    content-class="p-4"
    title="体检记录"
    description="按关键字、类型与日期浏览历次体检；点查看打开详情"
  >
    <ElForm inline class="mb-4">
      <ElFormItem label="关键字">
        <ElInput
          v-model="query.keyword"
          placeholder="姓名 / 身份证 / 流水号"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </ElFormItem>
      <ElFormItem label="类型">
        <ElRadioGroup v-model="query.groupScope" @change="handleSearch">
          <ElRadioButton value="all">全部</ElRadioButton>
          <ElRadioButton value="personal">个人体检</ElRadioButton>
          <ElRadioButton value="group">团体体检</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="体检日期">
        <ElDatePicker
          v-model="query.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          clearable
          @change="handleSearch"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="loading" @click="handleSearch">查询</ElButton>
      </ElFormItem>
    </ElForm>

    <ElTable :data="rows" border v-loading="loading" empty-text="暂无体检记录">
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn prop="patientName" label="姓名" min-width="90" />
      <ElTableColumn prop="idCardNo" label="身份证" min-width="160" show-overflow-tooltip />
      <ElTableColumn label="个检/团检" width="100">
        <template #default="{ row }">
          <ElTag :type="isGroupRow(row) ? 'warning' : 'success'" size="small" effect="plain">
            {{ isGroupRow(row) ? '团检' : '个检' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="examKind" label="体检类型" width="110">
        <template #default="{ row }">
          {{ kindLabel(row.examKind) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="packageName" label="套餐" min-width="140" show-overflow-tooltip />
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

    <div class="mt-3 flex justify-end">
      <ElPagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <ElDrawer v-model="detailVisible" title="体检详情" size="520px">
      <div v-loading="detailLoading">
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem label="姓名">
            {{ detailRow?.patientName || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="身份证">
            {{ detailRow?.idCardNo || '—' }}
          </ElDescriptionsItem>
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
