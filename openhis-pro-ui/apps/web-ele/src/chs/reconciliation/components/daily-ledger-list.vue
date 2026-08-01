<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ChargeType } from '#/api/core/common';
import type {
  ClearAmountSummary,
  LocalReconDailyRecord,
  ReconDaily3201Result,
  ReconDetailResultRow,
} from '#/chs/api';

import type { DailyFeeRow, DailyMismatchDetail, ReconStatusCode } from './daily-ledger-types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElButtonGroup,
  ElDatePicker,
  ElEmpty,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  listLocalReconDailyRecordsApi,
  reconcileDailyLedgerApi,
  reconcileDetailLedgerApi,
  summarizeClearAmountsApi,
} from '#/chs/api';
import {
  formatClrType,
  formatInsutype,
  formatStmtRslt,
  isStmtBalanced,
} from '#/chs/constants/reconciliation';
import {
  formatClearOrgCode,
  formatClearOrgOptionLabel,
} from '#/chs/reconciliation/format-clear-org';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import AppDialog from '#/components/app-dialog.vue';

import { RECON_STATUS, RECON_STATUS_LABEL } from './daily-ledger-types';

defineOptions({ name: 'DailyLedgerList' });

interface Props {
  chargeTypeOptions?: ChargeType[];
  loadingChargeTypes?: boolean;
  /** 侧栏嵌套：筛选换行、列精简、高度压缩 */
  compact?: boolean;
  /** 初始对账日（yyyy-MM-dd）；变化时同步并刷新 */
  initialReconDate?: string;
  /** 初始业务类型；空串=全部 */
  defaultClrType?: string;
}

const props = withDefaults(defineProps<Props>(), {
  chargeTypeOptions: () => [],
  loadingChargeTypes: false,
  compact: false,
  initialReconDate: undefined,
  defaultClrType: '11',
});

const contractBusNo = defineModel<string | undefined>('contractBusNo');

const emit = defineEmits<{
  contractChange: [];
  reconciled: [];
}>();

/** 清算机构切换后通知父级刷新本地记录 */
function handleContractChange() {
  emit('contractChange');
  void loadFeeList();
}

/** 两位补零 */
function pad2(n: number) {
  return String(n).padStart(2, '0');
}

/** 格式化为 yyyy-MM-dd */
function formatYmd(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 默认对账日：昨天 */
function defaultReconDate(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return formatYmd(d);
}

/** 金额两位小数展示 */
function formatMoney(value?: number | null): string {
  if (value == null) return '—';
  return value.toFixed(2);
}

/** 接口金额转 number */
function toMoneyNumber(value?: number | string | null): number {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) return 0;
  return n;
}

/** 状态 Tag 类型（按 status code） */
function statusTagType(status: ReconStatusCode | string): 'danger' | 'info' | 'success' {
  if (status === RECON_STATUS.BALANCED) return 'success';
  if (status === RECON_STATUS.UNBALANCED) return 'danger';
  return 'info';
}

/** 状态文案 */
function formatReconStatusLabel(status: ReconStatusCode | string): string {
  if (
    status === RECON_STATUS.PENDING ||
    status === RECON_STATUS.BALANCED ||
    status === RECON_STATUS.UNBALANCED
  ) {
    return RECON_STATUS_LABEL[status];
  }
  return String(status || '—');
}

/** 解析契约 busNo，缺省时提示 */
function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择清算机构');
  }
  return busNo;
}

/** 从契约 busNo 解析结算经办机构编码（3201/3202 setlOptins） */
function requireSetlOptins(): string {
  const code = formatClearOrgCode(contractBusNo.value);
  if (!code) {
    throw new Error('请先选择清算机构');
  }
  return code;
}

const loading = ref(false);
const reconciling = ref(false);
const detailReconciling = ref(false);
const tableData = ref<DailyFeeRow[]>([]);
/** 已发起明细对账的行 id */
const detailDoneIds = ref<Set<string>>(new Set());
/** 行 id → 最近一次 3202 差异明细 */
const detailCache = ref<Map<string, DailyMismatchDetail[]>>(new Map());

