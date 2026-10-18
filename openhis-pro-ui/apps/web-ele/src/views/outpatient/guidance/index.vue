<script setup lang="ts">
import type {
  ChargeQueueRowDto,
  PendingChargeItemRowDto,
} from '#/api/outpatient-charge/outpatient-charge.types';
import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { ServiceApplyKind } from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useUserStore } from '@zxplat/stores';

import { useDebounceFn } from '@vueuse/core';
import { ElDatePicker, ElMessage, ElPagination } from 'element-plus';

import {
  httpLoadPendingChargeItems,
  httpPageOutpatientChargeQueue,
} from '#/api/outpatient-charge/outpatient-charge.http';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import {
  getDateRangeQuick,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';
import { formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
  SERVICE_CATEGORY_TREATMENT,
} from '#/utils/print/constants/service-request-category-codes';
import {
  isBloodCollectionFeePrintItem,
  isVenousBloodCollectionPrintItem,
} from '#/utils/print/nurse-station-print-targets';
import { printServiceApplyFromNurseTreatmentLines } from '#/utils/print/print-service-apply';
import { OUTPATIENT_NURSE_SERVICE_APPLY_PRINT_ENABLED } from '#/views/outpatient/nurse-station/constants/nurse-print-apply.config';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';
import { SERVICE_APPLY_KIND_LABEL } from '#/views/outpatient/doctor-station/service-apply/service-apply.constants';

import './guidance.css';

defineOptions({
  name: 'OutpatientGuidance',
});

usePrintClientWarmup();

const QUEUE_TAB = {
  ALL: 'ALL',
  PENDING: 'PENDING',
  SETTLED: 'SETTLED',
} as const;

/** 开立内容 Tab：默认全部；查询条件含检查/检验/治疗/西药/耗材 */
type OrderTabCode = 'all' | 'device' | 'exam' | 'lab' | 'treatment' | 'western';

const QUEUE_TAB_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '全部', value: QUEUE_TAB.ALL },
  { label: '待收费', value: QUEUE_TAB.PENDING },
  { label: '已结算', value: QUEUE_TAB.SETTLED },
];

const ORDER_TAB_OPTIONS: Array<{ code: OrderTabCode; label: string }> = [
  { code: 'all', label: '全部' },
  { code: 'exam', label: '检查' },
  { code: 'lab', label: '检验' },
  { code: 'treatment', label: '治疗' },
  { code: 'western', label: '西药' },
  { code: 'device', label: '耗材' },
];

const APPLY_KIND_ORDER: ServiceApplyKind[] = ['exam', 'lab', 'treatment'];

const userStore = useUserStore();

const keyword = ref('');
const queueTabCode = ref<string>(QUEUE_TAB.SETTLED);
const dateRange = ref<[string, string] | null>(getDateRangeQuick({ strategy: 'calendar-day' }));
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();
const queuePager = reactive({
  pageNo: 1,
  pageSize: 20,
});
const queueRecords = ref<ChargeQueueRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedEncounterId = ref('');
const sheetLoading = ref(false);
const printSubmitting = ref(false);
const printingKind = ref<null | ServiceApplyKind | 'all'>(null);
/** 收费明细，与收费页 loadPendingChargeItems 同一份列表 */
const chargeItems = ref<PendingChargeItemRowDto[]>([]);
/** 开立内容默认全部 */
const activeOrderTab = ref<OrderTabCode>('all');

type GuidanceOrderCategory = Exclude<OrderTabCode, 'all'> | 'other';

interface GuidanceOrderRow {
  category: GuidanceOrderCategory;
  categoryLabel: string;
  detailText: string;
  feeText: string;
  itemName: string;
  quantityText: string;
  rowKey: string;
  statusClass: string;
  statusText: string;
  usageText: string;
}

const currentQueueRow = computed(
  () => queueRecords.value.find((row) => row.encounterId === selectedEncounterId.value) ?? null,
);

const genderText = computed(() => chargeQueueGenderDisplay(currentQueueRow.value));

const patientInitial = computed(() => {
  const name = String(currentQueueRow.value?.patientName ?? '').trim();
  return name ? name.slice(0, 1) : '患';
});

