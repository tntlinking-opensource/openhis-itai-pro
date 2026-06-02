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
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';
import { decimalAdd } from '#/utils/decimal-util';

defineOptions({ name: 'DoctorStationRefundApplicationDialog' });

type RefundTableRow = RefundableChargeLineItemDto;

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

const tableRef = ref<InstanceType<typeof ElTable> | null>(null);
const tableRows = ref<RefundTableRow[]>([]);
/** 表格多选：选中行即本单拟退 */
const selectedRows = ref<RefundTableRow[]>([]);

const listRequest = useRequest(httpListRefundableChargeLinesForEncounter, { manual: true });
const verifyRequest = useRequest(httpVerifyConsumableExecutionBeforeRefund, {
  manual: true,
  showErrorMessage: false,
});
const submitRequest = useRequest(httpSubmitOutpatientRefundApplication, { manual: true });

const listLoading = computed(() => listRequest.loading.value);
const submitLoading = computed(() => submitRequest.loading.value || verifyRequest.loading.value);

/** 拟退行（表格勾选） */
const refundingRows = computed(() => selectedRows.value);

/** 拟退行金额合计（展示用） */
const refundingAmountSum = computed(() => {
  let sum = 0;
  for (const row of refundingRows.value) {
    sum = decimalAdd(sum, row.lineTotalAmount ?? 0, 4);
  }
  return sum;
});

/** 金额格式化为两位小数 */
function formatMoney(value: null | number | string | undefined): string {
  const n = Number(value ?? 0);
  if (Number.isNaN(n)) return '0.00';
  return n.toFixed(2);
}

/** 关闭弹窗 */
function closeDialog() {
  emit('update:modelValue', false);
}

function onDialogModelValue(value: unknown) {
  emit('update:modelValue', Boolean(value));
}

/** 将接口行转为表格行并规范化 id */
function mapLinesToTableRows(lines: RefundableChargeLineItemDto[]): RefundTableRow[] {
  return lines.map((line) => ({
    ...line,
    paymentId: preserveApiIdForRequest(line.paymentId) ?? '',
    chargeItemId: preserveApiIdForRequest(line.chargeItemId) ?? '',
    encounterId: preserveApiIdForRequest(line.encounterId) ?? '',
    serviceId: preserveApiIdForRequest(line.serviceId) ?? '',
    paymentGroupSortKey: preserveApiIdForRequest(line.paymentGroupSortKey) ?? '',
  }));
}

/** 同步表格勾选（加载后默认全选） */
async function syncTableSelection(selectAll: boolean) {
  await nextTick();
  const table = tableRef.value;
  if (!table) return;
  table.clearSelection();
  if (!selectAll) {
    selectedRows.value = [];
    return;
  }
  for (const row of tableRows.value) {
    table.toggleRowSelection(row, true);
  }
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
  await syncTableSelection(selectAll);
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

/** 表格多选变更 */
function handleSelectionChange(rows: RefundTableRow[]) {
  selectedRows.value = rows;
}

/** 全选 / 清空勾选 */
function setAllSelected(selectAll: boolean) {
  void syncTableSelection(selectAll);
}

/** 构建提交体：须包含列表全部行，勾选行为本单拟退 */
function buildSubmitLines() {
  const selectedIds = new Set(
    selectedRows.value.map((row) => preserveApiIdForRequest(row.chargeItemId)).filter(Boolean),
  );
  return tableRows.value.map((row) => ({
    paymentId: row.paymentId,
    chargeItemId: row.chargeItemId,
    refundForCurrentBill: selectedIds.has(row.chargeItemId),
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
    ElMessage.warning('请至少勾选一项拟退费用');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认提交退费申请？拟退 ${refundingRows.value.length} 项，合计 ¥${formatMoney(refundingAmountSum.value)}`,
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
        <span class="flex-1" />
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
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn type="selection" width="48" align="center" fixed="left" />
        <ElTableColumn prop="paymentBusNo" label="支付单号" min-width="140" show-overflow-tooltip />
        <ElTableColumn
          prop="itemDisplayName"
          label="收费项目"
          min-width="200"
          show-overflow-tooltip
        />
        <ElTableColumn label="数量" width="88" align="right">
          <template #default="{ row }">{{ row.quantity ?? '—' }}</template>
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
        已选 {{ refundingRows.length }} 项，合计 ¥{{
          formatMoney(refundingAmountSum)
        }}；未选行将保留并重开账单
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
