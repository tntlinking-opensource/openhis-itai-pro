<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { RegisterHistoryTableRow } from '../services';

import type { DateRangeQuickSpec } from '#/utils/date-range-quick';

import { computed, onMounted, ref } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElCard, ElDatePicker, ElMessage, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { getDateRangeQuick } from '#/utils/date-range-quick';
import { tagTypeByCode } from '#/utils/status-tag';

import {
  cancelRegisterEncounter,
  DEFAULT_REGISTER_PAY_CHANNEL_CODE,
  ENCOUNTER_STATUS_CODE,
  ENCOUNTER_STATUS_EL_TAG_MAP,
  pageRegisterHistoryForTable,
  payRegisterFee,
} from '../services';

defineOptions({
  name: 'RegisterHistory',
});

const loading = ref(false);

/** 收费 / 退号共用一个核对弹窗 */
const historyActionDialogVisible = ref(false);
const historyActionMode = ref<'cancel' | 'charge'>('charge');
const historyActionRow = ref<null | RegisterHistoryTableRow>(null);
const actionDialogLoading = ref(false);

const historyActionTitle = computed(() => {
  if (historyActionMode.value === 'charge') {
    return '确认收费';
  }
  if (historyActionMode.value === 'cancel') {
    return '确认退号';
  }
  return '';
});
const tableData = ref<RegisterHistoryTableRow[]>([]);
const total = ref(0);
/** 与工具栏、分页绑定的查询条件（日期、keyword、分页） */
const queryParams = ref({
  pageNo: 1,
  pageSize: 20,
  dateRange: getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
  keyword: '',
});

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'encounterBusNo',
    label: '就诊号',
    width: 140,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'patientName',
    label: '患者信息',
    minWidth: 140,
    align: 'center',
    headerAlign: 'center',
    formatter: (row) => formatPatientInfoCell(row as RegisterHistoryTableRow),
  },
  {
    prop: 'contactPhone',
    label: '联系电话',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'organizationName',
    label: '科室',
    width: 90,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'doctorName',
    label: '医生',
    width: 80,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'serviceTypeName',
    label: '挂号类型',
    width: 100,
    slot: 'registerType',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'amount',
    label: '挂号费',
    width: 80,
    align: 'right',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => {
      const s = formatAmountTwoDecimals(cellValue);
      return s === '—' ? '—' : `${s} 元`;
    },
  },
  {
    prop: 'startTime',
    label: '挂号时间',
    width: 150,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'statusText',
    label: '状态',
    width: 80,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    width: 100,
    slot: 'action',
    align: 'center',
    headerAlign: 'center',
  },
];

/**
 * 按当前日期范围、关键字与分页拉取就诊记录表格数据。
 */
const handleQuery = async () => {
  loading.value = true;
  try {
    const [start, end] = queryParams.value.dateRange;
    const { list, total: totalCount } = await pageRegisterHistoryForTable({
      dateFrom: start ?? '',
      dateTo: end ?? '',
      keyword: queryParams.value.keyword,
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
    });
    tableData.value = list;
    total.value = totalCount;
  } finally {
    loading.value = false;
  }
};

/**
 * 列表「患者信息」列：姓名/性别（接口无年龄字段，仅拼性别文案）。
 */
function formatPatientInfoCell(row: RegisterHistoryTableRow): string {
  const name = String(row.patientName ?? '').trim();
  const gender = String(row.patientGenderText ?? '').trim();
  if (!name && !gender) {
    return '—';
  }
  if (!gender) {
    return name || '—';
  }
  if (!name) {
    return gender;
  }
  return `${name}/${gender}`;
}

/**
 * 金额四舍五入保留两位小数（列表费用列、弹窗等共用）。
 */
function formatAmountTwoDecimals(value: unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return (Math.round(n * 100) / 100).toFixed(2);
}

/**
 * 核对弹窗内空值展示。
 */
function historyDialogCell(value: string | undefined): string {
  const t = String(value ?? '').trim();
  return t || '—';
}

/**
 * 核对弹窗内挂号费金额（带 ¥）。
 */
function historyDialogAmountYuan(row: RegisterHistoryTableRow): string {
  const n = Number(row.amount);
  if (!Number.isFinite(n)) {
    return '—';
  }
  const s = (Math.round(n * 100) / 100).toFixed(2);
  return `¥${s}`;
}

