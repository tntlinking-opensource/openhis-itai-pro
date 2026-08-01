<!--
  调价单右侧主区：单头/明细 + useVbenVxeGrid；单据操作吸底（同构药库进货单）。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';

import type { AdjustableItemRowDto } from '#/api/finance/price-adjustment';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import { Delete, Plus } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  approvePriceAdjustmentApi,
  checkInTransitForSubmitApi,
  loadClinicalOldPriceApi,
  loadLogisticsAdjustmentSnapshotApi,
  rejectPriceAdjustmentApi,
  savePriceAdjustmentDraftApi,
  submitPriceAdjustmentForApprovalApi,
  withdrawPriceAdjustmentDraftApi,
} from '#/api/finance/price-adjustment';
import {
  buildClinicalLine,
  buildLogisticsLineFromSnapshot,
  buildSaveDraftRequest,
} from '#/api/finance/price-adjustment-page';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import SectionTitle from '#/components/layouts/section-title.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { printPriceAdjustment } from '#/utils/print/print-price-adjustment';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

import PriceAdjustmentApproveDialog from './components/price-adjustment-approve-dialog.vue';
import PriceAdjustmentLineLotExpand from './components/price-adjustment-line-lot-expand.vue';
import type { PriceAdjustmentHeadForm, PriceAdjustmentLineRow } from './types';
import {
  isClinicalItemTable,
  isLogisticsItemTable,
  resolveAdjustmentStyle,
} from './utils/price-adjustment-item-table';
import {
  buildPriceAdjustmentDocActionFlags,
  priceAdjustmentStatusLabel,
} from './utils/price-adjustment-status';

defineOptions({ name: 'PriceAdjustmentMainPane' });

interface Props {
  adjustmentDetailRows?: PriceAdjustmentLineRow[];
  adjustmentReasonOptions?: ApiOptionItem[];
  auditForm?: Record<string, unknown>;
  busNo?: string;
  documentEditable?: boolean;
  headMeta?: Partial<PriceAdjustmentHeadForm>;
  isDraftOrder?: boolean;
  itemTableTabOptions?: ApiOptionItem[];
  defaultOrgId?: string;
  resetKey?: number;
}

const props = withDefaults(defineProps<Props>(), {
  adjustmentDetailRows: () => [],
  adjustmentReasonOptions: () => [],
  auditForm: () => ({}),
  busNo: '',
  documentEditable: true,
  headMeta: () => ({}),
  isDraftOrder: false,
  itemTableTabOptions: () => [],
  defaultOrgId: '',
  resetKey: 0,
});

const emit = defineEmits<{
  documentChanged: [payload: { busNo?: string }];
  documentRemoved: [];
}>();

const headForm = ref<PriceAdjustmentHeadForm>(defaultHeadForm());
const pickItemTable = ref('');
const approveDialogVisible = ref(false);
const approveDialogMode = ref<'approve' | 'reject'>('approve');
const paneFlashActive = ref(false);
/** 明细行数（与网格同步；避免 getFullData 非响应导致汇总「共 0 行」） */
const displayLineCount = ref(0);
let paneFlashTimer: ReturnType<typeof setTimeout> | undefined;

function defaultHeadForm(): PriceAdjustmentHeadForm {
  return {
    busNo: '',
    orgId: props.defaultOrgId,
    adjustmentTime: formatDateTime(new Date()),
    adjustmentReason: '',
  };
}

const detailStatusEnum = computed(
  () => props.auditForm?.statusEnum ?? (props.isDraftOrder ? 'draft' : undefined),
);