const isSettledSelected = computed(
  () => currentQueueRow.value?.queueLabelCode === QUEUE_TAB.SETTLED,
);

const statusLabel = computed(() => (isSettledSelected.value ? '已结算' : '待收费'));

const hospitalName = computed(() => resolvePrintHospitalName() || '门诊导诊');

const orderRows = computed(() => chargeItems.value.map(mapChargeItemToOrderRow));

const orderTabCounts = computed<Record<OrderTabCode, number>>(() => ({
  all: orderRows.value.length,
  exam: orderRows.value.filter((row) => row.category === 'exam').length,
  lab: orderRows.value.filter((row) => row.category === 'lab').length,
  treatment: orderRows.value.filter((row) => row.category === 'treatment').length,
  western: orderRows.value.filter((row) => row.category === 'western').length,
  device: orderRows.value.filter((row) => row.category === 'device').length,
}));

const displayedOrderRows = computed<GuidanceOrderRow[]>(() => {
  if (activeOrderTab.value === 'all') {
    return orderRows.value;
  }
  return orderRows.value.filter((row) => row.category === activeOrderTab.value);
});

/** 与开立列表同一批收费明细；检查/检验/治疗有未退费行，对应打印按钮就可点 */
const printLines = computed(() => chargeItemsToPrintLines(chargeItems.value));

const printableExamCount = computed(
  () => printLinesOfKind(printLines.value, 'exam').length,
);
const printableLabCount = computed(
  () => printLinesOfKind(printLines.value, 'lab').length,
);
const printableTreatmentCount = computed(
  () => printLinesOfKind(printLines.value, 'treatment').length,
);

const printableItemTotal = computed(
  () => printableExamCount.value + printableLabCount.value + printableTreatmentCount.value,
);

const canPrintAny = computed(
  () =>
    Boolean(selectedEncounterId.value) && printableItemTotal.value > 0 && !printSubmitting.value,
);

const canPrintExam = computed(() => printableExamCount.value > 0 && !printSubmitting.value);
const canPrintLab = computed(() => printableLabCount.value > 0 && !printSubmitting.value);
const canPrintTreatment = computed(
  () => printableTreatmentCount.value > 0 && !printSubmitting.value,
);

function chargeQueueGenderDisplay(row: ChargeQueueRowDto | null | undefined): string {
  if (!row) return '—';
  const text = (row.genderEnumText ?? '').trim();
  if (text) return text;
  const code = (row.genderCode ?? '').trim();
  return code || '—';
}

function resolveEncounterIdForApi(raw: string): string {
  return preserveApiIdForRequest(raw) ?? '';
}

function resolveStatusClass(statusText: string): string {
  const text = statusText.trim();
  if (!text) return 'run';
  if (/已|完成|出报告/.test(text)) return 'ok';
  if (/待|未/.test(text)) return 'wait';
  return 'run';
}

function formatFee(value: null | number | string | undefined): string {
  if (value === null || value === undefined || String(value).trim() === '') {
    return '—';
  }
  return formatMoneyYuanWithSymbol(value);
}

/** 收费目录分类 → 导诊 Tab。对不上检查/检验/治疗/西药/耗材的行仍留在「全部」。 */
function resolveChargeItemCategory(item: PendingChargeItemRowDto): {
  category: GuidanceOrderCategory;
  categoryLabel: string;
} {
  const display = String(item.chargeCategoryDisplay ?? '').trim();
  const code = String(item.chargeCategoryCode ?? '').trim();
  const table = String(item.serviceTable ?? '').trim().toLowerCase();
  if (display.includes('检查') || code === '23') {
    return { category: 'exam', categoryLabel: display || '检查' };
  }
  if (display.includes('检验') || code === '22') {
    return { category: 'lab', categoryLabel: display || '检验' };
  }
  if (display.includes('治疗') || code === '21') {
    return { category: 'treatment', categoryLabel: display || '治疗' };
  }
  if (display.includes('耗材') || table.includes('device')) {
    return { category: 'device', categoryLabel: display || '耗材' };
  }
  if (
    display.includes('草药') ||
    display.includes('饮片') ||
    (table.includes('medication') && code === HERBAL_MEDICATION_CATEGORY_CODE)
  ) {
    return { category: 'other', categoryLabel: display || '中草药' };
  }
  if (
    display.includes('西药') ||
    display.includes('中成药') ||
    table.includes('medication')
  ) {
    return { category: 'western', categoryLabel: display || '西药' };
  }
  return { category: 'other', categoryLabel: display || '其他' };
}

