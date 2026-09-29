<script setup lang="ts">
import type { MedicalTechFeeItemRowDto } from '#/api/medical-tech/medical-tech-fee-list.types';

import { computed, inject, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
} from 'element-plus';

import {
  httpConfirmMedicalTechFees,
  httpListMedicalTechFeeItems,
  httpPrintMedicalTechFeeList,
} from '#/api/medical-tech/medical-tech-fee-list.http';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';

import { medicalTechWorkstationContextKey } from '../workbench-tab.config';
import {
  buildMedicalTechFeeSummaryLines,
  formatMedicalTechFeeConfirmLabel,
  formatMedicalTechFeeMoney,
  formatMedicalTechFeeSum,
  matchMedicalTechFeeChargeStatus,
  matchMedicalTechFeeConfirm,
  matchMedicalTechFeeKeyword,
  MT_FEE_CHARGE_STATUS_OPTIONS,
  MT_FEE_CONFIRM_OPTIONS,
  MT_FEE_PRESENTATION_OPTIONS,
  type MedicalTechFeeChargeStatusFilter,
  type MedicalTechFeeConfirmFilter,
  type MedicalTechFeePresentationMode,
  type MedicalTechFeeSummaryLine,
} from './medical-tech-fee-list.utils';

defineOptions({ name: 'MedicalTechFeeListPanel' });

/** 打印场景：复用住院费用明细单场景码（本科相关行由后端过滤） */
const PRINT_SCENARIO_CODE = PRINT_SCENARIO.ipFeeDetailPrint;

const ctx = inject(medicalTechWorkstationContextKey);
if (!ctx) {
  throw new Error('MedicalTechFeeListPanel 须在 MedicalTechWorkstation 壳内使用');
}

const { encounterScope, selectedEncounterId } = ctx;

/** 住院才有确费/划价时间；门诊费用清单不展示这些业务 */
const isInpatient = computed(() => encounterScope.value === 'INPATIENT');

const allFeeRows = ref<MedicalTechFeeItemRowDto[]>([]);
const sumAmount = ref<null | number | string>(null);
const keyword = ref('');
const chargeStatusFilter = ref<MedicalTechFeeChargeStatusFilter>('ALL');
const confirmFilter = ref<MedicalTechFeeConfirmFilter>('ALL');
const presentationMode = ref<MedicalTechFeePresentationMode>('DETAIL');
const appliedKeyword = ref('');
const appliedChargeStatus = ref<MedicalTechFeeChargeStatusFilter>('ALL');
const appliedConfirm = ref<MedicalTechFeeConfirmFilter>('ALL');

/** 当前筛后明细 */
const filteredDetailRows = computed(() => {
  // 门诊只筛项目+收费状态；确费筛仅住院
  return allFeeRows.value.filter((row) => {
    if (!matchMedicalTechFeeKeyword(row, appliedKeyword.value)) {
      return false;
    }
    if (!matchMedicalTechFeeChargeStatus(row, appliedChargeStatus.value)) {
      return false;
    }
    if (isInpatient.value && !matchMedicalTechFeeConfirm(row, appliedConfirm.value)) {
      return false;
    }
    return true;
  });
});

/** 当前筛后汇总 */
const summaryLines = computed<MedicalTechFeeSummaryLine[]>(() =>
  buildMedicalTechFeeSummaryLines(filteredDetailRows.value),
);

/** 展示行数 */
const activeLineCount = computed(() =>
  presentationMode.value === 'DETAIL' ? filteredDetailRows.value.length : summaryLines.value.length,
);

/** 筛后合计（明细金额加总展示） */
const filteredSumDisplay = computed(() => {
  let sum = 0;
  for (const row of filteredDetailRows.value) {
    const n = Number(row.totalPrice);
    if (Number.isFinite(n)) {
      sum += n;
    }
  }
  if (filteredDetailRows.value.length === 0) {
    return formatMedicalTechFeeSum(sumAmount.value);
  }
  return formatMedicalTechFeeSum(sum);
});

/** 存在尚未确费行时允许点一键确费（仅住院；对全量本科相关，不限当前筛） */
const hasUnconfirmed = computed(
  () => isInpatient.value && allFeeRows.value.some((row) => row && !row.feeConfirmerId),
);

const showEmptyState = computed(
  () => !listLoading.value && activeLineCount.value === 0 && Boolean(selectedEncounterId.value),
);

const { run: runListFee, loading: listLoading } = useRequest(httpListMedicalTechFeeItems, {
  manual: true,
  showErrorMessage: false,
  onSuccess: (data) => {
    allFeeRows.value = data.rows ?? [];
    sumAmount.value = data.sumAmount ?? null;
  },
});

const { run: runConfirm, loading: confirmLoading } = useRequest(httpConfirmMedicalTechFees, {
  manual: true,
  showErrorMessage: true,
  onSuccess: (data) => {
    ElMessage.success(data.message || `确费成功（${data.confirmedCount ?? 0} 行）`);
    void loadFeeList();
  },
});

