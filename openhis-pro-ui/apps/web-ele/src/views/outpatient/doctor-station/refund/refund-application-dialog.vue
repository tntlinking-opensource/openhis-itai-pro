<script setup lang="ts">
import type { RefundableChargeLineItemDto } from '#/api/doctor-station/refund-application.types';

import { computed, nextTick, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  httpCancelOutpatientRefundApplication,
  httpListRefundableChargeLinesForEncounter,
  httpSubmitOutpatientRefundApplication,
  httpVerifyConsumableExecutionBeforeRefund,
} from '#/api/doctor-station/refund-application.http';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'DoctorStationRefundApplicationDialog' });

/** 收费明细状态 code（与后端 ChargeItemStatus 一致，禁止用文案分支） */
const CHARGE_STATUS_SETTLED = 'SETTLED';
const CHARGE_STATUS_REFUNDING = 'REFUNDING';

/** 选中支付单操作模式：申请 / 撤销 / 混选非法 */
type SelectionActionMode = 'apply' | 'cancel' | 'mixed' | 'empty';

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    modelValue: boolean;
    patientName?: string;
  }>(),
  {
    encounterId: '',
    patientName: '',
  },
);

const emit = defineEmits<{
  submitted: [];
  'update:modelValue': [value: boolean];
}>();

/** 表格行：按原支付分组，首行承载合并勾选 */
type RefundTableRow = RefundableChargeLineItemDto & {
  isFirstLineInPaymentGroup: boolean;
  paymentGroupLineCount: number;
};

/** 勾选列 + 支付单号列按 paymentId 纵向合并 */
const PAYMENT_GROUP_MERGE_COL_COUNT = 2;

const tableRef = ref<InstanceType<typeof ElTable> | null>(null);
const tableRows = ref<RefundTableRow[]>([]);
/** 表格多选：按支付单整组拟退/拟撤，不支持组内半退/半取消 */
const selectedRows = ref<RefundTableRow[]>([]);
/** 程序同步勾选时跳过 selection-change，避免递归 */
const syncingSelection = ref(false);

const listRequest = useRequest(httpListRefundableChargeLinesForEncounter, { manual: true });
const verifyRequest = useRequest(httpVerifyConsumableExecutionBeforeRefund, {
  manual: true,
  showErrorMessage: false,
});
const submitRequest = useRequest(httpSubmitOutpatientRefundApplication, { manual: true });
const cancelRequest = useRequest(httpCancelOutpatientRefundApplication, { manual: true });

const listLoading = computed(() => listRequest.loading.value);
const submitLoading = computed(
  () => submitRequest.loading.value || verifyRequest.loading.value || cancelRequest.loading.value,
);

/** 拟退/拟撤行（已选支付单下的全部账单行） */
const refundingRows = computed(() => selectedRows.value);

/** 已选支付单数 */
const selectedPaymentGroupCount = computed(() => {
  const ids = new Set(refundingRows.value.map((row) => row.paymentId));
  return ids.size;
});

/** 拟退/拟撤行金额合计（6 位加总后 2 位展示） */
const refundingAmountSum = computed(() =>
  sumMoneyThenSettle(refundingRows.value.map((row) => row.lineTotalAmount ?? 0)),
);

/** 规范化收费状态 code */
function normalizeChargeStatus(status: null | string | undefined): string {
  return String(status ?? '')
    .trim()
    .toUpperCase();
}

/**
 * 根据已选行判定操作模式：全已结算→申请；全退款中→撤销；混选→非法。
 */
function resolveSelectionActionMode(rows: RefundTableRow[]): SelectionActionMode {
  if (rows.length === 0) {
    return 'empty';
  }
  let hasSettled = false;
  let hasRefunding = false;
  for (const row of rows) {
    const status = normalizeChargeStatus(row.chargeItemStatusEnum);
    if (status === CHARGE_STATUS_SETTLED) {
      hasSettled = true;
    } else if (status === CHARGE_STATUS_REFUNDING) {
      hasRefunding = true;
    } else {
      // 其它状态不允许在本弹窗操作
      return 'mixed';
    }
  }
  if (hasSettled && hasRefunding) {
    return 'mixed';
  }
  if (hasRefunding) {
    return 'cancel';
  }
  return 'apply';
}

const selectionActionMode = computed(() => resolveSelectionActionMode(refundingRows.value));

/** 主按钮文案随模式变化 */
const primaryActionLabel = computed(() => {
  if (selectionActionMode.value === 'cancel') {
    return '撤销退费';
  }
  return '提交申请';
});