const APPLY_KIND_CATEGORY_CODE: Record<ServiceApplyKind, string> = {
  exam: SERVICE_CATEGORY_TEST,
  lab: SERVICE_CATEGORY_PROOF,
  treatment: SERVICE_CATEGORY_TREATMENT,
};

function isRefundedChargeItem(item: PendingChargeItemRowDto): boolean {
  const code = String(item.statusCode ?? '').trim().toUpperCase();
  return code === 'REFUNDED' || code === 'REFUNDING';
}

function isApplyKindCategory(category: GuidanceOrderCategory): category is ServiceApplyKind {
  return category === 'exam' || category === 'lab' || category === 'treatment';
}

function printLinesOfKind(
  lines: NurseStationTreatmentLineDto[],
  kind: ServiceApplyKind,
): NurseStationTreatmentLineDto[] {
  return lines.filter((line) => {
    if (!line.requestId) {
      return false;
    }
    if (isVenousBloodCollectionPrintItem(line.itemName)) {
      return kind === 'lab';
    }
    return line.serviceCategoryCode === APPLY_KIND_CATEGORY_CODE[kind];
  });
}

/**
 * 开立列表里归到检查/检验/治疗、且未退费的收费行，逐条变成申请单打印行。
 * 打印时按类目+执行科室合单；静脉采血并入检验单，不单独打治疗单。
 */
function chargeItemsToPrintLines(items: PendingChargeItemRowDto[]): NurseStationTreatmentLineDto[] {
  const lines: NurseStationTreatmentLineDto[] = [];
  for (const item of items) {
    const mapped = resolveChargeItemCategory(item);
    const itemName = String(item.itemName ?? '').trim();
    let printCategory = mapped.category;
    if (isVenousBloodCollectionPrintItem(itemName)) {
      printCategory = 'lab';
    }
    if (!isApplyKindCategory(printCategory)) {
      continue;
    }
    if (isRefundedChargeItem(item)) {
      continue;
    }
    if (printCategory === 'lab' && isBloodCollectionFeePrintItem(itemName)) {
      continue;
    }
    const requestId =
      String(item.serviceId ?? '').trim() || String(item.chargeItemId ?? '').trim();
    if (!requestId) {
      continue;
    }
    const busNo = String(item.busNo ?? '').trim();
    const performOrgId = String(item.performOrgId ?? '').trim();
    const performOrgName = String(item.performOrgName ?? '').trim();
    lines.push({
      requestId,
      requestTable: 'wkf_service_request',
      serviceCategoryCode: APPLY_KIND_CATEGORY_CODE[printCategory],
      itemName,
      quantity: item.quantityValue,
      unitCode: String(item.quantityUnit ?? '').trim() || undefined,
      prescriptionNo: busNo.startsWith('AR') ? busNo : undefined,
      chargeStatusEnum: item.statusCode,
      displayStatusEnumText: item.statusText,
      performOrgId: performOrgId || undefined,
      performOrgName: performOrgName || undefined,
    });
  }
  return lines;
}

function formatChargeQuantity(item: PendingChargeItemRowDto): string {
  const display = String(item.quantityDisplay ?? '').trim();
  if (display) {
    return display;
  }
  const qty = item.quantityValue;
  if (qty === null || qty === undefined || String(qty).trim() === '') {
    return '—';
  }
  const unit = String(item.quantityUnit ?? '').trim();
  return unit ? `${qty}${unit}` : String(qty);
}

