<script setup lang="ts">
/**
 * 体检收费工作台（AS-PE-FIN-001）。
 * 布局对齐门诊收费：左患者队列卡片 + 中主区（患者信息条 / 收费明细），关闭空右栏。
 */
import type {
  LoadPhysicalExamChargeItemsResponse,
  PhysicalExamChargeItemRowDto,
  PhysicalExamChargeQueueRowDto,
  PreSettlePhysicalExamChargesResponse,
  PreviewPhysicalExamRefundResponse,
} from '#/api/peis/physical-exam-charge';
import type { OptionDto } from '#/api/outpatient-registration';
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { RotateCw } from '@vben/icons';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElPagination,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  httpAdjustPhysicalExamChargePrices,
  httpConfirmPhysicalExamChargeSettlement,
  httpLoadPhysicalExamChargeItems,
  httpLoadPhysicalExamChargePageInit,
  httpPagePhysicalExamCompleteQueue,
  httpPagePhysicalExamWaitingQueue,
  httpPreSettlePhysicalExamCharges,
  httpPreviewPhysicalExamRefund,
  httpRefundPhysicalExamCharges,
} from '#/api/peis/physical-exam-charge';
import BillingPatientCard from '#/components/charge/billing-patient-card.vue';
import PeisChargePriceAdjustDialog from '#/components/peis/peis-charge-price-adjust-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { getLocalDayRangeDateTimeStrings } from '#/utils/format/date-range-quick';
import { formatMoneyDisplay, scaleMoneySettle } from '#/utils/format/money-scale';
import {
  extractRequestErrorMessage,
  isStaleCrossRoleError,
} from '#/utils/http/stale-cross-role-error';
import { optionDtosToPayChannelOptions } from '#/utils/payment/payment-channel-options';
import {
  clearCachedClientRefundResult,
  submitScanRefund,
} from '#/utils/payment/submit-scan-refund';

import '../../outpatient/charge/charge-workbench-panel.css';

defineOptions({
  name: 'PeisPhysicalExamCharge',
});

const QUEUE_TAB = {
  WAITING: 'WAITING',
  COMPLETE: 'COMPLETE',
} as const;

/** 个检套餐整单 ChargeItem 锚点表名 */
const SERVICE_TABLE_ACTIVITY_DEFINITION = 'wkf_activity_definition';

const SETTLE_OUTCOME_COMPLETED = '1';

const route = useRoute();
const queueTabCode = ref<string>(QUEUE_TAB.WAITING);
const keyword = ref('');
const dateRange = ref<[string, string] | null>(null);
/** 首屏初始化完成前不响应筛选 watch，避免日期赋值触发二次查询把布局冲掉 */
const filtersReady = ref(false);
const queuePager = reactive({ pageNo: 1, pageSize: 20 });
const queueRecords = ref<PhysicalExamChargeQueueRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedEncounterId = ref('');
const chargeDetail = ref<LoadPhysicalExamChargeItemsResponse | null>(null);
const detailLoading = ref(false);
const selectedChargeRows = ref<PhysicalExamChargeItemRowDto[]>([]);
const chargeTableRef = ref<{
  clearSelection: () => void;
  toggleRowSelection: (row: PhysicalExamChargeItemRowDto, selected?: boolean) => void;
} | null>(null);

const paymentChannelOptions = ref<{ label: string; value: string }[]>([]);
const settlementVisible = ref(false);
const settlementLoading = ref(false);
const preSettleSnapshot = ref<PreSettlePhysicalExamChargesResponse | null>(null);
const lockedChargeItemIds = ref<string[]>([]);
const payChannelCode = ref('WECHAT');

const priceAdjustVisible = ref(false);
const priceAdjustRow = ref<PhysicalExamChargeItemRowDto | null>(null);
const packageAdjustVisible = ref(false);
const packageAdjustAmount = ref(0);
const packageAdjustLoading = ref(false);

const refundVisible = ref(false);
const refundLoading = ref(false);
const refundPreview = ref<PreviewPhysicalExamRefundResponse | null>(null);

const currentQueueRow = computed(() =>
  queueRecords.value.find((row) => String(row.encounterId) === selectedEncounterId.value),
);

const plannedChargeItems = computed(() => {
  const items = chargeDetail.value?.items ?? [];
  return items.filter((row) => row.status === 'PLANNED');
});