const queryParams = reactive({
  pageNo: 1,
  pageSize: 50,
  reconDate: props.initialReconDate?.trim() || defaultReconDate(),
  /** 默认门诊；空串表示全部 */
  clrType: props.defaultClrType ?? '11',
});

watch(
  () => props.initialReconDate,
  (next) => {
    const date = next?.trim();
    if (!date || date === queryParams.reconDate) {
      return;
    }
    queryParams.reconDate = date;
    void loadFeeList();
  },
);

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'custom',
    prop: 'contractBusNo',
    label: '清算机构',
    slot: 'contractSlot',
  },
  {
    type: 'custom',
    prop: 'reconDate',
    label: '对账日期',
    slot: 'reconDateSlot',
  },
  {
    type: 'select',
    prop: 'clrType',
    label: '业务类型',
    clearable: true,
    multiple: false,
    filterable: false,
    width: '120px',
    placeholder: '全部',
    options: [
      { label: '门诊', value: '11' },
      { label: '住院', value: '21' },
    ],
    onChange: () => {
      void loadFeeList();
    },
  },
  {
    type: 'custom',
    prop: 'reconActions',
    label: '',
    slot: 'reconActionsSlot',
  },
]);

const tableColumns = computed<TableColumn[]>(() => {
  if (props.compact) {
    // 侧栏宽度约 420～480：只保留关键列，禁止 fixed/minWidth 撑出横向滚动
    return [
      {
        type: 'index',
        label: '#',
        width: 40,
        align: 'center',
        headerAlign: 'center',
      },
      {
        prop: 'insutypeText',
        label: '险种',
        minWidth: 100,
        align: 'left',
        headerAlign: 'center',
        showOverflowTooltip: true,
      },
      {
        prop: 'medfeeSumamt',
        label: '医疗费',
        width: 78,
        align: 'right',
        headerAlign: 'center',
        slot: 'medfeeSlot',
      },
      {
        prop: 'status',
        label: '状态',
        width: 64,
        align: 'center',
        headerAlign: 'center',
        slot: 'statusSlot',
      },
      {
        prop: 'action',
        label: '操作',
        width: 88,
        align: 'center',
        headerAlign: 'center',
        slot: 'actions',
      },
    ];
  }
  return [
    {
      type: 'index',
      label: '序号',
      minWidth: 56,
      align: 'center',
      headerAlign: 'center',
    },
    {
      prop: 'insutypeText',
      label: '险种',
      minWidth: 160,
      align: 'center',
      headerAlign: 'center',
      showOverflowTooltip: true,
    },
    {
      prop: 'clrTypeText',
      label: '业务类型',
      minWidth: 88,
      align: 'center',
      headerAlign: 'center',
    },
    {
      prop: 'medfeeSumamt',
      label: '医疗费总额',
      minWidth: 110,
      align: 'right',
      headerAlign: 'center',
      slot: 'medfeeSlot',
    },
    {
      prop: 'fundPaySumamt',
      label: '基金统筹',
      minWidth: 100,
      align: 'right',
      headerAlign: 'center',
      slot: 'fundSlot',
    },
    {
      prop: 'cashPayamt',
      label: '医保现金',
      minWidth: 100,
      align: 'right',
      headerAlign: 'center',
      slot: 'cashSlot',
    },
    {
      prop: 'acctPay',
      label: '个账支付',
      minWidth: 100,
      align: 'right',
      headerAlign: 'center',
      slot: 'acctSlot',
    },
    {
      prop: 'setlCount',
      label: '笔数',
      minWidth: 72,
      align: 'right',
      headerAlign: 'center',
    },
    {
      prop: 'status',
      label: '状态',
      minWidth: 92,
      align: 'center',
      headerAlign: 'center',
      slot: 'statusSlot',
    },
    {
      prop: 'action',
      label: '操作',
      minWidth: 160,
      fixed: 'right',
      align: 'center',
      headerAlign: 'center',
      slot: 'actions',
    },
  ];
});

const detailVisible = ref(false);
const detailDialogTitle = ref('明细核对');
const detailRow = ref<DailyFeeRow | null>(null);
const detailList = ref<DailyMismatchDetail[]>([]);
const detailResultDesc = ref('');