const headerStatusText = computed(() => {
  if (props.isDraftOrder) return '编辑中';
  const text = props.auditForm?.statusEnumText;
  if (text !== undefined && String(text).trim() !== '') return String(text);
  return priceAdjustmentStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() =>
  buildPriceAdjustmentDocActionFlags({
    documentEditable: props.documentEditable,
    hasPersistedBusNo: Boolean(String(props.busNo ?? headForm.value.busNo ?? '').trim()),
    isDraft: props.isDraftOrder,
    statusRaw: detailStatusEnum.value,
  }),
);

/** 行内选品检索用的项目表（默认取初始化首项） */
const resolvedPickItemTable = computed(() => {
  const current = pickItemTable.value.trim();
  if (current) return current;
  return String(props.itemTableTabOptions[0]?.value ?? '');
});

/** 盈亏汇总（进价 + 售价） */
const impactSummary = computed(() => {
  const purchase = Number(props.auditForm?.totalPurchaseImpact ?? 0);
  const selling = Number(props.auditForm?.totalSellingImpact ?? 0);
  return {
    totalPurchaseImpact: purchase,
    totalSellingImpact: selling,
  };
});

const approveSummaryText = computed(() => {
  return `进价盈亏合计 ${formatMoneyDisplay(impactSummary.value.totalPurchaseImpact)} 元 · 售价盈亏合计 ${formatMoneyDisplay(impactSummary.value.totalSellingImpact)} 元`;
});

/** 用网格全量数据刷新底部行数展示 */
function refreshDisplayLineCount() {
  displayLineCount.value = collectGridRows().length;
}

function triggerPaneFlash() {
  if (paneFlashTimer !== undefined) clearTimeout(paneFlashTimer);
  paneFlashActive.value = true;
  paneFlashTimer = setTimeout(() => {
    paneFlashActive.value = false;
    paneFlashTimer = undefined;
  }, 420);
}

function isLogisticsRow(row: PriceAdjustmentLineRow): boolean {
  if (row.adjustmentStyle === 'logistics') return true;
  if (row.adjustmentStyle === 'clinical') return false;
  return isLogisticsItemTable(row.itemTable);
}

function formatAmount(value: unknown, placeholder = '—'): string {
  if (value === undefined || value === null || value === '') {
    return placeholder;
  }
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return placeholder;
  }
  return formatMoneyDisplay(n);
}

function formatQuantity(value: unknown, placeholder = '—'): string {
  if (value === undefined || value === null || value === '') {
    return placeholder;
  }
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return placeholder;
  }
  return String(n);
}

function isEmptyEditablePrice(value: unknown): boolean {
  return value === undefined || value === null || value === '';
}

/** 可编辑价格列单元格样式（物流进价列非物流行只读） */
function resolveEditablePriceCellClass(params: {
  column?: { field?: string };
  row?: PriceAdjustmentLineRow;
}): string {
  const field = params.column?.field;
  const row = params.row;
  if (field === 'newPurchaseAmount') {
    if (!row || !isLogisticsRow(row)) {
      return 'price-adjustment-readonly-cell';
    }
    const classes = ['price-adjustment-editable-cell'];
    if (props.documentEditable && isEmptyEditablePrice(row.newPurchaseAmount)) {
      classes.push('price-adjustment-editable-cell--empty');
    }
    return classes.join(' ');
  }
  if (field === 'newSellingAmount' && row) {
    const classes = ['price-adjustment-editable-cell'];
    if (props.documentEditable && isEmptyEditablePrice(row.newSellingAmount)) {
      classes.push('price-adjustment-editable-cell--empty');
    }
    return classes.join(' ');
  }
  return '';
}

/** 调后价格展示：空值在可编辑态提示「请填写」 */
function formatEditablePrice(value: unknown, editable: boolean, placeholder = '请填写'): string {
  if (isEmptyEditablePrice(value)) {
    return editable ? placeholder : '—';
  }
  return formatAmount(value);
}

const PRICE_ADJUSTMENT_EDITABLE_FIELDS = new Set([
  'itemPick',
  'newPurchaseAmount',
  'newSellingAmount',
]);

/** 单元格是否允许进入编辑（物流才可调进价） */
function beforePriceAdjustmentCellEdit(params: {
  column?: { field?: string };
  row?: PriceAdjustmentLineRow;
}): boolean {
  if (!props.documentEditable) return false;
  const field = params.column?.field;
  const row = params.row;
  if (!field || !row) return false;
  if (field === 'itemPick') return true;
  if (!PRICE_ADJUSTMENT_EDITABLE_FIELDS.has(field)) return false;
  if (field === 'newPurchaseAmount') {
    return isLogisticsRow(row);
  }
  return field === 'newSellingAmount';
}

function pickApiErrMsg(e: unknown): string {
  if (e && typeof e === 'object' && 'message' in e) {
    const msg = (e as { message?: unknown }).message;
    if (typeof msg === 'string' && msg.trim() !== '') return msg;
  }
  return '操作失败';
}