const selectedPlannedRows = computed(() =>
  selectedChargeRows.value.filter((row) => row.status === 'PLANNED'),
);

const selectedChargeSum = computed(() => {
  let sum = 0;
  for (const row of selectedPlannedRows.value) {
    const n = Number(row.totalPrice ?? 0);
    if (!Number.isNaN(n)) {
      sum += n;
    }
  }
  return sum;
});

const canSettle = computed(
  () => selectedPlannedRows.value.length > 0 && selectedEncounterId.value !== '',
);

/** 已结算或退款中的行，供收银员退费/重试。 */
const refundableChargeItems = computed(() => {
  const items = chargeDetail.value?.items ?? [];
  return items.filter((row) => row.status === 'SETTLED' || row.status === 'REFUNDING');
});

/** 当前体检单存在可退明细时允许退费。 */
const canRefund = computed(
  () => selectedEncounterId.value !== '' && refundableChargeItems.value.length > 0,
);

/** 个检套餐整单（锚 AD）且待收费，供套餐改价。 */
const packageAdjustableItems = computed(() =>
  (chargeDetail.value?.items ?? []).filter(
    (row) => row.priceEditable && row.serviceTable === SERVICE_TABLE_ACTIVITY_DEFINITION,
  ),
);

/** 个检、未结清、已挂套餐整单时允许套餐改价。 */
const canAdjustPackagePrice = computed(
  () =>
    selectedEncounterId.value !== '' &&
    !currentQueueRow.value?.groupFlag &&
    packageAdjustableItems.value.length > 0,
);

function formatMoney(value: null | number | string | undefined) {
  return formatMoneyDisplay(value);
}

/** 收费明细状态文案。 */
function chargeStatusLabel(status: null | string | undefined) {
  if (status === 'SETTLED') {
    return '已结算';
  }
  if (status === 'REFUNDING') {
    return '退款中';
  }
  if (status === 'REFUNDED') {
    return '已退款';
  }
  return '待收费';
}

/** 收费明细状态标签色。 */
function chargeStatusTagType(status: null | string | undefined) {
  if (status === 'SETTLED') {
    return 'success' as const;
  }
  if (status === 'REFUNDING') {
    return 'danger' as const;
  }
  if (status === 'REFUNDED') {
    return 'info' as const;
  }
  return 'warning' as const;
}

/** 该行是否可点退费（已结算或退款中待重试）。 */
function canRefundRow(row: PhysicalExamChargeItemRowDto) {
  return row.status === 'SETTLED' || row.status === 'REFUNDING';
}

