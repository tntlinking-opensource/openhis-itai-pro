<script setup lang="ts">
import type { ReturnMedicineDetailRow, ReturnMedicineQueuePatient } from './components/types';

import type {
  ReturnMedicineDetailRowDto,
  ReturnPatientRowDto,
} from '#/api/outpatient-pharmacy-return-medicine/outpatient-pharmacy-return-medicine.types';
import type { ChargeType } from '#/chs/constants/insurance-contract';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  httpConfirmMedicineReturn,
  httpGetReturnMedicineDetail,
  httpLoadReturnMedicineInit,
  httpPageReturnPatientQueue,
} from '#/api/outpatient-pharmacy-return-medicine/outpatient-pharmacy-return-medicine.http';
import { MED_MEDICATION_REQUEST_TABLE } from '#/api/outpatient-pharmacy-return-medicine/outpatient-pharmacy-return-medicine.types';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { parseListItemsFromApiData } from '#/utils/api/unwrap-list-response';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatLocalDateYmd } from '#/utils/format/date-range-quick';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { MSFX_HIS_SCENE } from '#/msfx/constants';

import ReturnMedicineDetailCenter from './components/return-medicine-detail-center.vue';
import ReturnMedicineQueuePanel from './components/return-medicine-queue-panel.vue';
import {
  filterQueuePatientsByRefundTab,
  mapReturnDetailRowToUi,
  mapReturnPatientRowToQueuePatient,
} from './services/map-api-to-return-ui';

defineOptions({
  name: 'PharmacyReturnMedicineOutpatient',
});

const keyword = ref('');
const defaultQueueListDate = formatLocalDateYmd();
const selectedQueueListDate = ref(defaultQueueListDate);
/** 与门诊发药一致：pending=待退药 / completed=已退药 */
const queueScope = ref<'completed' | 'pending'>('pending');
const selectedPatientId = ref<null | string>(null);

const detailCenterRef = ref<InstanceType<typeof ReturnMedicineDetailCenter> | null>(null);

const queuePatientsAll = ref<ReturnMedicineQueuePatient[]>([]);

/** 队列 Tab → 后端 refundStatus / 明细过滤 */
const refundQueueStatus = computed(() =>
  queueScope.value === 'pending' ? 'PENDING_REFUND' : 'REFUNDED',
);
const detailRows = ref<ReturnMedicineDetailRow[]>([]);

const queueLoading = ref(false);
const detailLoading = ref(false);
const initLoading = ref(false);
const submitLoading = ref(false);
const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

/** 后端队列含待退/已退混合，按 Tab 过滤展示 */
const filteredQueuePatients = computed(() =>
  filterQueuePatientsByRefundTab(queuePatientsAll.value, refundQueueStatus.value),
);

function handleQueueListDateChange(date: string | undefined) {
  selectedQueueListDate.value = date || defaultQueueListDate;
}

const selectedPatient = computed(() => {
  const id = selectedPatientId.value;
  if (!id) return null;
  return filteredQueuePatients.value.find((p) => p.id === id) ?? null;
});

/** 进入页时拉初始化元数据（与发药页 loadPageInit 同位） */
async function loadPageInit() {
  initLoading.value = true;
  try {
    await httpLoadReturnMedicineInit({ pageNo: 1, pageSize: 10 });
  } catch {
    /* 初始化失败不阻断队列 */
  } finally {
    initLoading.value = false;
  }
}