/** 金额格式化为两位小数（纯展示） */
function formatMoney(value: null | number | string | undefined): string {
  return formatMoneyDisplay(value);
}

/** 数量 + 单位展示（中药克/西药盒等；无数量时占位） */
function formatQuantityDisplay(
  row: Pick<RefundableChargeLineItemDto, 'quantity' | 'quantityUnit'>,
): string {
  const qty = row.quantity;
  if (qty === null || qty === undefined || String(qty).trim() === '') {
    return '—';
  }
  const unit = String(row.quantityUnit ?? '').trim();
  return unit ? `${qty}${unit}` : String(qty);
}

/** 关闭弹窗 */
function closeDialog() {
  emit('update:modelValue', false);
}

function onDialogModelValue(value: unknown) {
  emit('update:modelValue', Boolean(value));
}

/** 将接口行转为表格行：按 paymentGroupSortKey + paymentId 排序并打分组标记 */
function mapLinesToTableRows(lines: RefundableChargeLineItemDto[]): RefundTableRow[] {
  const normalized = lines.map((line) => ({
    ...line,
    paymentId: preserveApiIdForRequest(line.paymentId) ?? '',
    chargeItemId: preserveApiIdForRequest(line.chargeItemId) ?? '',
    encounterId: preserveApiIdForRequest(line.encounterId) ?? '',
    serviceId: preserveApiIdForRequest(line.serviceId) ?? '',
    paymentGroupSortKey: preserveApiIdForRequest(line.paymentGroupSortKey) ?? '',
  }));

  normalized.sort((a, b) => {
    const sortCompare = (a.paymentGroupSortKey ?? '').localeCompare(b.paymentGroupSortKey ?? '');
    if (sortCompare !== 0) return sortCompare;
    return (a.paymentId ?? '').localeCompare(b.paymentId ?? '');
  });

  const groupLineCounts = new Map<string, number>();
  for (const line of normalized) {
    const paymentId = line.paymentId;
    groupLineCounts.set(paymentId, (groupLineCounts.get(paymentId) ?? 0) + 1);
  }

  const seenPaymentIds = new Set<string>();
  return normalized.map((line) => {
    const isFirstLineInPaymentGroup = !seenPaymentIds.has(line.paymentId);
    if (isFirstLineInPaymentGroup) {
      seenPaymentIds.add(line.paymentId);
    }
    return {
      ...line,
      isFirstLineInPaymentGroup,
      paymentGroupLineCount: groupLineCounts.get(line.paymentId) ?? 1,
    };
  });
}

/** 同一原支付须整单退：仅每组首行可勾选 */
function isPaymentGroupHeadSelectable(row: RefundTableRow) {
  return row.isFirstLineInPaymentGroup;
}

/**
 * 按支付单首行勾选展开为整单拟退行。
 * 合并单元格后，取消某张支付单时其明细行可能仍留在表格选中集合里，不能据此把该单勾回去。
 */
function expandSelectionToWholePaymentGroups(selection: RefundTableRow[]) {
  const selectedPaymentIds = new Set<string>();
  // 只认每张支付单的首行勾选，两张单可以只退其中一张
  for (const row of selection) {
    if (!row.isFirstLineInPaymentGroup) {
      continue;
    }
    selectedPaymentIds.add(row.paymentId);
  }
  return tableRows.value.filter((row) => selectedPaymentIds.has(row.paymentId));
}

/** 将勾选状态同步到 ElTable：只勾支付单首行，避免明细行把已取消的单勾回 */
async function applyTableSelection(rows: RefundTableRow[]) {
  await nextTick();
  const table = tableRef.value;
  if (!table) return;

  syncingSelection.value = true;
  table.clearSelection();
  const selectedPaymentIds = new Set(rows.map((row) => row.paymentId));
  for (const row of tableRows.value) {
    if (!row.isFirstLineInPaymentGroup) {
      continue;
    }
    table.toggleRowSelection(row, selectedPaymentIds.has(row.paymentId));
  }
  syncingSelection.value = false;
}

/** 加载后默认勾选全部支付单整组 */
async function syncSelectAllPaymentGroups() {
  selectedRows.value = [...tableRows.value];
  await applyTableSelection(selectedRows.value);
}

/** 打开弹窗时拉取可退账单行 */
async function loadRefundableLines(selectAll = true) {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!encounterId) {
    tableRows.value = [];
    selectedRows.value = [];
    return;
  }
  const res = await listRequest.run({ encounterId });
  tableRows.value = mapLinesToTableRows(res.lines ?? []);
  if (selectAll) {
    await syncSelectAllPaymentGroups();
  } else {
    selectedRows.value = [];
    await applyTableSelection([]);
  }
}