const pendingCount = computed(
  () => tableData.value.filter((row) => row.status === RECON_STATUS.PENDING).length,
);

const unbalancedCount = computed(
  () => tableData.value.filter((row) => row.status === RECON_STATUS.UNBALANCED).length,
);

/** 是否已对某行做过明细对账 */
function isDetailDone(rowId: string): boolean {
  return detailDoneIds.value.has(rowId);
}

/** 匹配覆盖对账日的本地日账流水 */
function findLocalDailyRecord(
  records: LocalReconDailyRecord[],
  reconDate: string,
  insutype: string,
  clrType: string,
): LocalReconDailyRecord | undefined {
  // 按险种 + 清算类别 + 日期窗口匹配最近一次 3201 结果
  return records.find((record) => {
    const begin = (record.stmtBegndate || '').slice(0, 10);
    const end = (record.stmtEnddate || begin).slice(0, 10);
    if (!begin) return false;
    if (begin > reconDate || end < reconDate) return false;
    if ((record.insutype || '') !== insutype) return false;
    if ((record.clrType || '') !== clrType) return false;
    return true;
  });
}

/** 平台 stmt_rslt → 表格状态 */
function statusFromStmtRslt(stmtRslt?: string | null): ReconStatusCode {
  if (stmtRslt == null || stmtRslt === '') {
    return RECON_STATUS.PENDING;
  }
  if (isStmtBalanced(stmtRslt)) {
    return RECON_STATUS.BALANCED;
  }
  return RECON_STATUS.UNBALANCED;
}

/** HIS 汇总行 + 本地日账状态 → 表格行 */
function mapSummaryToFeeRow(
  summary: ClearAmountSummary,
  reconDate: string,
  localRecords: LocalReconDailyRecord[],
): DailyFeeRow {
  const insutype = (summary.insutype || '').trim();
  const clrType = (summary.clrType || '').trim();
  const local = findLocalDailyRecord(localRecords, reconDate, insutype, clrType);
  return {
    id: `${reconDate}-${insutype || 'NA'}-${clrType || 'NA'}`,
    reconDate,
    insutype,
    insutypeText: formatInsutype(insutype),
    clrType,
    clrTypeText: formatClrType(clrType),
    medfeeSumamt: toMoneyNumber(summary.medfeeSumamt),
    fundPaySumamt: toMoneyNumber(summary.fundAppySum),
    cashPayamt: toMoneyNumber(summary.cashPayamt),
    acctPay: toMoneyNumber(summary.acctPay),
    setlCount: Number(summary.setlCount ?? 0),
    status: statusFromStmtRslt(local?.stmtRslt),
    resultDesc: local?.stmtRsltDscr || formatStmtRslt(local?.stmtRslt),
    localRecordId: local?.id,
  };
}

/** 加载 HIS 本地金额列表，并合并已落库日账状态（不调医保对账） */
async function loadFeeList() {
  if (!queryParams.reconDate) {
    ElMessage.warning('请选择对账日期');
    return;
  }
  let busNo: string;
  try {
    busNo = requireContractBusNo();
  } catch (error) {
    tableData.value = [];
    ElMessage.warning(error instanceof Error ? error.message : '请先选择清算机构');
    return;
  }

  loading.value = true;
  try {
    const reconDate = queryParams.reconDate;
    const clrTypeFilter = (queryParams.clrType || '').trim();
    // 本地金额：仅读 HIS 结算快照
    const summaries = await summarizeClearAmountsApi({
      contractBusNo: busNo,
      begndate: reconDate,
      enddate: reconDate,
      clrType: clrTypeFilter || undefined,
    });
    // 本地日账：用于回显是否已对、平/不平
    const monthFilter = reconDate.replaceAll('-', '').slice(0, 6);
    const localRecords = await listLocalReconDailyRecordsApi({
      contractBusNo: busNo,
      monthFilter,
    });
    tableData.value = summaries.map((row) => mapSummaryToFeeRow(row, reconDate, localRecords));
  } catch (error) {
    tableData.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载对账金额失败');
  } finally {
    loading.value = false;
  }
}

/** 查询（点查询或筛选变更） */
function handleQuery() {
  void loadFeeList();
}