/**
 * 核对弹窗关闭后清空行与提交态。
 */
function onHistoryActionDialogClosed() {
  historyActionRow.value = null;
  actionDialogLoading.value = false;
}

/**
 * 打开收费核对弹窗（正式结算前由用户确认）。
 */
function handleCharge(row: RegisterHistoryTableRow) {
  const draftId = String(row.paymentDraftId ?? '').trim();
  if (!draftId) {
    ElMessage.warning('当前记录无支付草稿，无法收费');
    return;
  }
  historyActionMode.value = 'charge';
  historyActionRow.value = row;
  historyActionDialogVisible.value = true;
}

/**
 * 弹窗内确认收费：提交挂号费正式结算后刷新列表。
 */
async function executeChargeRegister() {
  const row = historyActionRow.value;
  if (!row || historyActionMode.value !== 'charge') {
    return;
  }
  const draftId = String(row.paymentDraftId ?? '').trim();
  if (!draftId) {
    return;
  }
  actionDialogLoading.value = true;
  try {
    await payRegisterFee({
      paymentDetails: [
        {
          payAmount: row.amount,
          payChannelCode: DEFAULT_REGISTER_PAY_CHANNEL_CODE,
        },
      ],
      paymentDraftId: draftId,
    });
    historyActionDialogVisible.value = false;
    historyActionRow.value = null;
    ElMessage.success('收费成功');
    await handleQuery();
  } finally {
    actionDialogLoading.value = false;
  }
}

/**
 * 打开退号核对弹窗。
 */
function handleCancel(row: RegisterHistoryTableRow) {
  historyActionMode.value = 'cancel';
  historyActionRow.value = row;
  historyActionDialogVisible.value = true;
}

/**
 * 弹窗内确认退号：调用接口成功后刷新列表。
 */
async function executeCancelRegister() {
  const row = historyActionRow.value;
  if (!row || historyActionMode.value !== 'cancel') {
    return;
  }
  actionDialogLoading.value = true;
  try {
    await cancelRegisterEncounter({
      cancelReason: '窗口退号',
      encounterId: row.encounterId,
    });
    historyActionDialogVisible.value = false;
    historyActionRow.value = null;
    ElMessage.success('退号成功');
    await handleQuery();
  } finally {
    actionDialogLoading.value = false;
  }
}

/**
 * 表格分页变更：写回页码与每页条数后重新查询。
 */
const handlePagination = ({ page, limit }: { limit: number; page: number }) => {
  queryParams.value.pageNo = page;
  queryParams.value.pageSize = limit;
  handleQuery();
};

/**
 * 关键字搜索：回到第一页再查，避免仍停留在深页导致空表误解。
 */
const handleKeywordSearch = () => {
  queryParams.value.pageNo = 1;
  handleQuery();
};

/**
 * 日期范围变更：同样回到第一页再查（区间变了结果集可能变短）。
 */
const handleDateRangeChange = () => {
  queryParams.value.pageNo = 1;
  handleQuery();
};

/** 快捷筛：入参为 `getDateRangeQuick` 的规则对象，各页按需组合 */
function applyDateRangeQuick(spec: DateRangeQuickSpec) {
  queryParams.value.dateRange = getDateRangeQuick(spec) as [string, string];
  handleDateRangeChange();
}

/** 进入页面默认按「今天」条件拉一次列表 */
onMounted(() => {
  handleQuery();
});