function newLineKey(): string {
  return `line:${Date.now()}:${Math.random().toString(36).slice(2, 7)}`;
}

function collectGridRows(): PriceAdjustmentLineRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => PriceAdjustmentLineRow[] };
  return g?.getFullData?.() ?? [];
}

function applyEmptyState() {
  headForm.value = defaultHeadForm();
  headForm.value.busNo = props.busNo ?? '';
  headForm.value.adjustmentTime = formatDateTime(new Date());
  headForm.value.orgId = props.defaultOrgId;
  if (props.adjustmentReasonOptions.length > 0) {
    headForm.value.adjustmentReason = String(props.adjustmentReasonOptions[0]?.value ?? '');
  }
  if (!pickItemTable.value.trim() && props.itemTableTabOptions.length > 0) {
    pickItemTable.value = String(props.itemTableTabOptions[0]?.value ?? '');
  }
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGridEditConfig();
    refreshDisplayLineCount();
  });
}

/** 从父级同步单头与表格 */
function syncFromParent() {
  if (props.isDraftOrder && props.adjustmentDetailRows.length === 0) {
    applyEmptyState();
    return;
  }
  headForm.value.busNo = String(props.busNo ?? props.headMeta?.busNo ?? '');
  headForm.value.orgId = String(props.headMeta?.orgId ?? props.defaultOrgId ?? '');
  headForm.value.adjustmentTime = String(
    props.headMeta?.adjustmentTime ?? formatDateTime(new Date()),
  );
  headForm.value.adjustmentReason = String(props.headMeta?.adjustmentReason ?? '');
  const lines = props.adjustmentDetailRows.map((line) => ({
    ...line,
    itemPick:
      line.itemPick ?? (line.itemId ? { id: String(line.itemId), name: line.itemName } : undefined),
  }));
  // 先按父级行数回填，避免网格 nextTick 加载前底部显示「共 0 行」
  displayLineCount.value = lines.length;
  void nextTick(() => {
    gridApi?.grid?.loadData(lines);
    patchGridEditConfig();
    refreshDisplayLineCount();
  });
}

/** 校验明细行 */
async function validateLines(): Promise<boolean> {
  const rows = collectGridRows();
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一条调价明细');
    return false;
  }
  for (const row of rows) {
    if (!row.itemTable || !row.itemId) {
      ElMessage.warning('明细行缺少项目信息');
      return false;
    }
    if (isLogisticsRow(row)) {
      const purchase = row.newPurchaseAmount;
      const selling = row.newSellingAmount;
      if (
        (purchase === undefined || purchase === null || purchase === '') &&
        (selling === undefined || selling === null || selling === '')
      ) {
        ElMessage.warning(`请填写「${row.itemName ?? ''}」的目标进价或目标售价`);
        return false;
      }
      if (!row.details || row.details.length === 0) {
        ElMessage.warning(`「${row.itemName ?? ''}」缺少批号快照，请重新添加`);
        return false;
      }
    } else {
      if (
        row.newSellingAmount === undefined ||
        row.newSellingAmount === null ||
        row.newSellingAmount === ''
      ) {
        ElMessage.warning(`请填写「${row.itemName ?? ''}」的目标售价`);
        return false;
      }
    }
  }
  if (!headForm.value.orgId?.trim()) {
    headForm.value.orgId = props.defaultOrgId;
  }
  if (!headForm.value.adjustmentReason?.trim()) {
    ElMessage.warning('请选择调价原因');
    return false;
  }
  return true;
}

function currentPersistedBusNo(): string | undefined {
  const bn = String(props.busNo ?? headForm.value.busNo ?? '').trim();
  return bn !== '' ? bn : undefined;
}