function formatStartTime(value: null | string | undefined) {
  if (!value) {
    return '—';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value;
}

function resolveQueueApi() {
  if (queueTabCode.value === QUEUE_TAB.COMPLETE) {
    return httpPagePhysicalExamCompleteQueue;
  }
  return httpPagePhysicalExamWaitingQueue;
}

function formatGender(row: PhysicalExamChargeQueueRowDto | null | undefined) {
  if (!row) {
    return '—';
  }
  const text = (row.genderEnumText ?? '').trim();
  if (text) {
    return text;
  }
  const code = (row.genderCode ?? '').trim();
  return code || '—';
}

function mapToBillingPatientCard(row: PhysicalExamChargeQueueRowDto): BillingPatientCardPatient {
  const settled = row.queueLabelCode === QUEUE_TAB.COMPLETE;
  const amountText = settled
    ? `已收 ¥${formatMoney(row.settledAmount)}`
    : `待收 ¥${formatMoney(row.plannedAmount)}`;
  return {
    id: String(row.encounterId),
    patientName: row.patientName || '—',
    gender: formatGender(row),
    ageText: (row.ageText ?? '').trim() || undefined,
    kindBadge: row.groupFlag ? '团' : '个',
    chargeStatusCode: settled ? 'SETTLED' : 'PLANNED',
    chargeStatus: settled ? '已结算' : '待收费',
    visitDepartment: row.packageName || '体检套餐',
    visitTime: formatStartTime(row.startTime),
    visitType: 'outpatient',
    insuranceType: amountText,
  };
}

async function loadQueue() {
  queueLoading.value = true;
  try {
    const range = dateRange.value;
    const body: {
      endDate?: string;
      keyword: string;
      pageNo: number;
      pageSize: number;
      startDate?: string;
    } = {
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      keyword: keyword.value.trim(),
    };
    // 与门诊收费一致：日历日扩成本地 00:00:00～23:59:59，避免起止同为 00:00 滤空
    if (range?.[0] && range?.[1]) {
      body.startDate = getLocalDayRangeDateTimeStrings(range[0]).startDate;
      body.endDate = getLocalDayRangeDateTimeStrings(range[1]).endDate;
    }
    const data = await resolveQueueApi()(body);
    const { content, totalCount } = parsePagedRowsFromApiData<PhysicalExamChargeQueueRowDto>(data);
    queueRecords.value = content ?? [];
    queueTotal.value = totalCount ?? 0;
  } catch (error) {
    ElMessage.error('查询体检收费队列失败');
    console.error(error);
  } finally {
    queueLoading.value = false;
  }
}

const debouncedLoadQueue = useDebounceFn(loadQueue, 300);

async function selectAllPlannedRows() {
  await nextTick();
  const table = chargeTableRef.value;
  if (!table) {
    return;
  }
  try {
    table.clearSelection();
    for (const row of plannedChargeItems.value) {
      table.toggleRowSelection(row, true);
    }
  } catch (error) {
    console.error(error);
  }
}

async function loadDetail(encounterId: string, patientId?: string) {
  if (!encounterId) {
    return;
  }
  selectedEncounterId.value = encounterId;
  detailLoading.value = true;
  selectedChargeRows.value = [];
  try {
    chargeDetail.value = await httpLoadPhysicalExamChargeItems({
      encounterId,
      patientId,
    });
    const warnings = chargeDetail.value?.pricingWarnings ?? [];
    if (warnings.length > 0) {
      const preview = warnings.slice(0, 3).join('；');
      const more = warnings.length > 3 ? `等共 ${warnings.length} 条` : '';
      ElMessage.warning(`部分项目未挂费：${preview}${more}`);
    }
    await selectAllPlannedRows();
  } catch (error) {
    chargeDetail.value = null;
    ElMessage.error('加载收费明细失败');
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
}

function handleChoosePatient(patient: BillingPatientCardPatient) {
  const row = queueRecords.value.find((item) => String(item.encounterId) === String(patient.id));
  void loadDetail(String(patient.id), row?.patientId ? String(row.patientId) : undefined);
}

function onSelectionChange(rows: PhysicalExamChargeItemRowDto[]) {
  selectedChargeRows.value = rows;
}

function openPriceAdjust(row: PhysicalExamChargeItemRowDto) {
  priceAdjustRow.value = row;
  priceAdjustVisible.value = true;
}

/** 打开套餐改价：预填套餐整单当前单价（结算 2 位）。 */
function openPackagePriceAdjust() {
  if (!canAdjustPackagePrice.value) {
    return;
  }
  const packageItem = packageAdjustableItems.value[0];
  packageAdjustAmount.value = scaleMoneySettle(
    packageItem?.unitPrice ?? packageItem?.totalPrice ?? 0,
  );
  packageAdjustVisible.value = true;
}

/** 提交套餐议价：只改套餐 AD 那一条 ChargeItem 单价。 */
async function submitPackagePriceAdjust() {
  if (!selectedEncounterId.value || packageAdjustAmount.value < 0) {
    ElMessage.warning('套餐金额不能为负');
    return;
  }
  const packageItem = packageAdjustableItems.value[0];
  if (!packageItem) {
    return;
  }
  packageAdjustLoading.value = true;
  try {
    await httpAdjustPhysicalExamChargePrices({
      encounterId: selectedEncounterId.value,
      items: [
        {
          chargeItemId: String(packageItem.chargeItemId),
          unitPrice: packageAdjustAmount.value,
          overrideReasonCode: 'PEIS_PACKAGE_PRICE',
          overrideReasonText: '套餐议价',
        },
      ],
    });
    ElMessage.success('套餐改价成功，请重新预结算后再收费');
    packageAdjustVisible.value = false;
    await handlePriceAdjustSuccess();
    await loadQueue();
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error, '套餐改价失败'));
  } finally {
    packageAdjustLoading.value = false;
  }
}

async function handlePriceAdjustSuccess() {
  preSettleSnapshot.value = null;
  lockedChargeItemIds.value = [];
  const row = currentQueueRow.value;
  if (selectedEncounterId.value) {
    await loadDetail(selectedEncounterId.value, row?.patientId ? String(row.patientId) : undefined);
  }
}

