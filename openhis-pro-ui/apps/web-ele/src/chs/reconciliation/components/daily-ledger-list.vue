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
import type { DetailCompareViewMode } from './detail-compare';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  classifyDetailMemo,
  formatClrType,
  formatDetailKindLabel,
  formatInsutype,
  formatStmtRslt,
  isDetailAttention,
  isStmtBalanced,
} from '#/chs/constants/reconciliation';
import {
  ElButton,
  ElButtonGroup,
  ElDatePicker,
  ElEmpty,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';
import {
  listLocalReconDailyRecordsApi,
  reconcileDailyLedgerApi,
  reconcileDetailLedgerApi,
  summarizeClearAmountsApi,
} from '#/chs/api';
import {
  formatClearOrgCode,
  formatClearOrgOptionLabel,
} from '#/chs/reconciliation/format-clear-org';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import AppDialog from '#/components/app-dialog.vue';

import { RECON_STATUS, RECON_STATUS_LABEL } from './daily-ledger-types';
import {
  buildCompareFields,
  formatLocalStatus,
  formatRefdFlag,
  isAmountDiff,
  isStatusKindDiff,
} from './detail-compare';

defineOptions({ name: 'DailyLedgerList' });

interface Props {
  chargeTypeOptions?: ChargeType[];
  loadingChargeTypes?: boolean;
  /** 侧栏嵌套：筛选换行、列精简、高度压缩 */
  compact?: boolean;
  /** 初始对账日（yyyy-MM-dd）；变化时同步为单日区间并刷新 */
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

/** 当前表格数据所属契约，防止切换清算机构后点旧行对账 */
const loadedForBusNo = ref('');

const emit = defineEmits<{
  contractChange: [];
  reconciled: [];
  /** 对账区间变更（yyyy-MM-dd），供右侧本地记录按月对齐 */
  reconDateChange: [date: string];
  reconRangeChange: [begin: string, end: string];
}>();

/** 筛选变更后作废列表，需用户再点查询 */
function invalidateList() {
  tableData.value = [];
  loadedForBusNo.value = '';
  queried.value = false;
  amountsEditable.value = false;
  detailDoneIds.value = new Set();
  detailCache.value = new Map();
}

/** 清算机构切换：清空列表，不自动查询 */
function handleContractChange() {
  invalidateList();
  emit('contractChange');
}

/** 将对账区间同步给父级 */
function emitReconDate() {
  const [begin, end] = requireDateRange();
  emit('reconDateChange', end);
  emit('reconRangeChange', begin, end);
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

/** 解析当前对账区间；未选全时抛错 */
function requireDateRange(): [string, string] {
  const begin = queryParams.dateRange?.[0]?.trim();
  const end = queryParams.dateRange?.[1]?.trim();
  if (!begin || !end) {
    throw new Error('请选择对账日期');
  }
  if (end < begin) {
    throw new Error('结束日期不能早于开始日期');
  }
  return [begin, end];
}

/** 展示区间文案 */
function formatDateRangeLabel(begin: string, end: string): string {
  return begin === end ? begin : `${begin} 至 ${end}`;
}

/** 起止日之间的逐日列表（含首尾） */
function daysBetween(begin: string, end: string): string[] {
  const days: string[] = [];
  const cursor = new Date(`${begin}T00:00:00`);
  const last = new Date(`${end}T00:00:00`);
  while (cursor <= last) {
    days.push(formatYmd(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return days;
}

/** 起止日之间的 yyyyMM 去重列表（含跨月） */
function monthsBetween(begin: string, end: string): string[] {
  const months: string[] = [];
  const cursor = new Date(`${begin}T00:00:00`);
  const last = new Date(`${end}T00:00:00`);
  cursor.setDate(1);
  last.setDate(1);
  while (cursor <= last) {
    months.push(`${cursor.getFullYear()}${pad2(cursor.getMonth() + 1)}`);
    cursor.setMonth(cursor.getMonth() + 1);
  }
  return months.length > 0 ? months : [begin.replaceAll('-', '').slice(0, 6)];
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
/** 是否已按当前条件点过查询（未查询时列表占位） */
const queried = ref(false);
/** 金额默认只读，避免误改；「改金额」后可编辑 */
const amountsEditable = ref(false);
const tableData = ref<DailyFeeRow[]>([]);
/** 已发起明细对账的行 id */
const detailDoneIds = ref<Set<string>>(new Set());
/** 行 id → 最近一次 3202 差异明细 */
const detailCache = ref<Map<string, DailyMismatchDetail[]>>(new Map());

const queryParams = reactive({
  pageNo: 1,
  pageSize: 50,
  /** 对账区间 [开始, 结束]，默认昨天单日 */
  dateRange: (() => {
    const d = props.initialReconDate?.trim() || defaultReconDate();
    return [d, d] as [string, string];
  })(),
  /** 默认门诊；空串表示全部 */
  clrType: props.defaultClrType ?? '11',
});

watch(
  () => props.initialReconDate,
  (next) => {
    const date = next?.trim();
    if (!date) return;
    const [begin, end] = queryParams.dateRange ?? ['', ''];
    if (date === begin && date === end) return;
    queryParams.dateRange = [date, date];
    emitReconDate();
    invalidateList();
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
    prop: 'dateRange',
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
      invalidateList();
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
      prop: 'stmtBegndate',
      label: '对账日期',
      minWidth: 120,
      align: 'center',
      headerAlign: 'center',
      slot: 'reconDaySlot',
    },
    {
      prop: 'insutypeText',
      label: '险种',
      minWidth: 220,
      align: 'left',
      headerAlign: 'center',
      showOverflowTooltip: true,
    },
    {
      prop: 'clrTypeText',
      label: '业务类型',
      minWidth: 96,
      align: 'center',
      headerAlign: 'center',
    },
    {
      prop: 'medfeeSumamt',
      label: '医疗费(可改)',
      minWidth: 128,
      align: 'center',
      headerAlign: 'center',
      slot: 'medfeeSlot',
    },
    {
      prop: 'fundPaySumamt',
      label: '基金(可改)',
      minWidth: 120,
      align: 'center',
      headerAlign: 'center',
      slot: 'fundSlot',
    },
    {
      prop: 'cashPayamt',
      label: '医保现金',
      minWidth: 110,
      align: 'right',
      headerAlign: 'center',
      slot: 'cashSlot',
    },
    {
      prop: 'acctPay',
      label: '个账(可改)',
      minWidth: 120,
      align: 'center',
      headerAlign: 'center',
      slot: 'acctSlot',
    },
    {
      prop: 'setlCount',
      label: '笔数(可改)',
      minWidth: 120,
      align: 'center',
      headerAlign: 'center',
      slot: 'setlCountSlot',
    },
    {
      prop: 'status',
      label: '状态',
      minWidth: 96,
      align: 'center',
      headerAlign: 'center',
      slot: 'statusSlot',
    },
    {
      prop: 'action',
      label: '操作',
      minWidth: 168,
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
/** 明细弹窗筛选：需关注（默认）/ 可忽略 / 全部 */
const detailFilter = ref<'attention' | 'noise' | 'all'>('attention');
/** 列表 | 左右对照 */
const detailViewMode = ref<DetailCompareViewMode>('list');
/** 当前选中结算号（字段对照 / 左右表联动） */
const selectedSetlId = ref<string>('');

const detailAttentionCount = computed(
  () => detailList.value.filter((row) => isDetailAttention(row.kind)).length,
);
const detailNoiseCount = computed(
  () => detailList.value.filter((row) => !isDetailAttention(row.kind)).length,
);
const detailFilteredList = computed(() => {
  if (detailFilter.value === 'attention') {
    return detailList.value.filter((row) => isDetailAttention(row.kind));
  }
  if (detailFilter.value === 'noise') {
    return detailList.value.filter((row) => !isDetailAttention(row.kind));
  }
  return detailList.value;
});

const selectedDetail = computed(() => {
  if (!selectedSetlId.value) return null;
  return (
    detailFilteredList.value.find((row) => row.setlId === selectedSetlId.value) ??
    detailList.value.find((row) => row.setlId === selectedSetlId.value) ??
    null
  );
});

const selectedCompareFields = computed(() => {
  if (!selectedDetail.value) return [];
  return buildCompareFields(selectedDetail.value);
});

function buildDetailResultDesc(rows: DailyMismatchDetail[]): string {
  const attention = rows.filter((row) => isDetailAttention(row.kind)).length;
  const noise = rows.length - attention;
  if (rows.length === 0) {
    return '明细核对完成，无差异行';
  }
  if (attention === 0) {
    return `无待处理差异 · 可忽略 ${noise} 笔（多为已平后重对）`;
  }
  if (noise === 0) {
    return `需关注 ${attention} 笔`;
  }
  return `需关注 ${attention} 笔 · 可忽略 ${noise} 笔（多为已平后重对）`;
}

function detailKindTagType(kind?: DailyMismatchDetail['kind']): 'danger' | 'warning' | 'info' {
  if (kind === 'refund_status' || kind === 'missing') return 'danger';
  if (kind === 'other') return 'warning';
  return 'info';
}

/** 打开明细弹窗时重置视图并默认选中首条需关注 */
function prepareDetailDialog(mismatches: DailyMismatchDetail[]) {
  detailList.value = mismatches;
  detailFilter.value = 'attention';
  detailViewMode.value = 'list';
  detailResultDesc.value = buildDetailResultDesc(mismatches);
  const attentionFirst = mismatches.find((item) => isDetailAttention(item.kind));
  selectedSetlId.value = attentionFirst?.setlId || mismatches[0]?.setlId || '';
}

function selectDetailRow(row: DailyMismatchDetail) {
  selectedSetlId.value = row.setlId;
}

function openDetailCompare(row: DailyMismatchDetail) {
  selectedSetlId.value = row.setlId;
  detailViewMode.value = 'side';
}

function sideCellClass(diff: boolean): string {
  return diff ? 'detail-compare-cell--diff' : '';
}

function localStatusCellClass(row: DailyMismatchDetail): string {
  return isStatusKindDiff(row) ? 'detail-compare-cell--diff' : '';
}

function amountCellClass(
  row: DailyMismatchDetail,
  side: 'local' | 'platform',
  field: 'medfee' | 'fund' | 'acct',
): string {
  const local =
    field === 'medfee'
      ? row.localMedfeeSumamt
      : field === 'fund'
        ? row.localFundPaySumamt
        : row.localAcctPay;
  const platform =
    field === 'medfee' ? row.medfeeSumamt : field === 'fund' ? row.fundPaySumamt : row.acctPay;
  if (row.localExists === false && side === 'local') {
    return 'detail-compare-cell--diff';
  }
  return isAmountDiff(local, platform) ? 'detail-compare-cell--diff' : '';
}

watch(detailFilter, () => {
  const stillVisible = detailFilteredList.value.some((row) => row.setlId === selectedSetlId.value);
  if (!stillVisible) {
    selectedSetlId.value = detailFilteredList.value[0]?.setlId || '';
  }
});

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

/** 匹配与对账区间完全一致的本地日账流水（优先精确起止） */
function findLocalDailyRecord(
  records: LocalReconDailyRecord[],
  stmtBegndate: string,
  stmtEnddate: string,
  insutype: string,
  clrType: string,
): LocalReconDailyRecord | undefined {
  const exact = records.find((record) => {
    const begin = (record.stmtBegndate || '').slice(0, 10);
    const end = (record.stmtEnddate || begin).slice(0, 10);
    if (!begin) return false;
    if (begin !== stmtBegndate || end !== stmtEnddate) return false;
    if ((record.insutype || '') !== insutype) return false;
    if ((record.clrType || '') !== clrType) return false;
    return true;
  });
  if (exact) return exact;
  // 回落：本地单日记录落在本区间内（兼容历史一日一账）
  if (stmtBegndate === stmtEnddate) {
    return records.find((record) => {
      const begin = (record.stmtBegndate || '').slice(0, 10);
      const end = (record.stmtEnddate || begin).slice(0, 10);
      if (!begin) return false;
      if (begin > stmtBegndate || end < stmtBegndate) return false;
      if ((record.insutype || '') !== insutype) return false;
      if ((record.clrType || '') !== clrType) return false;
      return true;
    });
  }
  return undefined;
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
  stmtBegndate: string,
  stmtEnddate: string,
  localRecords: LocalReconDailyRecord[],
): DailyFeeRow {
  const insutype = (summary.insutype || '').trim();
  const clrType = (summary.clrType || '').trim();
  const local = findLocalDailyRecord(localRecords, stmtBegndate, stmtEnddate, insutype, clrType);
  return {
    id: `${stmtBegndate}_${stmtEnddate}-${insutype || 'NA'}-${clrType || 'NA'}`,
    stmtBegndate,
    stmtEnddate,
    insutype,
    insutypeText: formatInsutype(insutype),
    clrType,
    clrTypeText: formatClrType(clrType),
    medfeeSumamt: toMoneyNumber(summary.medfeeSumamt),
    fundPaySumamt: toMoneyNumber(summary.fundAppySum),
    cashPayamt: toMoneyNumber(summary.cashPayamt),
    acctPay: toMoneyNumber(summary.acctPay),
    setlCount: Number(summary.setlCount ?? 0),
    localSetlCount: Number(summary.setlCount ?? 0),
    status: statusFromStmtRslt(local?.stmtRslt),
    resultDesc: local?.stmtRsltDscr || formatStmtRslt(local?.stmtRslt),
    localRecordId: local?.id,
  };
}

/** 加载 HIS 本地金额列表，并合并已落库日账状态（不调医保对账） */
async function loadFeeList() {
  let begin: string;
  let end: string;
  try {
    [begin, end] = requireDateRange();
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '请选择对账日期');
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
    emitReconDate();
    const clrTypeFilter = (queryParams.clrType || '').trim();
    const days = daysBetween(begin, end);
    // 本地日账：跨月时合并多月流水，用于回显是否已对、平/不平
    const monthList = monthsBetween(begin, end);
    const localBatches = await Promise.all(
      monthList.map((monthFilter) =>
        listLocalReconDailyRecordsApi({
          contractBusNo: busNo,
          monthFilter,
        }),
      ),
    );
    const localById = new Map<string, LocalReconDailyRecord>();
    for (const batch of localBatches) {
      for (const row of batch) {
        const key = String(row.id ?? `${row.stmtBegndate}-${row.insutype}-${row.clrType}`);
        localById.set(key, row);
      }
    }
    const localRecords = [...localById.values()];

    // 按天拆行：选多天时每天单独汇总、单独对账（不把多天合成一笔）
    const daySummaries = await Promise.all(
      days.map(async (day) => {
        const summaries = await summarizeClearAmountsApi({
          contractBusNo: busNo,
          begndate: day,
          enddate: day,
          clrType: clrTypeFilter || undefined,
        });
        return summaries.map((row) => mapSummaryToFeeRow(row, day, day, localRecords));
      }),
    );
    tableData.value = daySummaries.flat().sort((a, b) => {
      const byDate = a.stmtBegndate.localeCompare(b.stmtBegndate);
      if (byDate !== 0) return byDate;
      const byInsu = a.insutype.localeCompare(b.insutype);
      if (byInsu !== 0) return byInsu;
      return a.clrType.localeCompare(b.clrType);
    });
    loadedForBusNo.value = busNo;
    queried.value = true;
  } catch (error) {
    tableData.value = [];
    loadedForBusNo.value = '';
    queried.value = false;
    ElMessage.error(error instanceof Error ? error.message : '加载对账金额失败');
  } finally {
    loading.value = false;
  }
}

/** 点击查询 */
function handleQuery() {
  void loadFeeList();
}

/** 对账区间变更：仅更新条件，不自动查询 */
function handleReconRangeChange(value: string | [string, string] | null) {
  if (Array.isArray(value) && value[0] && value[1]) {
    queryParams.dateRange = [value[0], value[1]];
    emitReconDate();
    invalidateList();
    return;
  }
  if (typeof value === 'string' && value) {
    queryParams.dateRange = [value, value];
    emitReconDate();
    invalidateList();
  }
}

/** 紧凑模式单日变更 */
function handleCompactDateChange(value: string | null) {
  if (!value) return;
  queryParams.dateRange = [value, value];
  emitReconDate();
  invalidateList();
}

/** 快捷日期：仅改区间，需再点查询 */
function setDatePreset(preset: 'today' | 'yesterday' | 'week' | 'month') {
  const now = new Date();
  if (preset === 'today') {
    const d = formatYmd(now);
    queryParams.dateRange = [d, d];
  } else if (preset === 'yesterday') {
    const d = new Date(now);
    d.setDate(d.getDate() - 1);
    const y = formatYmd(d);
    queryParams.dateRange = [y, y];
  } else if (preset === 'week') {
    const end = new Date(now);
    const begin = new Date(now);
    begin.setDate(begin.getDate() - 6);
    queryParams.dateRange = [formatYmd(begin), formatYmd(end)];
  } else {
    const begin = new Date(now.getFullYear(), now.getMonth(), 1);
    const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    queryParams.dateRange = [formatYmd(begin), formatYmd(end)];
  }
  emitReconDate();
  invalidateList();
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

/** 发起单行 3201 日对账（出站医保；金额由后端按契约重算） */
async function reconcileOneRow(row: DailyFeeRow): Promise<DailyFeeRow> {
  if (loading.value) {
    throw new Error('列表刷新中，请稍后对账');
  }
  const busNo = requireContractBusNo();
  const setlOptins = requireSetlOptins();
  // 金额/笔数传 UI 值（可按平台口径改）；未改时即本地汇总
  const result = await reconcileDailyLedgerApi({
    contractBusNo: busNo,
    insutype: row.insutype,
    clrType: row.clrType,
    setlOptins,
    stmtBegndate: row.stmtBegndate,
    stmtEnddate: row.stmtEnddate,
    medfeeSumamt: row.medfeeSumamt,
    fundPaySumamt: row.fundPaySumamt,
    acctPay: row.acctPay,
    fixmedinsSetlCnt: row.setlCount,
  });
  return applyDailyResult(row, result);
}

/** 单行对账 */
async function handleReconcileRow(row: DailyFeeRow) {
  const currentBusNo = contractBusNo.value?.trim() ?? '';
  if (!currentBusNo || currentBusNo !== loadedForBusNo.value) {
    ElMessage.warning('清算机构与列表不一致，请先点查询再对账');
    return;
  }
  const medfee = Number(row.medfeeSumamt ?? 0);
  if (!Number.isFinite(medfee) || medfee === 0) {
    const busNo = contractBusNo.value?.trim() ?? '';
    const orgOpt = props.chargeTypeOptions.find((o) => o.busNo === busNo);
    const orgLabel = formatClearOrgOptionLabel(orgOpt?.label, busNo);
    try {
      await ElMessageBox.confirm(
        `当前清算机构「${orgLabel}」该日医疗费为 0，通常说明选错了机构（例如选了省医保而数据在市医保）。是否仍要对账？`,
        '请确认清算机构',
        { type: 'warning', confirmButtonText: '仍要对账', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }
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
        }，正在对明细账…`,
      );
      // 不平则自动 3202，拿到平台差异以便对齐口径
      reconciling.value = false;
      await handleDetailReconcile(next);
      return;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发起对账失败');
  } finally {
    reconciling.value = false;
  }
}

/** 批量对账：逐行调用 3201（含未对账与不平，便于改口径后重对） */
async function handleReconcileAll() {
  const targets = tableData.value.filter(
    (row) => row.status === RECON_STATUS.PENDING || row.status === RECON_STATUS.UNBALANCED,
  );
  if (targets.length === 0) {
    ElMessage.info('没有待对账或不平的记录');
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

/** 3202 结果行 → 明细表格行（保留不平或有 memo 的行；写入分类便于筛选） */
function mapDetailResultRows(rows?: ReconDetailResultRow[]): DailyMismatchDetail[] {
  if (!rows?.length) return [];
  const mapped: DailyMismatchDetail[] = [];
  for (const row of rows) {
    // 平账且无说明的行不进差异表
    if (isStmtBalanced(row.stmtRslt) && !row.memo) {
      continue;
    }
    const medfeeSumamt = toMoneyNumber(row.medfeeSumamt);
    const fundPaySumamt = toMoneyNumber(row.fundPaySumamt);
    const acctPay = toMoneyNumber(row.acctPay);
    const localMedfeeSumamt =
      row.localMedfeeSumamt === undefined || row.localMedfeeSumamt === null
        ? undefined
        : toMoneyNumber(row.localMedfeeSumamt);
    const localFundPaySumamt =
      row.localFundPaySumamt === undefined || row.localFundPaySumamt === null
        ? undefined
        : toMoneyNumber(row.localFundPaySumamt);
    const localAcctPay =
      row.localAcctPay === undefined || row.localAcctPay === null
        ? undefined
        : toMoneyNumber(row.localAcctPay);
    const amountsMatch =
      !isAmountDiff(localMedfeeSumamt, medfeeSumamt) &&
      !isAmountDiff(localFundPaySumamt, fundPaySumamt) &&
      !isAmountDiff(localAcctPay, acctPay);
    const kind = classifyDetailMemo(
      row.memo,
      {
        medfeeSumamt,
        fundPaySumamt,
        acctPay,
      },
      {
        localStatus: row.localStatus,
        refdSetlFlag: row.refdSetlFlag,
        amountsMatch,
      },
    );
    mapped.push({
      setlId: row.setlId || '—',
      psnNo: row.psnNo,
      psnName: row.psnName?.trim() || undefined,
      mdtrtId: row.mdtrtId,
      setlTime: row.setlTime?.trim() || undefined,
      localStatus: row.localStatus?.trim() || undefined,
      localExists: row.localExists === true ? true : row.localExists === false ? false : undefined,
      localMedfeeSumamt,
      localFundPaySumamt,
      localAcctPay,
      refdSetlFlag: row.refdSetlFlag,
      stmtRslt: row.stmtRslt,
      memo: row.memo,
      kind,
      kindLabel: formatDetailKindLabel(kind),
      medfeeSumamt,
      fundPaySumamt,
      acctPay,
    });
  }
  // 需关注在前，便于「全部」视图扫读
  mapped.sort((a, b) => {
    const aAtt = isDetailAttention(a.kind) ? 0 : 1;
    const bAtt = isDetailAttention(b.kind) ? 0 : 1;
    return aAtt - bAtt;
  });
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
      stmtBegndate: row.stmtBegndate,
      stmtEnddate: row.stmtEnddate,
      insutype: row.insutype,
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
    prepareDetailDialog(mismatches);
    const attention = mismatches.filter((item) => isDetailAttention(item.kind)).length;
    detailDialogTitle.value = attention > 0 ? '明细对账结果（有差异）' : '明细对账结果';
    detailVisible.value = true;
    emit('reconciled');
    if (attention > 0) {
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
  prepareDetailDialog(mismatches);
  const attention = mismatches.filter((item) => isDetailAttention(item.kind)).length;
  detailDialogTitle.value = attention > 0 ? '明细核对（有差异）' : '明细核对';
  detailVisible.value = true;
}

onMounted(() => {
  // 进入页不自动查询，避免切换机构/日期误打接口
  emitReconDate();
});
</script>

<template>
  <div
    class="daily-ledger-list"
    :class="{
      'daily-ledger-list--compact': compact,
      'daily-ledger-list--placeholder': !queried && !loading,
    }"
  >
    <TableLayout
      class="daily-ledger-list__table !p-0"
      :table-data="queried ? tableData : []"
      :loading="loading || reconciling || detailReconciling"
      :total="queried ? tableData.length : 0"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-default-buttons="false"
      :show-pagination="false"
      :border="false"
      :stripe="false"
      :max-height="compact ? 280 : 480"
      :size="compact ? 'small' : 'default'"
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
            v-if="compact"
            :model-value="queryParams.dateRange?.[0]"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            class="recon-filter-control recon-filter-control--date"
            @update:model-value="handleCompactDateChange"
          />
          <ElDatePicker
            v-else
            :model-value="queryParams.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            :clearable="false"
            class="recon-filter-control recon-filter-control--range"
            @update:model-value="handleReconRangeChange"
          />
          <ElButtonGroup>
            <ElButton @click="setDatePreset('yesterday')">昨天</ElButton>
            <ElButton @click="setDatePreset('today')">今天</ElButton>
            <ElButton v-if="!compact" @click="setDatePreset('week')">近7天</ElButton>
            <ElButton v-if="!compact" @click="setDatePreset('month')">本月</ElButton>
          </ElButtonGroup>
        </div>
      </template>

      <template #reconDaySlot="{ row }">
        {{ formatDateRangeLabel(row.stmtBegndate, row.stmtEnddate) }}
      </template>

      <template #reconActionsSlot>
        <div class="flex flex-wrap items-center gap-2">
          <ElButton type="primary" :loading="loading" @click="handleQuery">查询</ElButton>
          <span
            v-if="queried && unbalancedCount > 0"
            class="text-sm"
            style="color: var(--el-color-danger)"
          >
            不平 {{ unbalancedCount }} 条
          </span>
          <ElButton
            type="primary"
            plain
            :loading="reconciling"
            :disabled="!queried || (pendingCount === 0 && unbalancedCount === 0)"
            @click="handleReconcileAll"
          >
            全部对账{{ queried && pendingCount > 0 ? `（${pendingCount}）` : '' }}
          </ElButton>
          <ElButton
            v-if="queried && !compact"
            :type="amountsEditable ? 'warning' : 'default'"
            plain
            @click="amountsEditable = !amountsEditable"
          >
            {{ amountsEditable ? '锁定金额' : '改金额' }}
          </ElButton>
        </div>
      </template>

      <template #medfeeSlot="{ row }">
        <ElInputNumber
          v-if="compact || amountsEditable"
          v-model="row.medfeeSumamt"
          :precision="2"
          :step="0.01"
          :controls="false"
          size="small"
          class="daily-ledger-list__amt"
        />
        <span v-else class="daily-ledger-list__amt-text">{{ formatMoney(row.medfeeSumamt) }}</span>
      </template>
      <template #fundSlot="{ row }">
        <ElInputNumber
          v-if="compact || amountsEditable"
          v-model="row.fundPaySumamt"
          :precision="2"
          :step="0.01"
          :controls="false"
          size="small"
          class="daily-ledger-list__amt"
        />
        <span v-else class="daily-ledger-list__amt-text">{{ formatMoney(row.fundPaySumamt) }}</span>
      </template>
      <template #cashSlot="{ row }">
        <span class="daily-ledger-list__amt-text">{{ formatMoney(row.cashPayamt) }}</span>
      </template>
      <template #acctSlot="{ row }">
        <ElInputNumber
          v-if="compact || amountsEditable"
          v-model="row.acctPay"
          :precision="2"
          :step="0.01"
          :controls="false"
          size="small"
          class="daily-ledger-list__amt"
        />
        <span v-else class="daily-ledger-list__amt-text">{{ formatMoney(row.acctPay) }}</span>
      </template>
      <template #setlCountSlot="{ row }">
        <ElInputNumber
          v-if="compact || amountsEditable"
          v-model="row.setlCount"
          :min="0"
          :step="1"
          :controls="false"
          size="small"
          class="daily-ledger-list__setl-count"
          title="按平台明细笔数填写；本地缺收完即退测试单时可改大"
        />
        <span v-else class="daily-ledger-list__amt-text">{{ row.setlCount ?? '—' }}</span>
        <div
          v-if="row.setlCount !== row.localSetlCount"
          class="text-xs"
          style="color: var(--el-color-warning)"
        >
          本地 {{ row.localSetlCount }}
        </div>
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
            v-if="row.status === RECON_STATUS.UNBALANCED || row.status === RECON_STATUS.BALANCED"
            type="primary"
            link
            :loading="reconciling"
            @click="handleReconcileRow(row)"
          >
            {{ compact ? '重对' : '重新对账' }}
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
        </div>
      </template>
    </TableLayout>

    <div v-if="!queried && !loading" class="daily-ledger-list__placeholder">
      <ElEmpty
        :image-size="compact ? 64 : 88"
        description="请选择清算机构、对账日期与业务类型后点击「查询」；选多天时会按天拆成多行，仍按日对账"
      />
    </div>

    <AppDialog
      v-model="detailVisible"
      :title="detailDialogTitle"
      width="96%"
      top="3vh"
      class="detail-recon-dialog"
    >
      <template #default>
        <p v-if="detailRow" class="mb-2 text-sm" style="color: var(--el-text-color-regular)">
          {{ detailRow.insutypeText }} · {{ detailRow.clrTypeText }} ·
          {{ formatDateRangeLabel(detailRow.stmtBegndate, detailRow.stmtEnddate) }}
          <span v-if="detailResultDesc"> — {{ detailResultDesc }}</span>
        </p>
        <p class="mb-2 text-xs" style="color: var(--el-text-color-secondary)">
          对照：左为本院本地结算回执，右为医保中心 3202 回写（非完整日结镜像）
        </p>
        <div
          v-if="detailList.length"
          class="mb-3 flex flex-wrap items-center justify-between gap-2"
        >
          <ElRadioGroup v-model="detailFilter" size="small">
            <ElRadioButton value="attention"> 需关注 {{ detailAttentionCount }} </ElRadioButton>
            <ElRadioButton value="noise"> 可忽略 {{ detailNoiseCount }} </ElRadioButton>
            <ElRadioButton value="all"> 全部 {{ detailList.length }} </ElRadioButton>
          </ElRadioGroup>
          <ElRadioGroup v-model="detailViewMode" size="small">
            <ElRadioButton value="list">列表</ElRadioButton>
            <ElRadioButton value="side">左右对照</ElRadioButton>
          </ElRadioGroup>
        </div>

        <!-- 列表模式 -->
        <ElTable
          v-if="detailViewMode === 'list' && detailFilteredList.length"
          :data="detailFilteredList"
          border
          stripe
          size="small"
          row-key="setlId"
          highlight-current-row
          :current-row-key="selectedSetlId"
          class="detail-mismatch-table"
          @row-click="selectDetailRow"
        >
          <ElTableColumn label="分类" width="148" align="center">
            <template #default="{ row }">
              <ElTag :type="detailKindTagType(row.kind)" size="small" effect="light">
                {{ row.kindLabel || formatDetailKindLabel(row.kind) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结算时间" width="158" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.setlTime || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="人员姓名" width="88" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.psnName || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="psnNo" label="人员编号" min-width="160" show-overflow-tooltip />
          <ElTableColumn prop="setlId" label="结算号" min-width="220" show-overflow-tooltip />
          <ElTableColumn prop="mdtrtId" label="就诊号" min-width="180" show-overflow-tooltip />
          <ElTableColumn label="医疗费" width="92" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.medfeeSumamt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="基金" width="88" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.fundPaySumamt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="个账" width="88" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.acctPay) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="本地状态" width="80" align="center">
            <template #default="{ row }">
              <span :class="localStatusCellClass(row)">
                {{ formatLocalStatus(row.localStatus) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="退费标志" width="80" align="center">
            <template #default="{ row }">
              <span :class="localStatusCellClass(row)">
                {{ formatRefdFlag(row.refdSetlFlag) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果" width="64" align="center">
            <template #default="{ row }">
              {{ formatStmtRslt(row.stmtRslt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="说明" min-width="160">
            <template #default="{ row }">
              <ElTooltip v-if="row.memo" :content="row.memo" placement="top" :show-after="300">
                <span class="detail-memo-short">
                  {{ row.kindLabel || formatDetailKindLabel(row.kind) }}
                </span>
              </ElTooltip>
              <span v-else>{{ row.kindLabel || '—' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="72" align="center" fixed="right">
            <template #default="{ row }">
              <ElButton link type="primary" size="small" @click.stop="openDetailCompare(row)">
                对照
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <!-- 左右分栏表 -->
        <div
          v-else-if="detailViewMode === 'side' && detailFilteredList.length"
          class="detail-side-compare"
        >
          <div class="detail-side-compare__pane">
            <div class="detail-side-compare__title">本院</div>
            <ElTable
              :data="detailFilteredList"
              border
              stripe
              size="small"
              row-key="setlId"
              highlight-current-row
              :current-row-key="selectedSetlId"
              max-height="280"
              class="detail-side-compare__table"
              @row-click="selectDetailRow"
            >
              <ElTableColumn label="姓名" width="68" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.psnName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="时间" width="136" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.setlTime || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="setlId" label="结算号" min-width="200" show-overflow-tooltip />
              <ElTableColumn label="状态" width="64" align="center">
                <template #default="{ row }">
                  <span :class="localStatusCellClass(row)">
                    {{ row.localExists === false ? '无' : formatLocalStatus(row.localStatus) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="医疗费" width="76" align="right">
                <template #default="{ row }">
                  <span :class="amountCellClass(row, 'local', 'medfee')">
                    {{ row.localExists === false ? '—' : formatMoney(row.localMedfeeSumamt) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="基金" width="68" align="right">
                <template #default="{ row }">
                  <span :class="amountCellClass(row, 'local', 'fund')">
                    {{ row.localExists === false ? '—' : formatMoney(row.localFundPaySumamt) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="个账" width="68" align="right">
                <template #default="{ row }">
                  <span :class="amountCellClass(row, 'local', 'acct')">
                    {{ row.localExists === false ? '—' : formatMoney(row.localAcctPay) }}
                  </span>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
          <div class="detail-side-compare__pane">
            <div class="detail-side-compare__title">中心</div>
            <ElTable
              :data="detailFilteredList"
              border
              stripe
              size="small"
              row-key="setlId"
              highlight-current-row
              :current-row-key="selectedSetlId"
              max-height="280"
              class="detail-side-compare__table"
              @row-click="selectDetailRow"
            >
              <ElTableColumn label="退费标志" width="72" align="center">
                <template #default="{ row }">
                  <span :class="localStatusCellClass(row)">
                    {{ formatRefdFlag(row.refdSetlFlag) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="setlId" label="结算号" min-width="200" show-overflow-tooltip />
              <ElTableColumn label="结果" width="56" align="center">
                <template #default="{ row }">
                  {{ formatStmtRslt(row.stmtRslt) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="医疗费" width="76" align="right">
                <template #default="{ row }">
                  <span :class="amountCellClass(row, 'platform', 'medfee')">
                    {{ formatMoney(row.medfeeSumamt) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="基金" width="68" align="right">
                <template #default="{ row }">
                  <span :class="amountCellClass(row, 'platform', 'fund')">
                    {{ formatMoney(row.fundPaySumamt) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="个账" width="68" align="right">
                <template #default="{ row }">
                  <span :class="amountCellClass(row, 'platform', 'acct')">
                    {{ formatMoney(row.acctPay) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="说明" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.kindLabel || row.memo || '—' }}
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </div>

        <ElEmpty
          v-else-if="detailList.length"
          :description="
            detailFilter === 'attention'
              ? '当前筛选下无待处理差异，可切换到「可忽略」或「全部」'
              : '当前筛选下无明细'
          "
          :image-size="72"
          class="!py-4"
        />
        <ElEmpty v-else description="无差异明细" :image-size="72" class="!py-4" />

        <!-- 字段对照条 -->
        <div v-if="selectedDetail" class="detail-field-compare mt-3">
          <div class="detail-field-compare__head">
            <span>字段对照</span>
            <span class="detail-field-compare__meta">
              {{ selectedDetail.psnName || '—' }} · {{ selectedDetail.setlId }}
            </span>
          </div>
          <table class="detail-field-compare__table">
            <thead>
              <tr>
                <th class="detail-field-compare__label">字段</th>
                <th>本院</th>
                <th>中心</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="field in selectedCompareFields"
                :key="field.key"
                :class="{ 'detail-field-compare__row--diff': field.diff }"
              >
                <td class="detail-field-compare__label">{{ field.label }}</td>
                <td :class="sideCellClass(field.diff)">{{ field.localValue }}</td>
                <td :class="sideCellClass(field.diff)">{{ field.platformValue }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <template #footer>
        <ElButton type="primary" @click="detailVisible = false">关闭</ElButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.daily-ledger-list__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 12px 8px 24px;
}

.detail-memo-short {
  cursor: help;
  border-bottom: 1px dashed var(--el-border-color);
}

.detail-compare-cell--diff {
  font-weight: 600;
  color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
}

.detail-side-compare {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
}

.detail-side-compare__pane {
  min-width: 0;
  overflow-x: auto;
}

.detail-side-compare__title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-side-compare__table {
  width: max-content;
  min-width: 100%;
}

.detail-field-compare {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
}

.detail-field-compare__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color);
}

.detail-field-compare__meta {
  font-weight: 400;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.detail-field-compare__table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 13px;
}

.detail-field-compare__table th,
.detail-field-compare__table td {
  padding: 6px 10px;
  text-align: left;
  border-bottom: 1px solid var(--el-border-color-lighter);
  vertical-align: top;
  word-break: break-all;
}

.detail-field-compare__table th {
  font-weight: 600;
  background: var(--el-fill-color-blank);
}

.detail-field-compare__label {
  width: 118px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.detail-field-compare__table th:nth-child(2),
.detail-field-compare__table td:nth-child(2),
.detail-field-compare__table th:nth-child(3),
.detail-field-compare__table td:nth-child(3) {
  width: calc((100% - 118px) / 2);
}

.detail-field-compare__row--diff td:not(.detail-field-compare__label) {
  font-weight: 600;
  color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
}

.detail-mismatch-table {
  cursor: pointer;
}

.daily-ledger-list--placeholder :deep(.vxe-grid),
.daily-ledger-list--placeholder :deep(.vxe-table) {
  display: none;
}

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
  overflow: visible;
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

.recon-filter-control--range {
  width: 280px;
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

/* 对账清算页（非 compact）：字号对齐工伤对账，避免标签/险种截断 */
.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.el-form-item__label) {
  width: 108px !important;
  min-width: 108px;
  height: 40px;
  line-height: 40px;
  font-size: 16px;
}

.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.el-input__inner),
.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.el-select__selected-item),
.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.el-range-input),
.daily-ledger-list:not(.daily-ledger-list--compact) .daily-ledger-list__table :deep(.el-button) {
  font-size: 16px;
}

.daily-ledger-list:not(.daily-ledger-list--compact) .daily-ledger-list__table :deep(.vxe-grid) {
  --vxe-ui-font-size-small: 16px;
  --vxe-ui-table-row-height-small: 48px;
  font-size: 16px;
}

.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.vxe-header--column) {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.vxe-body--column) {
  height: 48px;
  font-size: 17px;
}

.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.vxe-header--column .vxe-cell),
.daily-ledger-list:not(.daily-ledger-list--compact)
  .daily-ledger-list__table
  :deep(.vxe-body--column .vxe-cell) {
  height: 48px;
  padding: 12px 10px;
  line-height: 24px;
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

.daily-ledger-list__setl-count {
  width: 88px;
}

.daily-ledger-list__amt {
  width: 100px;
}

.daily-ledger-list__amt-text {
  font-variant-numeric: tabular-nums;
  font-size: inherit;
}

.daily-ledger-list__actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  justify-content: center;
}
</style>

<!-- append-to-body 弹窗需非 scoped，否则 body 限高不生效 -->
<style>
.detail-recon-dialog.el-dialog {
  max-width: 1600px;
  margin-bottom: 2vh;
}

.detail-recon-dialog .el-dialog__body {
  max-height: calc(94vh - 130px);
  overflow-x: hidden;
  overflow-y: auto;
}
</style>