/** 弹窗打开：重置并加载列表 */
function onDialogOpen() {
  tableRows.value = [];
  selectedRows.value = [];
  void loadRefundableLines(true);
}

/** 刷新可退列表 */
async function handleRefresh() {
  const hadSelection = selectedRows.value.length > 0;
  await loadRefundableLines(hadSelection);
}

/** 表格多选变更：按原支付整单展开，支付单之间可只选一张 */
function handleSelectionChange(selection: RefundTableRow[]) {
  if (syncingSelection.value) {
    return;
  }
  const expanded = expandSelectionToWholePaymentGroups(selection);
  selectedRows.value = expanded;

  // 明细行残留在选中集合时清掉，避免取消一张支付单后又被勾回
  const hasNonHeadSelected = selection.some((row) => !row.isFirstLineInPaymentGroup);
  if (hasNonHeadSelected) {
    void applyTableSelection(expanded);
  }
}

/** 全选 / 清空勾选（按支付单整组） */
function setAllSelected(selectAll: boolean) {
  if (selectAll) {
    void syncSelectAllPaymentGroups();
    return;
  }
  selectedRows.value = [];
  void applyTableSelection([]);
}

/** ElTable spanMethod：勾选列与支付单号列按 paymentId 分组纵向合并 */
function handleRefundTableSpanMethod({
  row,
  columnIndex,
}: {
  columnIndex: number;
  row: RefundTableRow;
}): [number, number] | undefined {
  if (columnIndex >= PAYMENT_GROUP_MERGE_COL_COUNT) {
    return undefined;
  }
  if (row.isFirstLineInPaymentGroup) {
    return [row.paymentGroupLineCount, 1];
  }
  return [0, 0];
}

/** 构建提交体：仅提交已勾选支付单下的行，且全部为本单拟退（产品不支持半退保留） */
function buildSubmitLines() {
  const selectedPaymentIds = new Set(refundingRows.value.map((row) => row.paymentId));
  return tableRows.value
    .filter((row) => selectedPaymentIds.has(row.paymentId))
    .map((row) => ({
      paymentId: row.paymentId,
      chargeItemId: row.chargeItemId,
      refundForCurrentBill: true,
    }));
}

/** 构建撤销体：覆盖已勾选支付单下全部退款中行 */
function buildCancelLines() {
  const selectedPaymentIds = new Set(refundingRows.value.map((row) => row.paymentId));
  return tableRows.value
    .filter((row) => selectedPaymentIds.has(row.paymentId))
    .map((row) => ({
      paymentId: row.paymentId,
      chargeItemId: row.chargeItemId,
    }));
}

/** 拟退/拟撤行收费项 id 集合 */
function collectRefundingChargeItemIds(): string[] {
  const ids: string[] = [];
  for (const row of refundingRows.value) {
    const id = preserveApiIdForRequest(row.chargeItemId);
    if (id) ids.push(id);
  }
  return ids;
}

/** 校验已选支付单均为整组勾选（防半退/半取消） */
function validateWholePaymentGroupSelection(): boolean {
  const selectedPaymentIds = new Set(refundingRows.value.map((row) => row.paymentId));
  for (const paymentId of selectedPaymentIds) {
    const groupRows = tableRows.value.filter((row) => row.paymentId === paymentId);
    const selectedInGroup = refundingRows.value.filter((row) => row.paymentId === paymentId);
    if (selectedInGroup.length > 0 && selectedInGroup.length < groupRows.length) {
      ElMessage.warning('同一笔支付单须整单操作，不支持单独勾选某一项');
      return false;
    }
  }
  return true;
}

/** 提交前耗材执行门禁（仅申请路径） */
async function verifyConsumableGate(): Promise<boolean> {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  const chargeItemIds = collectRefundingChargeItemIds();
  if (!encounterId || chargeItemIds.length === 0) {
    return true;
  }
  try {
    const res = await verifyRequest.run({ encounterId, chargeItemIds });
    if (res.allowed) return true;
    ElMessage.warning('存在已执行的耗材，请先取消执行后再申请退费');
    return false;
  } catch {
    ElMessage.warning('耗材执行校验未通过，请稍后重试');
    return false;
  }
}