/** 保存草稿（IF-PRC-09） */
async function persistDocument(submitApproval: boolean): Promise<string | undefined> {
  if (!props.isDraftOrder && !props.documentEditable) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  if (!(await validateLines())) return undefined;
  let body;
  try {
    body = buildSaveDraftRequest({
      busNo: props.isDraftOrder ? undefined : currentPersistedBusNo(),
      head: { ...headForm.value },
      lines: collectGridRows(),
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '参数不完整');
    return undefined;
  }
  try {
    const res = await savePriceAdjustmentDraftApi(body);
    const busNo = res.data?.busNo?.trim();
    if (!busNo) {
      ElMessage.warning('保存成功但未返回单号');
      return undefined;
    }
    headForm.value.busNo = busNo;
    if (submitApproval) {
      const transit = await checkInTransitForSubmitApi({ busNo });
      if (transit.data?.check?.blocked) {
        ElMessage.error(transit.data.check.reasonMessage ?? '存在在途业务，无法提交');
        return busNo;
      }
      await submitPriceAdjustmentForApprovalApi({ busNo });
    }
    return busNo;
  } catch (error) {
    ElMessage.error(pickApiErrMsg(error));
    return undefined;
  }
}

async function handleSaveDoc() {
  const busNo = await persistDocument(false);
  if (!busNo) return;
  ElMessage.success('保存成功');
  emit('documentChanged', { busNo });
}

async function handleSaveAndSubmitDoc() {
  const busNo = await persistDocument(true);
  if (!busNo) return;
  ElMessage.success('保存并提交审批成功');
  emit('documentChanged', { busNo });
}

async function handleSubmitApproval() {
  if (props.isDraftOrder) {
    ElMessage.warning('请先保存单据');
    return;
  }
  const busNo = currentPersistedBusNo();
  if (!busNo) {
    ElMessage.warning('请先选择或保存单据');
    return;
  }
  try {
    await ElMessageBox.confirm(approveSummaryText.value, '确认提交审批？', { type: 'warning' });
  } catch {
    return;
  }
  try {
    const transit = await checkInTransitForSubmitApi({ busNo });
    if (transit.data?.check?.blocked) {
      ElMessage.error(transit.data.check.reasonMessage ?? '存在在途业务，无法提交');
      return;
    }
    await submitPriceAdjustmentForApprovalApi({ busNo });
    ElMessage.success('已提交审批');
    emit('documentChanged', { busNo });
  } catch (error) {
    ElMessage.error(pickApiErrMsg(error));
  }
}

/** 删除本地草稿或撤销已保存草稿 */
async function handleRemoveDoc() {
  if (props.isDraftOrder) {
    try {
      await ElMessageBox.confirm('确定删除当前调价单？', '删除单据', { type: 'warning' });
    } catch {
      return;
    }
    emit('documentRemoved');
    return;
  }
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    await ElMessageBox.confirm('撤销后单据将变为已废弃，是否继续？', '撤销调价单', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await withdrawPriceAdjustmentDraftApi({ busNo });
    ElMessage.success('已撤销');
    emit('documentRemoved');
  } catch (error) {
    ElMessage.error(pickApiErrMsg(error));
  }
}

async function handleWithdraw() {
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    await ElMessageBox.confirm('撤销后单据将变为已废弃，是否继续？', '撤销调价单', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await withdrawPriceAdjustmentDraftApi({ busNo });
    ElMessage.success('已撤销');
    emit('documentChanged', { busNo });
  } catch (error) {
    ElMessage.error(pickApiErrMsg(error));
  }
}

function openApproveDialog(mode: 'approve' | 'reject') {
  approveDialogMode.value = mode;
  approveDialogVisible.value = true;
}

async function handleApproveDialogConfirm(comment: string) {
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    if (approveDialogMode.value === 'reject') {
      if (comment.trim() === '') {
        ElMessage.warning('拒绝时请填写审批意见');
        return;
      }
      await rejectPriceAdjustmentApi({ busNo, approveComment: comment.trim() });
      ElMessage.success('已拒绝');
    } else {
      await approvePriceAdjustmentApi({ busNo, approveComment: comment.trim() || undefined });
      ElMessage.success('审批通过，调价已生效');
    }
    emit('documentChanged', { busNo });
  } catch (error) {
    ElMessage.error(pickApiErrMsg(error));
  }
}

function resolveOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const code = String(value ?? '').trim();
  if (!code) return '';
  const hit = (options ?? []).find((opt) => String(opt.value ?? '').trim() === code);
  return String(hit?.label ?? code).trim();
}