const { run: runPrint, loading: printLoading } = useRequest(httpPrintMedicalTechFeeList, {
  manual: true,
  showErrorMessage: false,
  onSuccess: (data) => {
    if (data.succeeded === false) {
      ElMessage.warning(data.message || '打印未成功');
      return;
    }
    ElMessage.success(data.message || '已提交打印');
  },
});

/** 有就诊时拉取费用清单；无就诊或门禁失败则清空 */
async function loadFeeList() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    allFeeRows.value = [];
    sumAmount.value = null;
    return;
  }
  try {
    await runListFee({
      encounterId,
      encounterScope: encounterScope.value,
    });
  } catch {
    allFeeRows.value = [];
    sumAmount.value = null;
  }
}

/** 应用项目/收费状态/确费筛并查询（本地过滤；列表已按就诊拉取） */
function handleQuery() {
  appliedKeyword.value = keyword.value.trim();
  appliedChargeStatus.value = chargeStatusFilter.value;
  appliedConfirm.value = confirmFilter.value;
  if (allFeeRows.value.length === 0) {
    void loadFeeList();
  }
}

/** 重置筛选项为默认 */
function handleReset() {
  keyword.value = '';
  chargeStatusFilter.value = 'ALL';
  confirmFilter.value = 'ALL';
  appliedKeyword.value = '';
  appliedChargeStatus.value = 'ALL';
  appliedConfirm.value = 'ALL';
  presentationMode.value = 'DETAIL';
  void loadFeeList();
}

/** 切换明细/汇总页签 */
function handlePresentationModeChange(mode: string | number) {
  const next = String(mode ?? '');
  if (next === 'DETAIL' || next === 'SUMMARY') {
    presentationMode.value = next;
  }
}

