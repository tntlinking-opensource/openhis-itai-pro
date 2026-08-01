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
  httpListRefundableChargeLinesForEncounter,
  httpSubmitOutpatientRefundApplication,
  httpVerifyConsumableExecutionBeforeRefund,
} from '#/api/doctor-station/refund-application.http';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'DoctorStationRefundApplicationDialog' });

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
/** 表格多选：按支付单整组拟退，不支持组内半退 */
const selectedRows = ref<RefundTableRow[]>([]);
/** 程序同步勾选时跳过 selection-change，避免递归 */
const syncingSelection = ref(false);

const listRequest = useRequest(httpListRefundableChargeLinesForEncounter, { manual: true });
const verifyRequest = useRequest(httpVerifyConsumableExecutionBeforeRefund, {
  manual: true,
  showErrorMessage: false,
});
const submitRequest = useRequest(httpSubmitOutpatientRefundApplication, { manual: true });

const listLoading = computed(() => listRequest.loading.value);
const submitLoading = computed(() => submitRequest.loading.value || verifyRequest.loading.value);

/** 拟退行（已选支付单下的全部账单行） */
const refundingRows = computed(() => selectedRows.value);

/** 已选支付单数 */
const selectedPaymentGroupCount = computed(() => {
  const ids = new Set(refundingRows.value.map((row) => row.paymentId));
  return ids.size;
});

/** 拟退行金额合计（6 位加总后 2 位展示） */
const refundingAmountSum = computed(() =>
  sumMoneyThenSettle(refundingRows.value.map((row) => row.lineTotalAmount ?? 0)),
);

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

/** 按组首行勾选结果展开为整单拟退行 */
function expandSelectionToWholePaymentGroups(selection: RefundTableRow[]) {
  const selectedPaymentIds = new Set<string>();
  for (const row of selection) {
    selectedPaymentIds.add(row.paymentId);
  }
  return tableRows.value.filter((row) => selectedPaymentIds.has(row.paymentId));
}

/** 将勾选状态同步到 ElTable（整单拟退行一并勾选） */
async function applyTableSelection(rows: RefundTableRow[]) {
  await nextTick();
  const table = tableRef.value;
  if (!table) return;

  syncingSelection.value = true;
  table.clearSelection();
  const selectedIds = new Set(rows.map((row) => row.chargeItemId));
  for (const row of tableRows.value) {
    table.toggleRowSelection(row, selectedIds.has(row.chargeItemId));
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

/** 表格多选变更：按原支付整单展开，不支持组内半退 */
function handleSelectionChange(selection: RefundTableRow[]) {
  if (syncingSelection.value) {
    return;
  }
  const expanded = expandSelectionToWholePaymentGroups(selection);
  selectedRows.value = expanded;

  const expandedIds = new Set(expanded.map((row) => row.chargeItemId));
  const rawIds = new Set(selection.map((row) => row.chargeItemId));
  const needsSync =
    expandedIds.size !== rawIds.size || [...expandedIds].some((id) => !rawIds.has(id));
  if (needsSync) {
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

/** 拟退行收费项 id 集合 */
function collectRefundingChargeItemIds(): string[] {
  const ids: string[] = [];
  for (const row of refundingRows.value) {
    const id = preserveApiIdForRequest(row.chargeItemId);
    if (id) ids.push(id);
  }
  return ids;
}

/** 校验已选支付单均为整组勾选（防半退） */
function validateWholePaymentGroupSelection(): boolean {
  const selectedPaymentIds = new Set(refundingRows.value.map((row) => row.paymentId));
  for (const paymentId of selectedPaymentIds) {
    const groupRows = tableRows.value.filter((row) => row.paymentId === paymentId);
    const selectedInGroup = refundingRows.value.filter((row) => row.paymentId === paymentId);
    if (selectedInGroup.length > 0 && selectedInGroup.length < groupRows.length) {
      ElMessage.warning('同一笔支付单须整单退费，不支持单独勾选某一项');
      return false;
    }
  }
  return true;
}

/** 提交前耗材执行门禁 */
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
async function handleSubmit() {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (tableRows.value.length === 0) {
    ElMessage.warning('当前就诊暂无可退账单行');
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
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    width="960px"
    title="申请退费"
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
        empty-text="暂无可申请退费的账单行"
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
        }}；同一支付单须整单退费
      </div>
    </div>

    <template #footer>
      <ElButton @click="closeDialog">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitLoading"
        :disabled="tableRows.length === 0"
        @click="handleSubmit"
      >
        提交申请
      </ElButton>
    </template>
  </ElDialog>
</template>