async function openSettlementDialog() {
  if (!canSettle.value) {
    ElMessage.warning('请选择待收费明细');
    return;
  }
  settlementLoading.value = true;
  try {
    const ids = selectedPlannedRows.value.map((row) => row.chargeItemId);
    lockedChargeItemIds.value = ids;
    preSettleSnapshot.value = await httpPreSettlePhysicalExamCharges({
      encounterId: selectedEncounterId.value,
      chargeItemIds: ids,
    });
    settlementVisible.value = true;
  } catch (error) {
    ElMessage.error('预结算失败');
    console.error(error);
  } finally {
    settlementLoading.value = false;
  }
}

async function confirmSettlement() {
  if (!preSettleSnapshot.value?.paymentDraftId) {
    return;
  }
  settlementLoading.value = true;
  try {
    const displayAmount = preSettleSnapshot.value.displayAmount ?? 0;
    await httpConfirmPhysicalExamChargeSettlement({
      paymentDraftId: preSettleSnapshot.value.paymentDraftId,
      encounterId: selectedEncounterId.value,
      chargeItemIds: lockedChargeItemIds.value,
      outcomeEnum: SETTLE_OUTCOME_COMPLETED,
      billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      paymentDetails: [
        {
          payChannelCode: payChannelCode.value,
          amount: displayAmount,
        },
      ],
    });
    ElMessage.success('收费成功');
    settlementVisible.value = false;
    preSettleSnapshot.value = null;
    await loadQueue();
    await loadDetail(selectedEncounterId.value, chargeDetail.value?.patientId);
  } catch (error) {
    const backendMsg = extractRequestErrorMessage(error) ?? '';
    const needRePreSettle = isStaleCrossRoleError(error) || backendMsg.includes('重新预结算');
    if (needRePreSettle) {
      settlementVisible.value = false;
      preSettleSnapshot.value = null;
      await loadDetail(selectedEncounterId.value, chargeDetail.value?.patientId);
      // 全局拦截器已展示业务码文案，此处仅补充操作指引，避免重复「正式收费失败」
      ElMessage.warning('请重新点击「收费」并完成预结算后再确认');
    } else {
      const detail = extractRequestErrorMessage(error);
      ElMessage.error(detail || '正式收费失败，请勿重复提交');
    }
    console.error(error);
  } finally {
    settlementLoading.value = false;
  }
}

/**
 * 打开退费确认窗：按所选或全部可退行预览原支付整单。
 *
 * @param row 明细行；空则取全部可退行
 */
async function openRefundDialog(row?: PhysicalExamChargeItemRowDto) {
  if (!selectedEncounterId.value) {
    ElMessage.warning('请先选择体检单');
    return;
  }
  const sourceRows = row ? [row] : refundableChargeItems.value;
  const ids = sourceRows.map((item) => String(item.chargeItemId ?? '')).filter((id) => id !== '');
  if (ids.length === 0) {
    ElMessage.warning('没有可退的已收费项目');
    return;
  }
  refundLoading.value = true;
  try {
    refundPreview.value = await httpPreviewPhysicalExamRefund({
      encounterId: selectedEncounterId.value,
      chargeItemIds: ids,
    });
    refundVisible.value = true;
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error) ?? '加载退费信息失败');
  } finally {
    refundLoading.value = false;
  }
}

/** 确认退费：银联原路退后执行院内冲正。 */
async function confirmRefund() {
  const preview = refundPreview.value;
  const encounterId = selectedEncounterId.value;
  if (!preview || !encounterId) {
    return;
  }
  refundLoading.value = true;
  let clientRefundResult: Awaited<ReturnType<typeof submitScanRefund>> | undefined;
  try {
    try {
      clientRefundResult =
        (await submitScanRefund({
          originalPaymentId: preview.originalPaymentId,
          originalPayChannelCodes: preview.originalPayChannelCodes,
          refundAmountYuan: preview.refundableAmount,
        })) ?? undefined;
    } catch (error) {
      const message = error instanceof Error ? error.message : '退费前银联校验失败，请稍后重试';
      ElMessage.error(message);
      return;
    }
    await httpRefundPhysicalExamCharges({
      billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      encounterId,
      originalPaymentId: preview.originalPaymentId,
      refundingChargeItemIds: (preview.refundingChargeItemIds ?? []).map((id) => String(id)),
      ...(clientRefundResult ? { clientRefundResult } : {}),
    });
    ElMessage.success('退费已完成');
    clearCachedClientRefundResult(preview.originalPaymentId);
    refundVisible.value = false;
    refundPreview.value = null;
    const patientId = chargeDetail.value?.patientId;
    queueTabCode.value = QUEUE_TAB.WAITING;
    await loadQueue();
    await loadDetail(encounterId, patientId);
  } catch (error: unknown) {
    const message = extractRequestErrorMessage(error) ?? '退费失败';
    if (clientRefundResult) {
      ElMessage.error(
        `${message}。银联扫码段可能已退成功，请勿重复人工退货；请直接再次点「确认退费」完成本院收口。`,
      );
    } else {
      ElMessage.error(message);
    }
  } finally {
    refundLoading.value = false;
  }
}