/** 按日期、Tab 与关键字拉取退药患者队列（与门诊发药 reloadQueue 同构） */
async function reloadQueue() {
  queueLoading.value = true;
  try {
    const res = await httpPageReturnPatientQueue({
      startDate: `${selectedQueueListDate.value} 00:00:00`,
      endDate: `${selectedQueueListDate.value} 23:59:59`,
      searchKey: keyword.value.trim() || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    const { content: rows } = parsePagedRowsFromApiData<ReturnPatientRowDto>(res);
    queuePatientsAll.value = rows.map((r) => mapReturnPatientRowToQueuePatient(r));
  } catch {
    queuePatientsAll.value = [];
  } finally {
    queueLoading.value = false;
  }
}

const debouncedReloadQueue = useDebounceFn(() => {
  void reloadQueue();
}, 400);

/** 查询当前就诊药品退药明细（refundStatus 与 Tab 一致） */
async function reloadDetail() {
  const enc = selectedPatientId.value;
  if (!enc) {
    detailRows.value = [];
    return;
  }
  detailLoading.value = true;
  try {
    const res = await httpGetReturnMedicineDetail({
      encounterId: preserveApiIdForRequest(enc) ?? enc,
      refundStatus: refundQueueStatus.value,
    });
    const items = parseListItemsFromApiData<ReturnMedicineDetailRowDto>(res);
    detailRows.value = items.map((row) => mapReturnDetailRowToUi(row));
  } catch {
    detailRows.value = [];
    ElMessage.warning('退药明细加载失败，请刷新后重试');
  } finally {
    detailLoading.value = false;
  }
}

watch(filteredQueuePatients, (list) => {
  const sid = selectedPatientId.value;
  if (!sid) return;
  if (!list.some((p) => p.id === sid)) {
    selectedPatientId.value = null;
  }
});

watch([selectedPatientId, queueScope], () => {
  void reloadDetail();
});

watch([selectedQueueListDate, queueScope], () => {
  void reloadQueue();
});

watch(keyword, () => {
  debouncedReloadQueue();
});

watch(queueScope, () => {
  selectedPatientId.value = null;
  detailRows.value = [];
});

function handleQueueCardSelect(patient: { id: string }) {
  selectedPatientId.value = patient.id;
  void reloadDetail();
}

/** 勾选行 → 提交 items（tableName 固定药品表） */
function buildSubmitItems() {
  const selected = detailCenterRef.value?.getSelectedDetailRows() ?? [];
  return selected.map((row) => ({
    dispenseId: preserveApiIdForRequest(row.dispenseId) ?? row.dispenseId,
    requestId: preserveApiIdForRequest(row.requestId) ?? row.requestId,
    tableName: MED_MEDICATION_REQUEST_TABLE,
  }));
}

/** 确认退药（reason 选填） */
async function handleConfirmReturn() {
  const items = buildSubmitItems();
  if (items.length === 0) {
    ElMessage.warning('请先勾选待退药明细');
    return;
  }
  try {
    await ElMessageBox.confirm('确认对所选药品执行退药并回补库存？', '确认退药', {
      type: 'warning',
    });
  } catch {
    return;
  }
  submitLoading.value = true;
  try {
    const res = await httpConfirmMedicineReturn({
      items,
      confirmType: 'CONFIRM',
      contractBusNo: insuranceContractBusNo.value,
    });
    if (res.success === false && (res.failedItems?.length ?? 0) > 0) {
      ElMessage.error(res.message?.trim() || '退药失败，请刷新后重试');
    } else {
      const message = res.message?.trim() || '退药成功';
      if (message.includes('EX-OPP-207')) {
        ElMessage.warning(message);
      } else {
        ElMessage.success(message);
      }
    }
    await reloadDetail();
    await reloadQueue();
    await nextTick(() => {
      detailCenterRef.value?.clearDetailSelection();
    });
  } catch {
    /* 拦截器提示 */
  } finally {
    submitLoading.value = false;
  }
}

onMounted(async () => {
  await loadPageInit();
  try {
    const chargeTypes = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(chargeTypes);
    insuranceChargeTypeOptions.value = eligible;
    insuranceContractBusNo.value = resolveDefaultInsuranceBusNo(eligible);
  } catch {
    insuranceContractBusNo.value = undefined;
    insuranceChargeTypeOptions.value = [];
  }
  await reloadQueue();
});
</script>

<template>
  <div
    v-loading="initLoading"
    class="pharmacy-return-medicine-outpatient-root flex h-[calc(100vh-120px)] min-h-[480px] w-full min-w-0 flex-col overflow-hidden"
  >
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="pharmacy-return-medicine-outpatient min-h-0 min-w-0 flex-1"
      left-track="minmax(228px, 260px)"
      left-track-lg="minmax(228px, 260px)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <div class="flex h-full min-h-0 flex-col">
          <div
            v-if="insuranceChargeTypeOptions.length > 0"
            class="border-b border-solid border-[var(--el-border-color-lighter)] px-2 pb-2"
          >
            <div class="flex min-w-0 items-center gap-1.5">
              <span class="shrink-0 text-xs text-[var(--el-text-color-secondary)]">医保契约</span>
              <ElSelect
                v-model="insuranceContractBusNo"
                class="!min-w-0 flex-1"
                placeholder="请选择医保契约"
                filterable
                clearable
              >
                <ElOption
                  v-for="item in insuranceChargeTypeOptions"
                  :key="item.busNo"
                  :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                  :value="item.busNo"
                />
              </ElSelect>
            </div>
          </div>
          <div class="min-h-0 flex-1 overflow-hidden">
            <ReturnMedicineQueuePanel
              v-model:keyword="keyword"
              v-model:queue-date="selectedQueueListDate"
              v-model:queue-scope="queueScope"
              :filtered-patients="filteredQueuePatients"
              :loading-queue="queueLoading"
              :selected-patient-id="selectedPatientId"
              @queue-date-change="handleQueueListDateChange"
              @refresh="reloadQueue"
              @select-patient="handleQueueCardSelect"
            />
          </div>
        </div>
      </template>

      <template #center>
        <ReturnMedicineDetailCenter
          ref="detailCenterRef"
          :action-disabled="submitLoading"
          :detail-loading="detailLoading"
          :detail-rows="detailRows"
          :refund-queue-status="refundQueueStatus"
          :selected-patient="selectedPatient"
          :msfx-his-scene="MSFX_HIS_SCENE.OP_RETURN"
          @confirm-return="handleConfirmReturn"
        />
      </template>
    </StationTriPaneLayout>
  </div>
</template>