/** 打印：按当前页单头/明细/审批区组装 PrintData */
async function handlePrint() {
  const busNo = currentPersistedBusNo();
  if (!busNo) {
    ElMessage.warning('请先保存单据后再打印');
    return;
  }
  const rows = collectGridRows().filter((row) => Boolean(String(row.itemId ?? '').trim()));
  if (rows.length === 0) {
    ElMessage.warning('没有可打印的明细');
    return;
  }

  const adjustmentReasonText = resolveOptionLabel(
    props.adjustmentReasonOptions,
    headForm.value.adjustmentReason,
  );
  const lineTypeTexts = [
    ...new Set(
      rows.map((row) => String(row.itemTableText ?? '').trim()).filter((text) => text !== ''),
    ),
  ];
  const itemTypeText =
    lineTypeTexts.length > 0
      ? lineTypeTexts.join('/')
      : resolveOptionLabel(props.itemTableTabOptions, pickItemTable.value);
  const applyTimeText = String(headForm.value.adjustmentTime ?? '')
    .trim()
    .replace('T', ' ');
  const preparedByName = String(props.auditForm?.submitterName ?? '').trim();
  const approvedByName = String(props.auditForm?.approverName ?? '').trim();

  await printPriceAdjustment({
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle: '调价单',
      docNo: busNo,
      applyTimeText,
      adjustmentReasonText,
      itemTypeText,
      statusText: headerStatusText.value,
      approvedByName,
      preparedByName,
      totalPurchaseImpact: impactSummary.value.totalPurchaseImpact,
      totalSellingImpact: impactSummary.value.totalSellingImpact,
      lineRows: rows.map((row) => ({
        itemName: String(row.itemPick?.name ?? row.itemName ?? '').trim(),
        specText: String(row.specification ?? '').trim(),
        categoryText: String(row.categoryCodeDictText ?? '').trim(),
        oldPurchasePrice: row.currentPurchasePrice,
        oldSellingPrice: row.currentSellingPrice ?? row.oldSellingAmount,
        quantity: row.quantity,
        newPurchasePrice: row.newPurchaseAmount,
        newSellingPrice: row.newSellingAmount,
      })),
    },
    bizRef: { busNo, docKind: 'price-adjustment' },
    jobName: `调价单-${busNo}`,
  });
}

function handleAddItemClick() {
  void addItem();
}

/** 是否与其他行重复选品 */
function isDuplicateLineItem(
  item: AdjustableItemRowDto,
  currentRow: PriceAdjustmentLineRow,
): boolean {
  const itemId = preserveApiIdForRequest(item.itemId);
  const itemTable = String(item.itemTable ?? '').trim();
  if (!itemId || !itemTable) return false;
  return collectGridRows().some((row) => {
    if (row === currentRow) return false;
    return (
      preserveApiIdForRequest(row.itemId) === itemId &&
      String(row.itemTable ?? '').trim() === itemTable
    );
  });
}

/** 行内选品确认：加载快照/调前价并回填当前行 */
async function syncRowFromItemPick(item: AdjustableItemRowDto, row: PriceAdjustmentLineRow) {
  if (isDuplicateLineItem(item, row)) {
    ElMessage.warning('该项目已在明细中，请勿重复添加');
    row.itemPick = undefined;
    row.itemId = undefined;
    row.itemName = undefined;
    return;
  }

  const itemId = preserveApiIdForRequest(item.itemId);
  const itemTable = String(item.itemTable ?? '').trim();
  if (!itemTable || !itemId) return;

  row.itemPick = { id: itemId, name: item.itemName };
  row.itemTable = itemTable;
  row.itemId = itemId;
  row.itemName = item.itemName;
  row.newPurchaseAmount = undefined;
  row.newSellingAmount = undefined;
  row.details = [];

  const itemWithStyle: AdjustableItemRowDto = {
    ...item,
    itemTable,
    adjustmentStyle: item.adjustmentStyle ?? resolveAdjustmentStyle(itemTable),
  };

  if (isLogisticsItemTable(itemTable)) {
    try {
      const snapRes = await loadLogisticsAdjustmentSnapshotApi({
        itemTable,
        itemIds: [itemId],
      });
      const built = buildLogisticsLineFromSnapshot(itemWithStyle, snapRes.data?.rows ?? []);
      Object.assign(row, built, { id: row.id, itemPick: row.itemPick });
    } catch (error) {
      ElMessage.error(pickApiErrMsg(error));
    }
    return;
  }

  if (!isClinicalItemTable(itemTable)) {
    return;
  }

  let oldSellingAmount: number | undefined;
  try {
    const priceRes = await loadClinicalOldPriceApi({ itemTable, itemId });
    oldSellingAmount = priceRes.data?.oldSellingAmount;
  } catch {
    oldSellingAmount = undefined;
  }
  const built = buildClinicalLine(itemWithStyle, oldSellingAmount);
  Object.assign(row, built, { id: row.id, itemPick: row.itemPick });
}