function mapChargeItemToOrderRow(item: PendingChargeItemRowDto): GuidanceOrderRow {
  const category = resolveChargeItemCategory(item);
  const statusText = String(item.statusText ?? '').trim() || '—';
  const feeSource =
    item.discountAmount !== null &&
    item.discountAmount !== undefined &&
    String(item.discountAmount).trim() !== ''
      ? item.discountAmount
      : item.totalPrice;
  return {
    rowKey: String(item.chargeItemId ?? item.busNo ?? item.itemName ?? ''),
    category: category.category,
    categoryLabel: category.categoryLabel,
    itemName: String(item.itemName ?? '').trim() || '未命名项目',
    detailText: '',
    quantityText: formatChargeQuantity(item),
    usageText: String(item.performOrgName ?? '').trim(),
    feeText: formatFee(feeSource),
    statusText,
    statusClass: resolveStatusClass(statusText),
  };
}

function resolvePrintHospitalName(): string {
  const userInfo = userStore.userInfo as null | Record<string, unknown> | undefined;
  if (!userInfo) return '';
  const candidates = [userInfo.hospitalName, userInfo.tenantName, userInfo.orgName];
  for (const item of candidates) {
    const text = String(item ?? '').trim();
    if (text) return text;
  }
  return '';
}

function buildPrintPatient() {
  const row = currentQueueRow.value;
  return {
    patientName: row?.patientName ?? '',
    genderText: genderText.value === '—' ? '' : genderText.value,
    ageText: row?.ageText ?? '',
    encounterNo: row?.encounterBusNo ?? '',
  };
}

async function loadChargeQueue() {
  queueLoading.value = true;
  try {
    const body = {
      endDate: undefined as string | undefined,
      keyword: keyword.value.trim() || undefined,
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      queueTabCode: queueTabCode.value,
      startDate: undefined as string | undefined,
    };
    const range = dateRange.value;
    if (range?.[0] && range[1]) {
      body.startDate = getLocalDayRangeDateTimeStrings(range[0]).startDate;
      body.endDate = getLocalDayRangeDateTimeStrings(range[1]).endDate;
    }
    const res = await httpPageOutpatientChargeQueue(body);
    const { content, totalCount } = parsePagedRowsFromApiData<ChargeQueueRowDto>(res);
    queueRecords.value = content;
    queueTotal.value = totalCount;
    if (
      selectedEncounterId.value &&
      !content.some((row) => row.encounterId === selectedEncounterId.value)
    ) {
      selectedEncounterId.value = '';
    }
  } finally {
    queueLoading.value = false;
  }
}

async function loadSelectedPatientOrders() {
  const encounterId = resolveEncounterIdForApi(selectedEncounterId.value);
  if (!encounterId) {
    chargeItems.value = [];
    return;
  }
  sheetLoading.value = true;
  const paymentId = String(currentQueueRow.value?.paymentId ?? '').trim() || undefined;
  try {
    const chargeResult = await httpLoadPendingChargeItems({
      encounterId,
      includePriceExtension: true,
      paymentId,
    });
    chargeItems.value = chargeResult.items ?? [];
  } catch {
    chargeItems.value = [];
  } finally {
    sheetLoading.value = false;
  }
}

const resetQueuePageAndLoad = () => {
  queuePager.pageNo = 1;
  void loadChargeQueue();
};

const debouncedQueueReload = useDebounceFn(resetQueuePageAndLoad, 400);

watch(keyword, () => {
  debouncedQueueReload();
});

watch(dateRange, () => {
  debouncedQueueReload();
});

watch(queueTabCode, () => {
  resetQueuePageAndLoad();
});

watch(selectedEncounterId, () => {
  activeOrderTab.value = 'all';
  void loadSelectedPatientOrders();
});

function handleChoosePatient(row: ChargeQueueRowDto) {
  const id = String(row.encounterId ?? '');
  if (!id) return;
  if (selectedEncounterId.value === id) {
    void loadSelectedPatientOrders();
    return;
  }
  selectedEncounterId.value = id;
}