/** 对账日期变更并重新加载 */
function handleReconDateChange(value: string | null) {
  if (!value) return;
  queryParams.reconDate = value;
  void loadFeeList();
}

/** 快捷日期 */
function setDatePreset(preset: 'today' | 'yesterday') {
  const d = new Date();
  if (preset === 'yesterday') {
    d.setDate(d.getDate() - 1);
  }
  queryParams.reconDate = formatYmd(d);
  void loadFeeList();
}

/** 用 3201 出参更新行状态 */
function applyDailyResult(row: DailyFeeRow, result: ReconDaily3201Result): DailyFeeRow {
  return {
    ...row,
    status: statusFromStmtRslt(result.stmtRslt),
    resultDesc: result.stmtRsltDscr || formatStmtRslt(result.stmtRslt),
    localRecordId: result.localRecordId ?? row.localRecordId,
  };
}

/** 发起单行 3201 日对账（出站医保） */
async function reconcileOneRow(row: DailyFeeRow): Promise<DailyFeeRow> {
  const busNo = requireContractBusNo();
  const setlOptins = requireSetlOptins();
  const result = await reconcileDailyLedgerApi({
    contractBusNo: busNo,
    insutype: row.insutype,
    clrType: row.clrType,
    setlOptins,
    stmtBegndate: row.reconDate,
    stmtEnddate: row.reconDate,
    medfeeSumamt: row.medfeeSumamt,
    fundPaySumamt: row.fundPaySumamt,
    acctPay: row.acctPay,
    fixmedinsSetlCnt: row.setlCount,
  });
  return applyDailyResult(row, result);
}