type PriceAdjustmentGridVm = {
  $nextTick?: () => Promise<void>;
  insertAt?: (
    record: PriceAdjustmentLineRow,
    target: null,
  ) => Promise<{ row: PriceAdjustmentLineRow }>;
  scrollToRow?: (row: PriceAdjustmentLineRow) => void | Promise<void>;
  setEditCell?: (row: PriceAdjustmentLineRow, field: string) => void | Promise<void>;
};

/** 新增空行并聚焦项目名称选品（同构药库进货单） */
async function addItem() {
  if (!props.documentEditable) return;
  const itemTable = resolvedPickItemTable.value.trim();
  if (!itemTable) {
    ElMessage.warning('请先选择项目类型');
    return;
  }
  const g = gridApi?.grid as PriceAdjustmentGridVm | undefined;
  if (!g?.insertAt) return;
  const record: PriceAdjustmentLineRow = { id: newLineKey() };
  const { row } = await g.insertAt(record, null);
  await g.$nextTick?.();
  if (row) {
    await g.scrollToRow?.(row);
    await g.setEditCell?.(row, 'itemPick');
  }
  refreshDisplayLineCount();
}

/** 删除选中行或单行 */
async function deleteItem(target?: PriceAdjustmentLineRow | 'select') {
  const g = gridApi?.grid as null | {
    getCheckboxRecords?: () => PriceAdjustmentLineRow[];
    getFullData?: () => PriceAdjustmentLineRow[];
  };
  let removeKeys: Set<string>;
  if (target === 'select') {
    const selected = g?.getCheckboxRecords?.() ?? [];
    if (selected.length === 0) {
      ElMessage.warning('请先勾选要删除的明细行');
      return;
    }
    removeKeys = new Set(selected.map((r) => String(r.id ?? r.itemId ?? '')));
  } else if (target) {
    removeKeys = new Set([String(target.id ?? target.itemId ?? '')]);
  } else {
    return;
  }
  const rows = (g?.getFullData?.() ?? []).filter(
    (r) => !removeKeys.has(String(r.id ?? r.itemId ?? '')),
  );
  gridApi?.grid?.loadData(rows);
  refreshDisplayLineCount();
}