/** 一键确费：仅住院；先弹确认，再对本页未确费行批量打戳 */
async function handleConfirmFees() {
  if (!isInpatient.value) {
    return;
  }
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const pendingCount = allFeeRows.value.filter((row) => row && !row.feeConfirmerId).length;
  if (pendingCount <= 0) {
    ElMessage.warning('没有待确费明细');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认对本患者 ${pendingCount} 条未确费明细执行一键确费？`,
      '一键确费',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await runConfirm({
    encounterId,
    encounterScope: encounterScope.value,
  });
}

/** 打印当前筛后费用明细（空筛=本科相关全集） */
async function handlePrint() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (filteredDetailRows.value.length === 0) {
    ElMessage.warning('当前条件下暂无费用数据可打印');
    return;
  }
  // 仅提交当前可见明细主键，避免打印超范围
  const chargeItemIds = filteredDetailRows.value
    .map((row) => preserveApiIdForRequest(row.chargeItemId))
    .filter((id): id is string => Boolean(id));
  try {
    await runPrint({
      encounterId,
      encounterScope: encounterScope.value,
      scenarioCode: PRINT_SCENARIO_CODE,
      chargeItemIds,
    });
  } catch {
    ElMessage.warning('打印失败请重试');
  }
}

watch(
  [selectedEncounterId, encounterScope],
  () => {
    keyword.value = '';
    chargeStatusFilter.value = 'ALL';
    confirmFilter.value = 'ALL';
    appliedKeyword.value = '';
    appliedChargeStatus.value = 'ALL';
    appliedConfirm.value = 'ALL';
    presentationMode.value = 'DETAIL';
    void loadFeeList();
  },
  { immediate: true },
);
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="bg-background flex shrink-0 flex-col gap-2 px-3 pb-1.5 pt-2">
      <div class="flex items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-4">
          <SectionTitle title="费用清单" />
          <div
            v-if="selectedEncounterId"
            class="flex items-center gap-3 text-sm text-[var(--el-text-color-regular)]"
          >
            <span>
              合计：
              <span class="text-[var(--el-text-color-primary)]">{{ filteredSumDisplay }}</span>
            </span>
            <span>
              {{ presentationMode === 'DETAIL' ? '明细' : '汇总' }}项数：{{ activeLineCount }} 项
            </span>
          </div>
        </div>
        <ElButton
          v-if="isInpatient"
          type="primary"
          :loading="confirmLoading"
          :disabled="!selectedEncounterId || !hasUnconfirmed || listLoading"
          @click="handleConfirmFees"
        >
          一键确费
        </ElButton>
      </div>
      <!-- 收费状态独立筛；确费筛仅住院（门诊无确费业务） -->
      <div class="mt-fee-query">
        <label class="mt-fee-field">
          <span>项目查询</span>
          <ElInput
            v-model="keyword"
            clearable
            class="!w-44"
            placeholder="项目名称/编码"
            @clear="handleQuery"
            @keyup.enter="handleQuery"
          />
        </label>
        <label class="mt-fee-field">
          <span>费用状态</span>
          <ElSelect v-model="chargeStatusFilter" class="!w-32" @change="handleQuery">
            <ElOption
              v-for="opt in MT_FEE_CHARGE_STATUS_OPTIONS"
              :key="opt.code"
              :label="opt.display"
              :value="opt.code"
            />
          </ElSelect>
        </label>
        <label v-if="isInpatient" class="mt-fee-field">
          <span>确费状态</span>
          <ElSelect v-model="confirmFilter" class="!w-32" @change="handleQuery">
            <ElOption
              v-for="opt in MT_FEE_CONFIRM_OPTIONS"
              :key="opt.code"
              :label="opt.display"
              :value="opt.code"
            />
          </ElSelect>
        </label>
        <div class="mt-fee-query__actions">
          <ElButton @click="handleReset">重置</ElButton>
          <ElButton type="primary" :loading="listLoading" @click="handleQuery">查询</ElButton>
          <ElButton
            plain
            :loading="printLoading"
            :disabled="!selectedEncounterId"
            @click="handlePrint"
          >
            打印费用明细
          </ElButton>
        </div>
      </div>
    </div>

    <div
      v-if="!selectedEncounterId"
      class="text-text-sub flex flex-1 items-center justify-center text-sm"
    >
      请先选择患者
    </div>

    <div
      v-else
      class="bg-background flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden px-3 pb-2 pt-0"
    >
      <div
        class="flex items-center justify-between gap-2 border-b border-[var(--el-border-color-lighter)]"
      >
        <ElTabs
          :model-value="presentationMode"
          class="mt-fee-tabs"
          @tab-change="handlePresentationModeChange"
        >
          <ElTabPane
            v-for="opt in MT_FEE_PRESENTATION_OPTIONS"
            :key="opt.code"
            :label="opt.display"
            :name="opt.code"
          />
        </ElTabs>
      </div>

      <div v-if="showEmptyState" class="flex flex-1 items-center justify-center">
        <ElEmpty description="当前查询条件下暂无费用数据" :image-size="96" />
      </div>

      <ElTable
        v-else-if="presentationMode === 'DETAIL'"
        v-loading="listLoading"
        :data="filteredDetailRows"
        border
        stripe
        height="100%"
        class="mt-fee-table min-h-0 flex-1"
        row-key="chargeItemId"
      >
        <ElTableColumn type="index" label="序号" width="56" align="center" />
        <ElTableColumn label="项目" width="148" prop="itemName" show-overflow-tooltip />
        <ElTableColumn
          label="编码"
          min-width="96"
          prop="itemCode"
          show-overflow-tooltip
          align="center"
        />
        <ElTableColumn align="right" label="数量" prop="quantity" min-width="64" />
        <ElTableColumn align="right" label="单价" min-width="80">
          <template #default="{ row }">
            {{ formatMedicalTechFeeMoney(row.unitPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="right" label="金额" min-width="80">
          <template #default="{ row }">
            {{ formatMedicalTechFeeMoney(row.totalPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="收费状态" prop="chargeStatusEnumText" min-width="88" />
        <ElTableColumn v-if="isInpatient" align="center" label="确费" min-width="64">
          <template #default="{ row }">
            {{ formatMedicalTechFeeConfirmLabel(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="isInpatient"
          align="center"
          label="确费人"
          min-width="80"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.feeConfirmerName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="isInpatient"
          align="center"
          label="计费人"
          min-width="80"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.billingPractitionerName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="isInpatient"
          align="center"
          label="划价时间"
          prop="pricedTime"
          min-width="148"
          show-overflow-tooltip
        />
        <ElTableColumn
          align="center"
          label="结算时间"
          prop="occurredTime"
          min-width="148"
          show-overflow-tooltip
        />
        <ElTableColumn
          label="开立科室"
          min-width="96"
          prop="relatedOrgDisplay"
          show-overflow-tooltip
          align="center"
        />
      </ElTable>

      <ElTable
        v-else
        v-loading="listLoading"
        :data="summaryLines"
        border
        stripe
        height="100%"
        class="mt-fee-table min-h-0 flex-1"
        row-key="rowKey"
      >
        <ElTableColumn type="index" label="序号" width="56" align="center" />
        <ElTableColumn label="项目" width="148" prop="itemName" show-overflow-tooltip />
        <ElTableColumn
          label="编码"
          min-width="120"
          prop="itemCode"
          show-overflow-tooltip
          align="center"
        />
        <ElTableColumn align="right" label="数量" prop="quantity" min-width="88" />
        <ElTableColumn align="right" label="单价" min-width="96">
          <template #default="{ row }">
            {{ formatMedicalTechFeeMoney(row.unitPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="right" label="金额" min-width="96">
          <template #default="{ row }">
            {{ formatMedicalTechFeeMoney(row.totalPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="明细条数" prop="lineCount" min-width="96" />
      </ElTable>
    </div>
  </div>
</template>

<style scoped>
.mt-fee-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.mt-fee-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: var(--el-fill-color) !important;
}
</style>