/** 单行对账 */
async function handleReconcileRow(row: DailyFeeRow) {
  reconciling.value = true;
  try {
    await ensureInsuranceSignIn(requireContractBusNo());
    const next = await reconcileOneRow(row);
    tableData.value = tableData.value.map((item) => (item.id === row.id ? next : item));
    emit('reconciled');
    if (next.status === RECON_STATUS.BALANCED) {
      ElMessage.success(`${next.insutypeText}（${next.clrTypeText}）对账平`);
    } else {
      ElMessage.warning(
        `${next.insutypeText}（${next.clrTypeText}）对账不平${
          next.resultDesc ? `：${next.resultDesc}` : ''
        }`,
      );
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发起对账失败');
  } finally {
    reconciling.value = false;
  }
}

/** 批量对账：逐行调用 3201 */
async function handleReconcileAll() {
  const targets = tableData.value.filter((row) => row.status === RECON_STATUS.PENDING);
  if (targets.length === 0) {
    ElMessage.info('没有待对账的记录');
    return;
  }
  reconciling.value = true;
  let unbalanced = 0;
  let failed = 0;
  try {
    await ensureInsuranceSignIn(requireContractBusNo());
    // 顺序发起，避免并发打爆医保网关
    for (const row of targets) {
      try {
        const next = await reconcileOneRow(row);
        tableData.value = tableData.value.map((item) => (item.id === row.id ? next : item));
        if (next.status === RECON_STATUS.UNBALANCED) {
          unbalanced += 1;
        }
      } catch {
        failed += 1;
      }
    }
    emit('reconciled');
    if (failed > 0) {
      ElMessage.warning(`全部对账结束：失败 ${failed} 条，不平 ${unbalanced} 条`);
    } else if (unbalanced > 0) {
      ElMessage.warning(`全部对账完成，其中 ${unbalanced} 条不平`);
    } else {
      ElMessage.success('全部对账完成，均已平账');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '全部对账失败');
  } finally {
    reconciling.value = false;
  }
}

/** 3202 结果行 → 明细表格行（保留不平或有 memo 的行） */
function mapDetailResultRows(rows?: ReconDetailResultRow[]): DailyMismatchDetail[] {
  if (!rows?.length) return [];
  const mapped: DailyMismatchDetail[] = [];
  for (const row of rows) {
    // 平账且无说明的行不进差异表
    if (isStmtBalanced(row.stmtRslt) && !row.memo) {
      continue;
    }
    mapped.push({
      setlId: row.setlId || '—',
      psnNo: row.psnNo,
      mdtrtId: row.mdtrtId,
      stmtRslt: row.stmtRslt,
      memo: row.memo,
      medfeeSumamt: toMoneyNumber(row.medfeeSumamt),
      fundPaySumamt: toMoneyNumber(row.fundPaySumamt),
      acctPay: toMoneyNumber(row.acctPay),
    });
  }
  return mapped;
}

/** 对明细账：出站 3202 */
async function handleDetailReconcile(row: DailyFeeRow) {
  if (row.status !== RECON_STATUS.UNBALANCED) return;
  detailReconciling.value = true;
  try {
    await ensureInsuranceSignIn(requireContractBusNo());
    const result = await reconcileDetailLedgerApi({
      contractBusNo: requireContractBusNo(),
      setlOptins: requireSetlOptins(),
      stmtBegndate: row.reconDate,
      stmtEnddate: row.reconDate,
      clrType: row.clrType,
      medfeeSumamt: row.medfeeSumamt,
      fundPaySumamt: row.fundPaySumamt,
      cashPayamt: row.cashPayamt,
      fixmedinsSetlCnt: row.setlCount,
    });
    const mismatches = mapDetailResultRows(result.resultRows);
    const nextDone = new Set(detailDoneIds.value);
    nextDone.add(row.id);
    detailDoneIds.value = nextDone;
    const nextCache = new Map(detailCache.value);
    nextCache.set(row.id, mismatches);
    detailCache.value = nextCache;
    detailRow.value = row;
    detailList.value = mismatches;
    const mismatchCount = result.mismatchCount ?? mismatches.length;
    detailResultDesc.value =
      mismatchCount > 0 ? `发现 ${mismatchCount} 笔明细差异` : '明细核对完成，无差异行';
    detailDialogTitle.value = mismatchCount > 0 ? '明细对账结果（有差异）' : '明细对账结果';
    detailVisible.value = true;
    emit('reconciled');
    if (mismatchCount > 0) {
      ElMessage.warning(detailResultDesc.value);
    } else {
      ElMessage.success(detailResultDesc.value);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '明细对账失败');
  } finally {
    detailReconciling.value = false;
  }
}

/** 再次查看已缓存的明细对账结果 */
function handleViewDetail(row: DailyFeeRow) {
  const mismatches = detailCache.value.get(row.id) ?? [];
  detailRow.value = row;
  detailList.value = mismatches;
  detailResultDesc.value = row.resultDesc ?? '';
  detailDialogTitle.value = '明细核对';
  detailVisible.value = true;
}

onMounted(() => {
  void loadFeeList();
});
</script>

<template>
  <div class="daily-ledger-list" :class="{ 'daily-ledger-list--compact': compact }">
    <TableLayout
      class="daily-ledger-list__table !p-0"
      :table-data="tableData"
      :loading="loading || reconciling || detailReconciling"
      :total="tableData.length"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-default-buttons="false"
      :show-pagination="false"
      :border="false"
      :stripe="false"
      :max-height="compact ? 280 : 420"
      size="small"
      row-key="id"
      @query="handleQuery"
    >
      <template #contractSlot>
        <ElSelect
          v-model="contractBusNo"
          class="recon-filter-control recon-filter-control--org"
          placeholder="请选择清算机构"
          filterable
          :loading="loadingChargeTypes"
          @change="handleContractChange"
        >
          <ElOption
            v-for="item in chargeTypeOptions"
            :key="item.busNo"
            :label="formatClearOrgOptionLabel(item.label, item.busNo)"
            :value="item.busNo"
          />
        </ElSelect>
      </template>

      <template #reconDateSlot>
        <div class="flex flex-nowrap items-center gap-2">
          <ElDatePicker
            :model-value="queryParams.reconDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            class="recon-filter-control recon-filter-control--date"
            @update:model-value="handleReconDateChange"
          />
          <ElButtonGroup>
            <ElButton @click="setDatePreset('yesterday')">昨天</ElButton>
            <ElButton @click="setDatePreset('today')">今天</ElButton>
          </ElButtonGroup>
        </div>
      </template>

      <template #reconActionsSlot>
        <div class="flex items-center gap-2">
          <span v-if="unbalancedCount > 0" class="text-sm" style="color: var(--el-color-danger)">
            不平 {{ unbalancedCount }} 条
          </span>
          <ElButton
            type="primary"
            :loading="reconciling"
            :disabled="pendingCount === 0"
            @click="handleReconcileAll"
          >
            全部对账{{ pendingCount > 0 ? `（${pendingCount}）` : '' }}
          </ElButton>
        </div>
      </template>

      <template #medfeeSlot="{ row }">
        {{ formatMoney(row.medfeeSumamt) }}
      </template>
      <template #fundSlot="{ row }">
        {{ formatMoney(row.fundPaySumamt) }}
      </template>
      <template #cashSlot="{ row }">
        {{ formatMoney(row.cashPayamt) }}
      </template>
      <template #acctSlot="{ row }">
        {{ formatMoney(row.acctPay) }}
      </template>

      <template #statusSlot="{ row }">
        <ElTag :type="statusTagType(row.status)" effect="light" size="small">
          {{ formatReconStatusLabel(row.status) }}
        </ElTag>
      </template>

      <template #actions="{ row }">
        <div class="daily-ledger-list__actions">
          <ElButton
            v-if="row.status === RECON_STATUS.PENDING"
            type="primary"
            link
            :loading="reconciling"
            @click="handleReconcileRow(row)"
          >
            {{ compact ? '对账' : '发起对账' }}
          </ElButton>
          <ElButton
            v-if="row.status === RECON_STATUS.UNBALANCED && !isDetailDone(row.id)"
            type="warning"
            link
            :loading="detailReconciling"
            @click="handleDetailReconcile(row)"
          >
            {{ compact ? '明细' : '对明细账' }}
          </ElButton>
          <ElButton
            v-if="row.status === RECON_STATUS.UNBALANCED && isDetailDone(row.id)"
            type="primary"
            link
            @click="handleViewDetail(row)"
          >
            {{ compact ? '查看' : '查看明细' }}
          </ElButton>
          <span
            v-if="row.status === RECON_STATUS.BALANCED"
            class="text-sm"
            style="color: var(--el-text-color-secondary)"
          >
            已平
          </span>
        </div>
      </template>
    </TableLayout>

    <AppDialog v-model="detailVisible" :title="detailDialogTitle" width="720px">
      <template #default>
        <p v-if="detailRow" class="mb-3 text-sm" style="color: var(--el-text-color-regular)">
          {{ detailRow.insutypeText }} · {{ detailRow.clrTypeText }} ·
          {{ detailRow.reconDate }}
          <span v-if="detailResultDesc"> — {{ detailResultDesc }}</span>
        </p>
        <ElTable v-if="detailList.length" :data="detailList" border stripe size="small">
          <ElTableColumn prop="setlId" label="结算号" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="psnNo" label="人员编号" min-width="120" show-overflow-tooltip />
          <ElTableColumn prop="mdtrtId" label="就诊号" min-width="120" show-overflow-tooltip />
          <ElTableColumn label="医疗费" width="100" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.medfeeSumamt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="基金" width="100" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.fundPaySumamt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="个账" width="100" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.acctPay) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果" width="72" align="center">
            <template #default="{ row }">
              {{ formatStmtRslt(row.stmtRslt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="memo" label="说明" min-width="140" show-overflow-tooltip />
        </ElTable>
        <ElEmpty v-else description="无差异明细" :image-size="72" class="!py-4" />
      </template>
      <template #footer>
        <ElButton type="primary" @click="detailVisible = false">关闭</ElButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
/* 筛选行：单行排列，不出现横向滚动；操作顶到最右 */
.daily-ledger-list__table :deep(.el-form) {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  width: 100%;
  overflow: visible;
}

.daily-ledger-list--compact .daily-ledger-list__table :deep(.el-form) {
  flex-wrap: wrap;
  overflow: hidden;
}

.daily-ledger-list--compact {
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
}

.daily-ledger-list--compact .daily-ledger-list__table {
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
}

.daily-ledger-list--compact .daily-ledger-list__table :deep(.vxe-grid),
.daily-ledger-list--compact .daily-ledger-list__table :deep(.vxe-table),
.daily-ledger-list--compact .daily-ledger-list__table :deep(.vxe-table--main-wrapper),
.daily-ledger-list--compact .daily-ledger-list__table :deep(.vxe-table--body-wrapper),
.daily-ledger-list--compact .daily-ledger-list__table :deep(.vxe-table--header-wrapper) {
  min-width: 0 !important;
  max-width: 100%;
  overflow-x: hidden !important;
}

.daily-ledger-list--compact
  .daily-ledger-list__table
  :deep(.vxe-table--body-wrapper::-webkit-scrollbar:horizontal),
.daily-ledger-list--compact
  .daily-ledger-list__table
  :deep(.vxe-table--header-wrapper::-webkit-scrollbar:horizontal) {
  display: none;
  height: 0;
}

.daily-ledger-list--compact .recon-filter-control--date {
  width: 130px;
}

.daily-ledger-list--compact .recon-filter-control--org {
  width: 100%;
  max-width: 200px;
}

.daily-ledger-list--compact .daily-ledger-list__table :deep(.el-form-item__label) {
  width: 64px !important;
  min-width: 64px;
}

.daily-ledger-list--compact .daily-ledger-list__table :deep(.el-form > .el-form-item:last-child) {
  margin-left: 0;
  width: 100%;
}

.daily-ledger-list--compact .daily-ledger-list__actions {
  flex-wrap: nowrap;
}

.daily-ledger-list--compact .daily-ledger-list__actions :deep(.el-button) {
  padding: 0 2px;
  margin: 0;
}

.daily-ledger-list__table :deep(.el-form-item) {
  flex-shrink: 0;
  margin-right: 12px;
  margin-bottom: 8px;
}

.daily-ledger-list__table :deep(.el-form-item__label) {
  width: 80px !important;
  min-width: 80px;
  height: 32px;
  line-height: 32px;
  justify-content: flex-end;
  white-space: nowrap !important;
  word-break: keep-all;
}

/* inline 表单项标签与内容并排，勿再给 content 加 margin-left，否则会撑出滚动条 */
.daily-ledger-list__table :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.daily-ledger-list__table :deep(.el-form > .el-form-item:last-child) {
  margin-left: auto;
  margin-right: 0;
}

.daily-ledger-list__table :deep(.el-form > .el-form-item:last-child .el-form-item__label) {
  display: none;
}

.recon-filter-control--org {
  width: 220px;
}

.recon-filter-control--date {
  width: 160px;
}

/* 对齐门诊挂号 history 列表视觉；勿用 height:100% 触发 Table 自测高度死循环 */
.daily-ledger-list__table :deep(.vxe-grid) {
  --vxe-ui-table-border-color: #f0f4fa;
  --vxe-ui-table-header-background-color: #f7fafc;
  --vxe-ui-font-color: #1a2b3c;
  --vxe-ui-font-size-small: 13px;
  --vxe-ui-table-row-height-small: 34px;
  font-size: 13px;
}

.daily-ledger-list__table :deep(.vxe-table--header-wrapper) {
  border-bottom: 2px solid #e6edf4;
}

.daily-ledger-list__table :deep(.vxe-header--column) {
  height: 34px;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  color: #2c4054;
  background: #f7fafc;
}

.daily-ledger-list__table :deep(.vxe-header--column .vxe-cell) {
  height: 34px;
  padding: 6px 10px;
  line-height: 20px;
}

.daily-ledger-list__table :deep(.vxe-body--column) {
  height: 34px;
  border-bottom: 1px solid #f0f4fa;
  background-image: none;
}

.daily-ledger-list__table :deep(.vxe-body--column .vxe-cell) {
  height: 34px;
  padding: 6px 10px;
  line-height: 20px;
  color: #1a2b3c;
  white-space: nowrap;
}

.daily-ledger-list__table :deep(.vxe-body--row) {
  background: #fff;
}

.daily-ledger-list__table :deep(.vxe-body--row:hover .vxe-body--column) {
  background: #fafcff;
}

.daily-ledger-list__table :deep(.vxe-table--border-line) {
  display: none;
}

.daily-ledger-list__table :deep(.vxe-toolbar) {
  padding: 0 0 8px;
  background: transparent;
}

.daily-ledger-list__actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  justify-content: center;
}
</style>