const columns: VxeGridPropTypes.Columns<PriceAdjustmentLineRow> = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center' },
  {
    type: 'expand',
    width: 44,
    fixed: 'left',
    align: 'center',
    slots: { content: 'lotExpand' },
  },
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  {
    field: 'itemPick',
    title: '项目名称',
    minWidth: 200,
    fixed: 'left',
    showOverflow: true,
    editRender: {
      name: 'PriceAdjustmentItemSelect',
      props: {
        query: async () => ({
          itemTable: resolvedPickItemTable.value,
          orgId: headForm.value.orgId || props.defaultOrgId,
        }),
        onSelected: (item: AdjustableItemRowDto, row: PriceAdjustmentLineRow) => {
          void syncRowFromItemPick(item, row);
        },
      },
    },
  },
  {
    field: 'specification',
    title: '规格',
    minWidth: 120,
    showOverflow: true,
  },
  {
    field: 'categoryCodeDictText',
    title: '项目种类',
    width: 96,
    showOverflow: true,
  },
  {
    field: 'currentPurchasePrice',
    title: '当前进价',
    width: 92,
    align: 'right',
    headerAlign: 'center',
    formatter: ({ cellValue, row }) =>
      isLogisticsRow(row as PriceAdjustmentLineRow) ? formatAmount(cellValue) : '—',
  },
  {
    field: 'currentSellingPrice',
    title: '当前售价',
    width: 92,
    align: 'right',
    headerAlign: 'center',
    formatter: ({ cellValue }) => formatAmount(cellValue),
  },
  {
    field: 'quantity',
    title: '库存数量',
    width: 120,
    align: 'center',
    headerAlign: 'center',
    formatter: ({ cellValue, row }) => {
      const line = row as PriceAdjustmentLineRow;
      if (!isLogisticsRow(line)) return '—';
      // 与其它库存单据一致：数量 + 单位拼一列，如「12 盒」
      const qty = formatQuantity(cellValue, '');
      if (!qty) return '—';
      const unit = String(line.unitName ?? '').trim();
      return unit ? `${qty} ${unit}` : qty;
    },
  },
  {
    field: 'newPurchaseAmount',
    title: '调后进价',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    headerClassName: 'price-adjustment-editable-header',
    className: resolveEditablePriceCellClass,
    formatter: ({ cellValue, row }) => {
      const line = row as PriceAdjustmentLineRow;
      if (!isLogisticsRow(line)) return '—';
      return formatEditablePrice(cellValue, props.documentEditable);
    },
    editRender: {
      name: 'ElInput',
      props: { type: 'number', min: 0, step: 0.01, clearable: true, placeholder: '请填写' },
    },
  },
  {
    field: 'newSellingAmount',
    title: '调后售价',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    headerClassName: 'price-adjustment-editable-header',
    className: resolveEditablePriceCellClass,
    formatter: ({ cellValue }) => formatEditablePrice(cellValue, props.documentEditable),
    editRender: {
      name: 'ElInput',
      props: { type: 'number', min: 0, step: 0.01, clearable: true, placeholder: '请填写' },
    },
  },
  {
    field: 'action',
    title: '操作',
    width: 80,
    fixed: 'right',
    align: 'center',
    slots: { default: 'action' },
  },
];

const gridOptions = reactive<VxeGridProps<PriceAdjustmentLineRow>>({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  showFooter: false,
  size: 'medium',
  columnConfig: { resizable: true },
  expandConfig: { accordion: false, trigger: 'default' },
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'cell',
    autoClear: true,
    beforeEditMethod: beforePriceAdjustmentCellEdit,
  },
  columns,
  pagerConfig: { enabled: false },
});