/** 确认并提交退费申请 */
async function handleSubmitApply() {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (tableRows.value.length === 0) {
    ElMessage.warning('当前就诊暂无可操作账单行');
    return;
  }
  if (refundingRows.value.length === 0) {
    ElMessage.warning('请至少勾选一笔支付单');
    return;
  }
  if (!validateWholePaymentGroupSelection()) {
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认提交退费申请？拟退 ${selectedPaymentGroupCount.value} 笔支付单、${refundingRows.value.length} 项，合计 ¥${formatMoney(refundingAmountSum.value)}`,
      '提交退费申请',
      { type: 'warning', confirmButtonText: '提交', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  const gateOk = await verifyConsumableGate();
  if (!gateOk) return;

  const res = await submitRequest.run({
    encounterId,
    lines: buildSubmitLines(),
  });
  if (res.success) {
    const n = res.refundingChargeItemCount ?? refundingRows.value.length;
    ElMessage.success(`退费申请已提交（${n} 项进入退费中）`);
    emit('submitted');
    closeDialog();
  }
}

/** 确认并撤销退费 */
async function handleSubmitCancel() {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (refundingRows.value.length === 0) {
    ElMessage.warning('请至少勾选一笔退款中的支付单');
    return;
  }
  if (!validateWholePaymentGroupSelection()) {
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认撤销退费？将还原 ${selectedPaymentGroupCount.value} 笔支付单、${refundingRows.value.length} 项至申请前状态，合计 ¥${formatMoney(refundingAmountSum.value)}`,
      '撤销退费',
      { type: 'warning', confirmButtonText: '撤销退费', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  const res = await cancelRequest.run({
    encounterId,
    lines: buildCancelLines(),
  });
  if (res.success) {
    emit('submitted');
    closeDialog();
  }
}

/** 主按钮：按选中模式分发申请或撤销 */
async function handlePrimaryAction() {
  const mode = selectionActionMode.value;
  if (mode === 'empty') {
    ElMessage.warning('请至少勾选一笔支付单');
    return;
  }
  if (mode === 'mixed') {
    ElMessage.warning('请勿同时勾选「可申请」与「可撤销」支付单，请分开操作');
    return;
  }
  if (mode === 'cancel') {
    await handleSubmitCancel();
    return;
  }
  await handleSubmitApply();
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    width="960px"
    title="退费"
    append-to-body
    destroy-on-close
    @update:model-value="onDialogModelValue"
    @open="onDialogOpen"
  >
    <div class="refund-dialog flex flex-col gap-3">
      <div class="flex flex-wrap items-center gap-2 text-sm text-[var(--el-text-color-regular)]">
        <span v-if="patientName">患者：{{ patientName }}</span>
        <span v-else class="text-[var(--el-text-color-placeholder)]">未选择患者</span>
        <span class="flex-1"></span>
        <ElButton link type="primary" :loading="listLoading" @click="handleRefresh">刷新</ElButton>
        <ElButton link type="primary" @click="setAllSelected(true)">全选</ElButton>
        <ElButton link type="primary" @click="setAllSelected(false)">清空</ElButton>
      </div>

      <ElTable
        ref="tableRef"
        v-loading="listLoading"
        :data="tableRows"
        border
        stripe
        height="400"
        row-key="chargeItemId"
        empty-text="暂无可申请或可撤销的账单行"
        :span-method="handleRefundTableSpanMethod"
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="48"
          align="center"
          fixed="left"
          :selectable="isPaymentGroupHeadSelectable"
        />
        <ElTableColumn prop="paymentBusNo" label="支付单号" min-width="140" show-overflow-tooltip />
        <ElTableColumn
          prop="itemDisplayName"
          label="收费项目"
          min-width="200"
          show-overflow-tooltip
        />
        <ElTableColumn label="数量" width="100" align="right">
          <template #default="{ row }">{{ formatQuantityDisplay(row) }}</template>
        </ElTableColumn>
        <ElTableColumn label="金额" width="100" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.lineTotalAmount) }}</template>
        </ElTableColumn>
        <ElTableColumn label="收费状态" width="100" align="center">
          <template #default="{ row }">
            <ElTag size="small" effect="plain">{{ row.chargeItemStatusEnumText || '—' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="refundStatusText"
          label="退费状态"
          width="100"
          align="center"
          show-overflow-tooltip
        />
      </ElTable>

      <div class="text-sm text-[var(--el-text-color-regular)]">
        已选 {{ selectedPaymentGroupCount }} 笔支付单、{{ refundingRows.length }} 项，合计 ¥{{
          formatMoney(refundingAmountSum)
        }}；同一支付单须整单操作；请勿混选「已结算」与「退款中」
      </div>
    </div>

    <template #footer>
      <ElButton @click="closeDialog">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitLoading"
        :disabled="tableRows.length === 0"
        @click="handlePrimaryAction"
      >
        {{ primaryActionLabel }}
      </ElButton>
    </template>
  </ElDialog>
</template>