async function printApplyKind(applyKind: ServiceApplyKind) {
  if (!OUTPATIENT_NURSE_SERVICE_APPLY_PRINT_ENABLED) {
    ElMessage.warning('当前院点不支持打印检查/检验/治疗申请单');
    return;
  }
  const encounterId = resolveEncounterIdForApi(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const kindLines = printLinesOfKind(printLines.value, applyKind);
  if (kindLines.length === 0) {
    ElMessage.warning(`没有可打印的${SERVICE_APPLY_KIND_LABEL[applyKind]}申请单`);
    return;
  }
  printSubmitting.value = true;
  printingKind.value = applyKind;
  try {
    const row = currentQueueRow.value;
    const results = await printServiceApplyFromNurseTreatmentLines({
      applyKind,
      encounterId,
      patientId: preserveApiIdForRequest(row?.patientId) ?? undefined,
      patient: buildPrintPatient(),
      serviceLines: kindLines,
      mergeByApplyKind: true,
      printMeta: {
        hospitalName: resolvePrintHospitalName(),
        departmentName: row?.organizationName ?? '',
        orderingPractitionerName: row?.doctorName ?? '',
      },
      orderDateText: row?.startTime ?? '',
    });
    const successCount = results.filter((item) => item.ok).length;
    if (successCount <= 0) {
      ElMessage.warning(`没有可打印的${SERVICE_APPLY_KIND_LABEL[applyKind]}申请单`);
      return;
    }
    ElMessage.success(`已提交 ${successCount} 张${SERVICE_APPLY_KIND_LABEL[applyKind]}申请单打印`);
  } finally {
    printSubmitting.value = false;
    printingKind.value = null;
  }
}

async function printAllApplyKinds() {
  if (!OUTPATIENT_NURSE_SERVICE_APPLY_PRINT_ENABLED) {
    ElMessage.warning('当前院点不支持打印检查/检验/治疗申请单');
    return;
  }
  const encounterId = resolveEncounterIdForApi(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const kinds = APPLY_KIND_ORDER.filter(
    (kind) => printLinesOfKind(printLines.value, kind).length > 0,
  );
  if (kinds.length === 0) {
    ElMessage.warning('当前患者没有可打印的申请单');
    return;
  }
  printSubmitting.value = true;
  printingKind.value = 'all';
  try {
    const row = currentQueueRow.value;
    let totalSuccess = 0;
    let userCancelled = false;
    for (const applyKind of kinds) {
      const results = await printServiceApplyFromNurseTreatmentLines({
        applyKind,
        encounterId,
        patientId: preserveApiIdForRequest(row?.patientId) ?? undefined,
        patient: buildPrintPatient(),
        serviceLines: printLinesOfKind(printLines.value, applyKind),
        mergeByApplyKind: true,
        printMeta: {
          hospitalName: resolvePrintHospitalName(),
          departmentName: row?.organizationName ?? '',
          orderingPractitionerName: row?.doctorName ?? '',
        },
        orderDateText: row?.startTime ?? '',
      });
      totalSuccess += results.filter((item) => item.ok).length;
      if (results.some((item) => !item.ok && item.errorCode === 'USER_CANCELLED')) {
        userCancelled = true;
        break;
      }
    }
    if (userCancelled) {
      return;
    }
    if (totalSuccess <= 0) {
      ElMessage.warning('没有可打印的申请单');
      return;
    }
    ElMessage.success(`已提交 ${totalSuccess} 张申请单打印`);
  } finally {
    printSubmitting.value = false;
    printingKind.value = null;
  }
}

onMounted(() => {
  void loadChargeQueue();
});
</script>

<template>
  <div class="guidance-app">
    <header class="guidance-topbar">
      <div class="guidance-brand">
        <div class="guidance-brand-logo">十</div>
        <div>
          <div class="guidance-brand-title">门诊导诊工作台</div>
          <div class="guidance-brand-sub">{{ hospitalName }}</div>
        </div>
      </div>
    </header>

    <div class="guidance-main">
      <aside class="guidance-queue-panel">
        <div class="guidance-queue-head">
          <div class="guidance-queue-title">
            患者队列
            <span class="guidance-count">{{ queueTotal }}</span>
          </div>
          <button
            type="button"
            class="guidance-icon-btn"
            title="刷新队列"
            :disabled="queueLoading"
            @click="loadChargeQueue"
          >
            ↻
          </button>
        </div>

        <div class="guidance-search-box">
          <span class="guidance-search-icon">🔍</span>
          <input v-model="keyword" placeholder="搜索姓名 / 业务号" autocomplete="off" />
        </div>

        <div class="guidance-filter-tabs">
          <button
            v-for="opt in QUEUE_TAB_OPTIONS"
            :key="opt.value"
            type="button"
            class="guidance-ftab"
            :class="{ active: queueTabCode === opt.value }"
            @click="queueTabCode = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>

        <div class="guidance-date-row">
          <ElDatePicker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            unlink-panels
            clearable
            :shortcuts="dateRangeShortcuts"
          />
        </div>

        <div class="guidance-queue-list">
          <div v-if="queueRecords.length === 0" class="guidance-empty-small">
            {{ queueLoading ? '正在载入就诊…' : '暂无符合条件的患者' }}
          </div>
          <button
            v-for="row in queueRecords"
            :key="row.encounterId"
            type="button"
            class="guidance-q-item"
            :class="{ active: row.encounterId === selectedEncounterId }"
            @click="handleChoosePatient(row)"
          >
            <div class="guidance-q-main">
              <div class="guidance-q-row1">
                <span class="guidance-q-name">{{ row.patientName || '—' }}</span>
                <span class="guidance-q-meta">
                  {{ chargeQueueGenderDisplay(row) }} · {{ row.ageText || '—' }}
                </span>
                <span
                  class="guidance-badge"
                  :class="row.queueLabelCode === QUEUE_TAB.SETTLED ? 'settled' : 'pending'"
                >
                  {{ row.queueLabelCode === QUEUE_TAB.SETTLED ? '已结算' : '待收费' }}
                </span>
              </div>
              <div class="guidance-q-row2">{{ row.organizationName || '—' }}</div>
              <div class="guidance-q-row3">
                <span class="guidance-q-wait">{{ row.startTime || '—' }}</span>
                <span>{{ row.encounterBusNo || '' }}</span>
              </div>
            </div>
          </button>
        </div>

        <div class="guidance-queue-pager">
          <ElPagination
            v-model:current-page="queuePager.pageNo"
            v-model:page-size="queuePager.pageSize"
            :disabled="queueLoading"
            :page-sizes="[10, 20, 50]"
            :total="queueTotal"
            background
            layout="total, prev, pager, next"
            small
            @current-change="loadChargeQueue"
            @size-change="
              () => {
                queuePager.pageNo = 1;
                loadChargeQueue();
              }
            "
          />
        </div>
      </aside>

      <section class="guidance-detail-panel">
        <div v-if="!selectedEncounterId" class="guidance-empty">请从左侧队列中选择一位患者</div>

        <template v-else>
          <div class="guidance-patient-card">
            <div class="guidance-pc-head">
              <div class="guidance-pc-avatar">{{ patientInitial }}</div>
              <div class="guidance-pc-id">
                <div class="guidance-pc-name">
                  {{ currentQueueRow?.patientName || '—' }}
                  <span class="guidance-badge" :class="isSettledSelected ? 'settled' : 'pending'">
                    {{ statusLabel }}
                  </span>
                </div>
                <div class="guidance-pc-sub">
                  {{ genderText }} · {{ currentQueueRow?.ageText || '—' }} ·
                  {{ currentQueueRow?.organizationName || '—' }} ·
                  {{ currentQueueRow?.doctorName || '—' }}
                </div>
              </div>
              <div v-if="OUTPATIENT_NURSE_SERVICE_APPLY_PRINT_ENABLED" class="guidance-pc-actions">
                <span class="guidance-btn-wrap">
                  <button
                    type="button"
                    class="guidance-btn guidance-btn-ghost"
                    :disabled="!canPrintExam"
                    @click="printApplyKind('exam')"
                  >
                    {{ printingKind === 'exam' ? '打印中…' : '打印检查单' }}
                  </button>
                  <i v-if="printableExamCount > 0" class="guidance-btn-dot" aria-hidden="true"></i>
                </span>
                <span class="guidance-btn-wrap">
                  <button
                    type="button"
                    class="guidance-btn guidance-btn-ghost"
                    :disabled="!canPrintLab"
                    @click="printApplyKind('lab')"
                  >
                    {{ printingKind === 'lab' ? '打印中…' : '打印检验单' }}
                  </button>
                  <i v-if="printableLabCount > 0" class="guidance-btn-dot" aria-hidden="true"></i>
                </span>
                <span class="guidance-btn-wrap">
                  <button
                    type="button"
                    class="guidance-btn guidance-btn-soft"
                    :disabled="!canPrintTreatment"
                    @click="printApplyKind('treatment')"
                  >
                    {{ printingKind === 'treatment' ? '打印中…' : '打印治疗单' }}
                  </button>
                  <i
                    v-if="printableTreatmentCount > 0"
                    class="guidance-btn-dot"
                    aria-hidden="true"
                  ></i>
                </span>
                <button
                  type="button"
                  class="guidance-btn guidance-btn-primary"
                  :disabled="!canPrintAny"
                  @click="printAllApplyKinds"
                >
                  {{ printingKind === 'all' ? '打印中…' : '一键打印申请单' }}
                </button>
              </div>
            </div>

            <div class="guidance-pc-info">
              <div class="guidance-info-item">
                <span class="k">就诊业务号</span>
                <span class="v">{{ currentQueueRow?.encounterBusNo || '—' }}</span>
              </div>
              <div class="guidance-info-item">
                <span class="k">接诊医生</span>
                <span class="v">{{ currentQueueRow?.doctorName || '—' }}</span>
              </div>
              <div class="guidance-info-item">
                <span class="k">挂号时间</span>
                <span class="v">{{ currentQueueRow?.startTime || '—' }}</span>
              </div>
              <div class="guidance-info-item">
                <span class="k">挂号科室</span>
                <span class="v">{{ currentQueueRow?.organizationName || '—' }}</span>
              </div>
              <div class="guidance-info-item">
                <span class="k">收费状态</span>
                <span class="v">{{ statusLabel }}</span>
              </div>
              <div class="guidance-info-item">
                <span class="k">开立项目</span>
                <span class="v money">
                  <template v-if="sheetLoading">加载中…</template>
                  <template v-else>{{ orderTabCounts.all }} 项</template>
                </span>
              </div>
            </div>
          </div>

          <div v-loading="sheetLoading" class="guidance-card">
            <div class="guidance-card-head">
              <span class="guidance-card-title">开立内容</span>
              <div class="guidance-tabs">
                <button
                  v-for="tab in ORDER_TAB_OPTIONS"
                  :key="tab.code"
                  type="button"
                  class="guidance-tab"
                  :class="{ active: activeOrderTab === tab.code }"
                  @click="activeOrderTab = tab.code"
                >
                  {{ tab.label }}
                  <span class="num">{{ orderTabCounts[tab.code] }}</span>
                </button>
              </div>
            </div>

            <div class="guidance-orders-body">
              <div v-if="displayedOrderRows.length === 0" class="guidance-empty-block">
                {{ sheetLoading ? '正在加载开立内容…' : '暂无符合条件的开立记录' }}
              </div>
              <table v-else>
                <thead>
                  <tr>
                    <th class="guidance-th-type">类型</th>
                    <th>项目内容</th>
                    <th style="width: 90px">数量</th>
                    <th>执行科室</th>
                    <th style="width: 100px">状态</th>
                    <th style="width: 100px">费用</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in displayedOrderRows" :key="row.rowKey">
                    <td>
                      <span
                        class="guidance-type-tag"
                        :class="{
                          'is-lab': row.category === 'lab',
                          'is-treatment': row.category === 'treatment',
                          'is-western': row.category === 'western',
                          'is-device': row.category === 'device',
                        }"
                      >
                        {{ row.categoryLabel }}
                      </span>
                    </td>
                    <td>
                      <div class="guidance-td-name">{{ row.itemName }}</div>
                      <div v-if="row.detailText" class="guidance-td-sub">{{ row.detailText }}</div>
                    </td>
                    <td>{{ row.quantityText }}</td>
                    <td>
                      <span v-if="row.usageText">{{ row.usageText }}</span>
                      <span v-else style="color: #93a1b0">—</span>
                    </td>
                    <td>
                      <span class="guidance-pill" :class="row.statusClass">{{
                        row.statusText
                      }}</span>
                    </td>
                    <td class="guidance-td-fee">{{ row.feeText }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>