const [Grid, gridApi] = useVbenVxeGrid<PriceAdjustmentLineRow>({
  gridOptions,
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

function patchGridEditConfig() {
  gridOptions.editConfig = {
    ...gridOptions.editConfig,
    enabled: props.documentEditable,
    showIcon: props.documentEditable,
    trigger: 'click',
    mode: 'cell',
    autoClear: true,
    beforeEditMethod: beforePriceAdjustmentCellEdit,
  };
}

watch(
  () => props.documentEditable,
  () => {
    patchGridEditConfig();
    void nextTick(() => {
      const g = gridApi?.grid as undefined | { clearEdit?: () => void };
      if (!props.documentEditable) g?.clearEdit?.();
    });
  },
  { immediate: true },
);

watch(
  () => [props.adjustmentDetailRows, props.busNo, props.headMeta, props.resetKey] as const,
  () => {
    syncFromParent();
  },
  { immediate: true, deep: true },
);

watch(
  () => props.resetKey,
  () => {
    triggerPaneFlash();
  },
);

watch(
  () => props.itemTableTabOptions,
  (opts) => {
    if (pickItemTable.value.trim()) return;
    pickItemTable.value = String(opts[0]?.value ?? '');
  },
  { immediate: true },
);
</script>

<template>
  <div
    class="price-adjustment-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'price-adjustment-main-pane--flash': paneFlashActive }"
  >
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions
            require-finance-approval-role
            v-bind="docActionFlags"
            @approve="openApproveDialog('approve')"
            @print="handlePrint"
            @reject="openApproveDialog('reject')"
            @remove-doc="handleRemoveDoc"
            @save="handleSaveDoc"
            @save-and-submit="handleSaveAndSubmitDoc"
            @submit-approval="handleSubmitApproval"
            @withdraw="handleWithdraw"
          />
        </div>
      </template>

      <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2">
        <div class="shrink-0">
          <SectionTitle title="调价单信息" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText
            }}</span>
          </div>
          <ElForm :model="headForm" label-width="80px" class="compact-head-form mt-1">
            <div
              class="grid grid-cols-1 gap-x-2 gap-y-0 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5"
            >
              <ElFormItem label="单据号">
                <ElInput
                  :model-value="headForm.busNo || '（保存后生成）'"
                  disabled
                  placeholder="未选择"
                />
              </ElFormItem>
              <ElFormItem label="制单时间">
                <ElDatePicker
                  v-model="headForm.adjustmentTime"
                  class="w-full"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择时间"
                  :disabled="!documentEditable"
                />
              </ElFormItem>
              <ElFormItem label="调价原因">
                <ElSelect
                  v-model="headForm.adjustmentReason"
                  placeholder="请选择"
                  class="w-full"
                  filterable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="opt in adjustmentReasonOptions"
                    :key="String(opt.value)"
                    :label="String(opt.label ?? '')"
                    :value="String(opt.value ?? '')"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="项目类型">
                <ElSelect
                  v-model="pickItemTable"
                  placeholder="请选择"
                  class="w-full"
                  filterable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="tab in itemTableTabOptions"
                    :key="String(tab.value)"
                    :label="String(tab.label ?? '')"
                    :value="String(tab.value ?? '')"
                  />
                </ElSelect>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div class="price-adjustment-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col">
          <Grid>
            <template #toolbar-actions>
              <template v-if="documentEditable">
                <ElButton class="mr-2" type="primary" :icon="Plus" @click="handleAddItemClick">
                  添加行
                </ElButton>
                <ElButton type="danger" :icon="Delete" @click="deleteItem('select')">
                  删除行
                </ElButton>
              </template>
            </template>
            <template #lotExpand="{ row }">
              <PriceAdjustmentLineLotExpand :row="row" />
            </template>
            <template #action="{ row }">
              <ElButton v-if="documentEditable" type="danger" text @click="deleteItem(row)">
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>

        <div
          class="shrink-0 border-t border-[var(--el-border-color-extra-light)] py-2 text-sm text-[var(--el-text-color-regular)]"
        >
          进价盈亏合计
          <strong class="tabular-nums text-[var(--el-text-color-primary)]">{{
            formatMoneyDisplay(impactSummary.totalPurchaseImpact)
          }}</strong>
          元 · 售价盈亏合计
          <strong class="tabular-nums text-[var(--el-text-color-primary)]">{{
            formatMoneyDisplay(impactSummary.totalSellingImpact)
          }}</strong>
          元
        </div>
      </div>
    </InvMainShell>

    <PriceAdjustmentApproveDialog
      :visible="approveDialogVisible"
      :approve-mode="approveDialogMode"
      :bus-no="headForm.busNo || '（新调价单）'"
      :line-count="displayLineCount"
      :summary-text="approveSummaryText"
      @confirm="handleApproveDialogConfirm"
      @update:visible="approveDialogVisible = $event"
    />
  </div>
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.price-adjustment-main-pane__grid-wrap :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
}

.price-adjustment-main-pane__grid-wrap :deep(.vxe-table--body-wrapper),
.price-adjustment-main-pane__grid-wrap :deep(.vxe-table--header-wrapper) {
  width: 100% !important;
}

.price-adjustment-main-pane__grid-wrap :deep(.price-adjustment-editable-header) {
  background-color: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
}

.price-adjustment-main-pane__grid-wrap
  :deep(.price-adjustment-editable-header .vxe-cell--title::after) {
  margin-left: 2px;
  color: var(--el-color-danger);
  content: '*';
}

.price-adjustment-main-pane__grid-wrap :deep(.price-adjustment-editable-cell) {
  background-color: var(--el-color-warning-light-9);
  cursor: pointer;
}

.price-adjustment-main-pane__grid-wrap
  :deep(.price-adjustment-editable-cell--empty .vxe-cell--label) {
  color: var(--el-color-warning);
  font-style: normal;
}

.price-adjustment-main-pane__grid-wrap :deep(.price-adjustment-readonly-cell) {
  background-color: var(--el-fill-color-lighter);
  color: var(--el-text-color-placeholder);
}

@keyframes price-adjustment-pane-flash {
  0% {
    opacity: 1;
  }

  35% {
    opacity: 0.72;
  }

  100% {
    opacity: 1;
  }
}

.price-adjustment-main-pane--flash {
  animation: price-adjustment-pane-flash 0.42s ease-out;
}
</style>