async function initPage() {
  try {
    const init = await httpLoadPhysicalExamChargePageInit();
    paymentChannelOptions.value = optionDtosToPayChannelOptions(
      init.paymentChannelOptions as OptionDto[] | undefined,
    );
    if (paymentChannelOptions.value.length > 0) {
      payChannelCode.value = paymentChannelOptions.value[0]?.value ?? 'WECHAT';
    }
  } catch (error) {
    console.error(error);
  }
}

watch(queueTabCode, () => {
  if (!filtersReady.value) {
    return;
  }
  queuePager.pageNo = 1;
  selectedEncounterId.value = '';
  chargeDetail.value = null;
  selectedChargeRows.value = [];
  void loadQueue();
});

watch([keyword, dateRange], () => {
  if (!filtersReady.value) {
    return;
  }
  queuePager.pageNo = 1;
  debouncedLoadQueue();
});

onMounted(async () => {
  if (!dateRange.value) {
    const today = dayjs().format('YYYY-MM-DD');
    dateRange.value = [today, today];
  }
  await initPage();
  await loadQueue();
  filtersReady.value = true;
  const encounterId = String(route.query.encounterId ?? '').trim();
  const patientId = String(route.query.patientId ?? '').trim();
  if (encounterId) {
    await loadDetail(encounterId, patientId || undefined);
  }
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden">
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="charge-outpatient-workbench !h-full min-h-0 flex-1"
      left-track="minmax(280px, 0.36fr)"
      left-track-lg="minmax(300px, 0.34fr)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <div
          class="flex h-full min-h-0 flex-col gap-2 overflow-hidden p-2"
          v-loading="queueLoading"
        >
          <div class="shrink-0 px-0.5 pt-0.5">
            <SectionTitle title="体检收费队列" />
            <div class="charge-filter-toolbar mt-2">
              <div class="charge-filter-toolbar__main-col flex flex-col gap-2">
                <SelectFuzzyInput v-model="keyword" placeholder="流水号 / 姓名" full-width />
                <ElRadioGroup v-model="queueTabCode" class="charge-filter-toolbar__status">
                  <ElRadio :value="QUEUE_TAB.WAITING">待收费</ElRadio>
                  <ElRadio :value="QUEUE_TAB.COMPLETE">已收费</ElRadio>
                </ElRadioGroup>
              </div>
              <div class="charge-filter-toolbar__date-row flex min-w-0 items-center gap-1.5">
                <ElDatePicker
                  v-model="dateRange"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  unlink-panels
                  clearable
                  class="charge-filter-toolbar__date min-w-0 flex-1"
                />
                <ElButton
                  type="default"
                  class="!shrink-0 !px-2"
                  title="刷新队列"
                  :loading="queueLoading"
                  @click="loadQueue"
                >
                  <RotateCw class="h-4 w-4" />
                </ElButton>
              </div>
            </div>
          </div>

          <div class="charge-patient-scroll min-h-0 flex-1 space-y-2 overflow-y-auto px-0.5 pb-1">
            <template v-if="(queueRecords ?? []).length === 0">
              <div class="py-8 text-center text-sm text-[var(--el-text-color-placeholder)]">
                暂无收费记录
              </div>
            </template>
            <BillingPatientCard
              v-for="row in queueRecords ?? []"
              :key="row.encounterId"
              :patient="mapToBillingPatientCard(row)"
              :selected="String(row.encounterId) === selectedEncounterId"
              @select="handleChoosePatient"
            />
          </div>

          <div class="shrink-0 pt-1">
            <ElPagination
              v-model:current-page="queuePager.pageNo"
              v-model:page-size="queuePager.pageSize"
              :disabled="queueLoading"
              :page-sizes="[10, 20, 50]"
              :total="queueTotal"
              background
              layout="total, sizes, prev, pager, next"
              small
              @current-change="loadQueue"
              @size-change="loadQueue"
            />
          </div>
        </div>
      </template>

      <template #center>
        <ElCard
          shadow="never"
          class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
          body-class="!flex !h-full !min-h-0 !flex-col !overflow-hidden !p-2"
          v-loading="detailLoading"
        >
          <SectionTitle class="mb-2 shrink-0" title="体检信息" />

          <template v-if="!selectedEncounterId">
            <div
              class="flex flex-1 items-center justify-center text-sm text-[var(--el-text-color-placeholder)]"
            >
              请从左侧选择体检单
            </div>
          </template>

          <template v-else>
            <div class="flex min-h-0 flex-1 flex-col gap-3 overflow-hidden">
              <div class="patient-info-bar shrink-0">
                <div class="patient-info-grid">
                  <div class="patient-info-item">
                    <span class="patient-info-label">患者姓名</span>
                    <span class="patient-info-value">
                      {{ currentQueueRow?.patientName || '—' }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">性别</span>
                    <span class="patient-info-value">
                      {{ formatGender(currentQueueRow) }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">年龄</span>
                    <span class="patient-info-value">
                      {{ currentQueueRow?.ageText || '—' }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">流水号</span>
                    <span class="patient-info-value">
                      {{ chargeDetail?.busNo || currentQueueRow?.busNo || '—' }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">类型</span>
                    <span class="patient-info-value">
                      {{ currentQueueRow?.groupFlag ? '团检' : '个检' }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">套餐</span>
                    <span class="patient-info-value patient-info-value--with-action">
                      {{ chargeDetail?.packageName || currentQueueRow?.packageName || '—' }}
                      <ElButton
                        v-if="canAdjustPackagePrice"
                        link
                        type="primary"
                        @click="openPackagePriceAdjust"
                      >
                        套餐改价
                      </ElButton>
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">登记时间</span>
                    <span class="patient-info-value">
                      {{ formatStartTime(currentQueueRow?.startTime) }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">待收合计</span>
                    <span class="patient-info-value text-[var(--el-color-warning)]">
                      ¥{{ formatMoney(chargeDetail?.plannedTotalAmount) }}
                    </span>
                  </div>
                  <div class="patient-info-item">
                    <span class="patient-info-label">已收合计</span>
                    <span class="patient-info-value text-[var(--el-color-success)]">
                      ¥{{ formatMoney(chargeDetail?.settledTotalAmount) }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
                <div class="mb-2 flex shrink-0 flex-wrap items-center gap-2">
                  <SectionTitle title="收费明细" />
                  <div class="flex-1"></div>
                  <span class="mr-2 text-sm text-[var(--el-text-color-regular)]">
                    已选待收 {{ selectedPlannedRows.length }} 项，合计 ¥{{
                      formatMoney(selectedChargeSum)
                    }}
                  </span>
                  <ElButton
                    type="primary"
                    :disabled="!canSettle"
                    :loading="settlementLoading"
                    @click="openSettlementDialog"
                  >
                    预结算并收费
                  </ElButton>
                  <ElButton
                    type="danger"
                    plain
                    :disabled="!canRefund"
                    :loading="refundLoading"
                    @click="openRefundDialog()"
                  >
                    退费
                  </ElButton>
                </div>

                <div class="charge-detail-wrapper min-h-0 flex-1 overflow-auto">
                  <ElTable
                    ref="chargeTableRef"
                    :data="chargeDetail?.items ?? []"
                    row-key="chargeItemId"
                    :max-height="560"
                    @selection-change="onSelectionChange"
                  >
                    <ElTableColumn
                      type="selection"
                      width="48"
                      :selectable="(row) => row.status === 'PLANNED'"
                    />
                    <ElTableColumn
                      prop="itemLabel"
                      label="项目"
                      min-width="180"
                      show-overflow-tooltip
                    />
                    <ElTableColumn label="单价" min-width="100" align="right">
                      <template #default="{ row }"> ¥{{ formatMoney(row.unitPrice) }} </template>
                    </ElTableColumn>
                    <ElTableColumn label="数量" prop="quantity" min-width="80" align="center" />
                    <ElTableColumn label="金额" min-width="110" align="right">
                      <template #default="{ row }"> ¥{{ formatMoney(row.totalPrice) }} </template>
                    </ElTableColumn>
                    <ElTableColumn label="状态" min-width="100" align="center">
                      <template #default="{ row }">
                        <ElTag :type="chargeStatusTagType(row.status)" size="small" effect="plain">
                          {{ chargeStatusLabel(row.status) }}
                        </ElTag>
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="操作" width="88" align="center" fixed="right">
                      <template #default="{ row }">
                        <ElButton
                          v-if="row.priceEditable"
                          link
                          type="primary"
                          @click="openPriceAdjust(row)"
                        >
                          改价
                        </ElButton>
                        <ElButton
                          v-else-if="canRefundRow(row)"
                          link
                          type="danger"
                          :loading="refundLoading"
                          @click="openRefundDialog(row)"
                        >
                          退费
                        </ElButton>
                      </template>
                    </ElTableColumn>
                  </ElTable>
                </div>
              </div>
            </div>
          </template>
        </ElCard>
      </template>
    </StationTriPaneLayout>

    <ElDialog v-model="settlementVisible" title="确认收费（自费）" width="520px">
      <div class="space-y-3">
        <div>
          应付金额：
          <strong class="text-lg text-[var(--el-color-danger)]">
            ¥{{ formatMoney(preSettleSnapshot?.displayAmount) }}
          </strong>
        </div>
        <div class="flex items-center gap-2">
          <span>支付渠道</span>
          <ElRadioGroup v-model="payChannelCode">
            <ElRadio v-for="opt in paymentChannelOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </ElRadio>
          </ElRadioGroup>
        </div>
        <ElTable :data="preSettleSnapshot?.previews ?? []" size="small" max-height="280">
          <ElTableColumn prop="itemLabel" label="项目" min-width="160" />
          <ElTableColumn label="金额" min-width="100" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.totalPrice) }}</template>
          </ElTableColumn>
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="settlementVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="settlementLoading" @click="confirmSettlement">
          确认收费
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="refundVisible" title="确认退费" width="520px">
      <div class="space-y-3">
        <div>
          应退金额：
          <strong class="text-lg text-[var(--el-color-danger)]">
            ¥{{ formatMoney(refundPreview?.refundableAmount) }}
          </strong>
        </div>
        <div class="text-sm text-[var(--el-text-color-secondary)]">
          退费渠道按原支付原路返回；同一笔收费须整单退费。
        </div>
        <ElTable :data="refundPreview?.previews ?? []" size="small" max-height="280">
          <ElTableColumn prop="itemLabel" label="项目" min-width="160" />
          <ElTableColumn label="金额" min-width="100" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.totalPrice) }}</template>
          </ElTableColumn>
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="refundVisible = false">取消</ElButton>
        <ElButton type="danger" :loading="refundLoading" @click="confirmRefund">
          确认退费
        </ElButton>
      </template>
    </ElDialog>

    <PeisChargePriceAdjustDialog
      v-model:visible="priceAdjustVisible"
      :encounter-id="selectedEncounterId"
      :row="priceAdjustRow"
      @success="handlePriceAdjustSuccess"
    />

    <ElDialog
      v-model="packageAdjustVisible"
      title="套餐改价"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="space-y-3">
        <div class="text-sm text-[var(--el-text-color-regular)]">
          套餐：
          <strong>{{ chargeDetail?.packageName || currentQueueRow?.packageName || '—' }}</strong>
        </div>
        <div class="text-sm text-[var(--el-text-color-secondary)]">
          当前套餐价：¥{{ formatMoney(packageAdjustableItems[0]?.unitPrice) }}
        </div>
        <ElForm label-width="88px">
          <ElFormItem label="新套餐价" required>
            <ElInputNumber
              v-model="packageAdjustAmount"
              :min="0"
              :precision="2"
              :step="1"
              class="!w-full"
            />
          </ElFormItem>
        </ElForm>
      </div>
      <template #footer>
        <ElButton @click="packageAdjustVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="packageAdjustLoading" @click="submitPackagePriceAdjust">
          确认改价
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.patient-info-value--with-action {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
</style>