/** 供父页在挂号成功等场景触发右侧列表刷新 */
defineExpose({
  refresh: handleQuery,
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col overflow-hidden">
    <ElCard
      shadow="never"
      class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
      body-class="flex-1 min-h-0 overflow-hidden !p-2 !pt-0"
    >
      <template #header>
        <SectionTitle title="就诊记录" />
      </template>
      <div class="flex h-full min-h-0 flex-1 flex-col overflow-hidden">
        <TableLayout
          :table-data="tableData"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :table-columns="tableColumns"
          :show-pagination="true"
          :show-top-query="false"
          border
          stripe
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <div class="flex w-full items-center gap-2">
              <SelectFuzzyInput
                v-model="queryParams.keyword"
                placeholder="患者姓名/就诊号"
                @search="handleKeywordSearch"
              />
              <ElDatePicker
                v-model="queryParams.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 260px; max-width: 260px; flex: 0 0 260px"
                @change="handleDateRangeChange"
              />
              <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-day' })">今天</ElButton>
              <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-week' })">本周</ElButton>
              <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-month' })">本月</ElButton>
            </div>
          </template>
          <template #toolbar-tools>
            <ElButton :icon="Refresh" circle @click="handleKeywordSearch" />
          </template>
          <template #registerType="{ row }">
            <span>{{ row.serviceTypeName }}</span>
          </template>

          <template #status="{ row }">
            <ElTag
              :type="tagTypeByCode(row.statusEnum, ENCOUNTER_STATUS_EL_TAG_MAP)"
              effect="light"
            >
              {{ row.statusText }}
            </ElTag>
          </template>

          <template #action="{ row }">
            <ElButton
              type="primary"
              link
              :disabled="row.statusEnum !== ENCOUNTER_STATUS_CODE.PLANNED"
              @click="handleCharge(row)"
            >
              收费
            </ElButton>
            <ElButton
              type="danger"
              link
              :disabled="row.statusEnum !== ENCOUNTER_STATUS_CODE.REGISTERED"
              @click="handleCancel(row)"
            >
              退号
            </ElButton>
          </template>
        </TableLayout>

        <AppDialog
          v-model="historyActionDialogVisible"
          :title="historyActionTitle"
          width="520px"
          append-to-body
          destroy-on-close
          @closed="onHistoryActionDialogClosed"
        >
          <div
            v-if="historyActionRow"
            class="max-h-[min(400px,58vh)] overflow-y-auto text-left text-[14px] leading-relaxed"
          >
            <div
              class="mb-1 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
            >
              就诊与患者
            </div>
            <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
              <span class="text-[var(--el-text-color-regular)]">就诊号</span>
              <span class="break-all text-[var(--el-text-color-primary)]">{{
                historyDialogCell(historyActionRow.encounterBusNo)
              }}</span>
              <span class="text-[var(--el-text-color-regular)]">患者姓名</span>
              <span class="break-all text-[var(--el-text-color-primary)]">{{
                historyDialogCell(historyActionRow.patientName)
              }}</span>
              <span class="text-[var(--el-text-color-regular)]">联系电话</span>
              <span class="break-all text-[var(--el-text-color-primary)]">{{
                historyDialogCell(historyActionRow.contactPhone)
              }}</span>
            </div>

            <template v-if="historyActionMode === 'charge'">
              <div
                class="mb-1 mt-4 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
              >
                费用与草稿
              </div>
              <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
                <span class="text-[var(--el-text-color-regular)]">应收金额</span>
                <span class="text-[var(--el-text-color-primary)]">{{
                  historyDialogAmountYuan(historyActionRow)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">支付草稿</span>
                <span class="break-all text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.paymentDraftId)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">挂号类型</span>
                <span class="break-all text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.serviceTypeName)
                }}</span>
              </div>
            </template>

            <template v-else>
              <div
                class="mb-1 mt-4 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
              >
                挂号信息
              </div>
              <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
                <span class="text-[var(--el-text-color-regular)]">挂号科室</span>
                <span class="break-all text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.organizationName)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">接诊医生</span>
                <span class="break-all text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.doctorName)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">挂号类型</span>
                <span class="break-all text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.serviceTypeName)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">挂号时间</span>
                <span class="text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.startTime)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">应退金额</span>
                <span class="text-[var(--el-text-color-primary)]">{{
                  historyDialogAmountYuan(historyActionRow)
                }}</span>
                <span class="text-[var(--el-text-color-regular)]">当前状态</span>
                <span class="text-[var(--el-text-color-primary)]">{{
                  historyDialogCell(historyActionRow.statusText)
                }}</span>
              </div>
            </template>
          </div>

          <template #footer>
            <div class="flex justify-end gap-2">
              <ElButton :disabled="actionDialogLoading" @click="historyActionDialogVisible = false">
                取消
              </ElButton>
              <ElButton
                v-if="historyActionMode === 'charge'"
                type="primary"
                :loading="actionDialogLoading"
                @click="executeChargeRegister"
              >
                确认收费
              </ElButton>
              <ElButton
                v-else-if="historyActionMode === 'cancel'"
                type="danger"
                :loading="actionDialogLoading"
                @click="executeCancelRegister"
              >
                确认退号
              </ElButton>
            </div>
          </template>
        </AppDialog>
      </div>
    </ElCard>
  </div>
</template>
